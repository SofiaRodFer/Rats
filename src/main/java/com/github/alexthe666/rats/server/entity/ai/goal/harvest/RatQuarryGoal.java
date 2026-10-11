package com.github.alexthe666.rats.server.entity.ai.goal.harvest;

import com.github.alexthe666.rats.RatConfig;
import com.github.alexthe666.rats.data.tags.RatsBlockTags;
import com.github.alexthe666.rats.registry.RatsBlockRegistry;
import com.github.alexthe666.rats.server.block.entity.RatQuarryBlockEntity;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.misc.QuarryGeometry;
import com.github.alexthe666.rats.server.misc.QuarrySafety;
import com.github.alexthe666.rats.server.misc.QuarryTravel;
import com.github.alexthe666.rats.server.misc.RatUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class RatQuarryGoal extends BaseRatHarvestGoal {
	private RatQuarryBlockEntity quarry;
	private int breakingTime;
	private int previousBreakProgress = -1;
	private BlockState miningState;
	private int navigationTick;
	private String lastDecision = "not evaluated";
	public String debugState() { return this.lastDecision + "; delay=" + this.nextStartTick; }

	public RatQuarryGoal(TamedRat rat) { super(rat); }
	@Override
	public boolean canUse() {
		if (!super.canUse()) return false;
		if (!this.checkTheBasics(false, false) || this.rat.getDepositPos().isEmpty()) { this.lastDecision = "basics"; return false; }
		var deposit = this.rat.getDepositPos().get();
		if (!deposit.dimension().equals(this.rat.level().dimension()) || !this.rat.level().hasChunkAt(deposit.pos()) || !(this.rat.level().getBlockEntity(deposit.pos()) instanceof RatQuarryBlockEntity block)) { this.lastDecision = "missing quarry"; return false; }
		this.quarry = block;
		this.lastDecision = "evaluating";
		if (this.rat.quarryReturning && !this.rat.cargo.isEmpty()) { this.lastDecision = "returning"; return false; }
		if (this.quarry.isPaused() || this.rat.cargo.isFull()) { this.lastDecision = "paused/full"; this.requestDeposit(); return false; }
		if (!QuarrySafety.prepareSlice(this.quarry, this.rat)) { this.lastDecision = "unsafe"; this.quarry.markBlocked(); this.requestDeposit(); return false; }
		this.setTargetBlock(this.quarry.reserveTarget(this.rat, this::canMine));
		if (this.getTargetBlock() == null) {
			this.lastDecision = "no target";
			if (this.quarry.getStatus() != 0 && this.quarry.getStatus() != 5) this.requestDeposit();
			return false;
		}
		List<ItemStack> drops = this.drops(this.getTargetBlock());
		this.lastDecision = "target " + this.getTargetBlock();
		if (!this.rat.cargo.isEmpty() && !this.rat.cargo.canFit(drops)) {
			this.quarry.releaseTarget(this.rat.getUUID());
			this.setTargetBlock(null);
			this.requestDeposit();
			return false;
		}
		return true;
	}
	private void requestDeposit() { if (!this.rat.cargo.isEmpty()) this.rat.quarryReturning = true; }
	@Override
	public boolean isReadyToFire() {
		if (this.nextStartTick > 0) { this.nextStartTick--; return false; }
		this.nextStartTick = this.adjustedTickDelay(Math.max(1, RatConfig.ratHarvestDelay / 2));
		return true;
	}
	private boolean canMine(BlockPos pos) {
		return this.canTargetBlock(pos) && !this.rat.level().getBlockState(pos).is(RatsBlockTags.QUARRY_IGNORABLES)
				&& !RatUtils.isBlockProtected(this.rat.level(), pos, this.rat) && RatUtils.canRatBreakBlock(this.rat.level(), pos, this.rat);
	}
	private List<ItemStack> drops(BlockPos pos) {
		return Block.getDrops(this.rat.level().getBlockState(pos), (ServerLevel) this.rat.level(), pos, this.rat.level().getBlockEntity(pos), this.rat, ItemStack.EMPTY);
	}
	@Override
	public boolean canContinueToUse() {
		return this.checkTheBasics(false, false) && this.quarry != null && !this.quarry.isRemoved() && !this.quarry.isPaused()
				&& this.rat.getDepositPos().filter(pos -> pos.dimension().equals(this.rat.level().dimension()) && pos.pos().equals(this.quarry.getBlockPos())).isPresent()
				&& this.getTargetBlock() != null && this.quarry.ownsTarget(this.getTargetBlock(), this.rat.getUUID())
				&& this.canMine(this.getTargetBlock()) && !this.rat.quarryReturning;
	}
	@Override
	public void stop() {
		if (this.quarry != null) this.quarry.releaseTarget(this.rat.getUUID());
		super.stop();
		this.breakingTime = 0;
		this.previousBreakProgress = -1;
		this.miningState = null;
		this.navigationTick = 0;
	}
	@Override
	public void tick() {
		BlockPos target = this.getTargetBlock();
		if (target == null || this.quarry == null) return;
		this.quarry.renewTarget(target, this.rat.getUUID());
		if (++this.navigationTick % 10 == 1) {
			if (!QuarrySafety.prepareSlice(this.quarry, this.rat)) {
				this.quarry.markBlocked();
				this.deferCurrentTarget();
				this.requestDeposit();
				this.stop();
				return;
			}
		}
		Vec3 approach = this.approach(target);
		boolean withinShaft = this.quarry.getDigDirection() == Direction.DOWN
				&& Math.abs(this.rat.getY() - target.getY()) <= 3
				&& Math.abs(this.rat.getX() - this.quarry.getBlockPos().getX()) <= this.quarry.getWidth() / 2.0 + 0.5
				&& Math.abs(this.rat.getZ() - this.quarry.getBlockPos().getZ()) <= this.quarry.getWidth() / 2.0 + 0.5;
		double dx = this.rat.getX() - target.getX() - 0.5, dz = this.rat.getZ() - target.getZ() - 0.5;
		// Quarry upgrade works across its configured height; do not demand a
		// ground navigator climb onto the ceiling of every 6-high cross section.
		boolean visibleReach = dx * dx + dz * dz <= 9.0D
				&& Math.abs(this.rat.getEyeY() - target.getY() - 0.5) <= this.quarry.getWidth() + 1
				&& this.canSeeTarget(target);
		if (!withinShaft && !visibleReach && this.rat.distanceToSqr(approach) > 4.0D) {
			if (this.navigationTick % 10 == 1) QuarryTravel.moveTo(this.rat, this.quarry, approach);
			this.rat.crafting = false;
			return;
		}
		this.rat.getNavigation().stop();
		this.rat.getLookControl().setLookAt(target.getCenter());
		BlockState state = this.rat.level().getBlockState(target);
		if (state != this.miningState) { this.breakingTime = 0; this.miningState = state; }
		this.rat.crafting = true;
		this.rat.level().broadcastEntityEvent(this.rat, (byte) 85);
		int hardness = Math.max(1, (int) (state.getDestroySpeed(this.rat.level(), target) * 10));
		int progress = Math.min(9, ++this.breakingTime * 10 / hardness);
		SoundType sound = state.getSoundType(this.rat.level(), target, this.rat);
		if (this.breakingTime % 5 == 0) this.rat.playSound(sound.getHitSound(), sound.getVolume(), sound.getPitch());
		if (progress != this.previousBreakProgress) {
			this.rat.level().destroyBlockProgress(this.rat.getId(), target, progress);
			this.previousBreakProgress = progress;
		}
		if (this.breakingTime < hardness) return;
		if (!this.canMine(target) || !QuarrySafety.prepareSlice(this.quarry, this.rat)) { this.stop(); return; }
		List<ItemStack> drops = this.drops(target);
		if (!this.rat.cargo.isEmpty() && !this.rat.cargo.canFit(drops)) { this.requestDeposit(); this.stop(); return; }
		if (this.rat.level().destroyBlock(target, false)) {
			for (ItemStack stack : drops) {
				ItemStack left = this.rat.cargo.insert(stack, false);
				if (!left.isEmpty()) {
					ItemEntity item = new ItemEntity(this.rat.level(), target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D, left);
					item.setExtendedLifetime();
					item.setNoPickUpDelay();
					this.rat.level().addFreshEntity(item);
				}
			}
			this.rat.playSound(sound.getBreakSound(), sound.getVolume(), sound.getPitch());
		}
		if (this.rat.cargo.isFull()) this.requestDeposit();
		this.stop();
	}
	private Vec3 approach(BlockPos target) {
		if (this.quarry.getDigDirection() == Direction.DOWN) {
			int depth = Math.max(0, this.quarry.getProgress() - 1);
			if (this.quarry.getProgress() == 0) return Vec3.atBottomCenterOf(this.quarry.getBlockPos().above());
			return Vec3.atBottomCenterOf(QuarryGeometry.stair(this.quarry.getBlockPos(), this.quarry.getWidth(), depth).above());
		}
		BlockPos behind = target.relative(this.quarry.getDigDirection().getOpposite());
		// Do not force every row to the quarry's Y: a buried entrance is not
		// walkable until the upper rows have been excavated. Stand on a visible
		// target when possible, then descend naturally as the floor is mined.
		Vec3 top = Vec3.atBottomCenterOf(target.above());
		if (this.rat.level().getFluidState(target.above()).isEmpty()
				&& this.rat.level().noCollision(this.rat, this.rat.getBoundingBox().move(top.subtract(this.rat.position())))) return top;
		return Vec3.atBottomCenterOf(behind);
	}
	private boolean canSeeTarget(BlockPos target) {
		var hit = this.rat.level().clip(new net.minecraft.world.level.ClipContext(this.rat.getEyePosition(), target.getCenter(),
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this.rat));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getBlockPos().equals(target);
	}
}
