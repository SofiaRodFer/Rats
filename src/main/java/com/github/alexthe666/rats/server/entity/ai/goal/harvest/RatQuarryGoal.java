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
	private int approachTicks;
	private boolean oreTarget;
	private com.github.alexthe666.rats.server.misc.QuarryAudit.Task maintenance;
	private boolean maintenanceReturning;
	private int maintenanceIdle;
	private double maintenanceDistance = Double.MAX_VALUE;
	private String lastDecision = "not evaluated";
	private String lastTickDecision = "not ticked";
	public String debugState() { return this.lastDecision + "; delay=" + this.nextStartTick + "; lastTick=" + this.lastTickDecision; }

	public RatQuarryGoal(TamedRat rat) { super(rat); }
	@Override
	public boolean canUse() {
		if (!super.canUse()) return false;
		if (!this.checkTheBasics(false, false) || this.rat.getDepositPos().isEmpty()) { this.lastDecision = "basics"; return false; }
		var deposit = this.rat.getDepositPos().get();
		if (!deposit.dimension().equals(this.rat.level().dimension()) || !this.rat.level().hasChunkAt(deposit.pos()) || !(this.rat.level().getBlockEntity(deposit.pos()) instanceof RatQuarryBlockEntity block)) { this.lastDecision = "missing quarry"; return false; }
		this.quarry = block;
		if (!this.quarry.assignWorker(this.rat)) { this.lastDecision = "worker limit"; return false; }
		this.lastDecision = "evaluating";
		if (this.rat.quarryReturning && !this.rat.cargo.isEmpty()) { this.lastDecision = "returning"; return false; }
		if (this.quarry.isPaused() || this.rat.cargo.isFull()) { this.lastDecision = "paused/full"; this.requestDeposit(); return false; }
		this.maintenance = this.quarry.claimMaintenance(this.rat);
		if (this.maintenance != null) {
			this.setTargetBlock(this.maintenance.target()); this.maintenanceReturning = false; this.maintenanceIdle = 0; this.maintenanceDistance = Double.MAX_VALUE;
			if (this.getTargetBlock() == null) { this.quarry.finishMaintenance(this.rat, false); this.maintenance = null; return false; }
			this.lastDecision = "maintenance " + this.maintenance; return true;
		}
		if (!QuarrySafety.prepareSlice(this.quarry, this.rat)) { this.lastDecision = "unsafe"; this.quarry.markBlocked(); this.requestDeposit(); return false; }
		this.oreTarget = false;
		this.setTargetBlock(null);
		if (com.github.alexthe666.rats.server.misc.RatUpgradeUtils.hasUpgrade(this.rat, com.github.alexthe666.rats.registry.RatsItemRegistry.RAT_UPGRADE_ORE_HUNTER.get())) {
			this.quarry.claimVein(this.rat);
			if (!this.rat.oreVein.targets(this.quarry.getBlockPos()).isEmpty()) this.rat.oreVein.rememberReturnPoint(this.rat.position());
			for (BlockPos ore : this.rat.oreVein.targets(this.quarry.getBlockPos()).stream().sorted(java.util.Comparator.comparingDouble(pos -> this.rat.distanceToSqr(pos.getCenter()))).toList()) {
				if (!this.rat.level().hasChunkAt(ore)) continue;
				if (!this.rat.level().getBlockState(ore).is(net.minecraftforge.common.Tags.Blocks.ORES) || !this.canMine(ore)) { this.rat.oreVein.remove(ore); continue; }
				if (this.quarry.reserveOre(ore, this.rat)) { this.oreTarget = true; this.setTargetBlock(ore); break; }
			}
		}
		if (this.getTargetBlock() == null) this.setTargetBlock(this.quarry.reserveTarget(this.rat, this::canMine));
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
		return this.rat.level().hasChunkAt(pos) && this.rat.level().getBlockEntity(pos) == null && this.canTargetBlock(pos) && !this.rat.level().getBlockState(pos).is(RatsBlockTags.QUARRY_IGNORABLES)
				&& !RatUtils.isBlockProtected(this.rat.level(), pos, this.rat) && RatUtils.canRatBreakBlock(this.rat.level(), pos, this.rat);
	}
	private List<ItemStack> drops(BlockPos pos) {
		return Block.getDrops(this.rat.level().getBlockState(pos), (ServerLevel) this.rat.level(), pos, this.rat.level().getBlockEntity(pos), this.rat, ItemStack.EMPTY);
	}
	@Override
	public boolean canContinueToUse() {
		if (this.maintenance != null) return this.checkTheBasics(false, false) && this.quarry != null && !this.quarry.isRemoved() && !this.quarry.isPaused()
				&& this.rat.getDepositPos().filter(pos -> pos.dimension().equals(this.rat.level().dimension()) && pos.pos().equals(this.quarry.getBlockPos())).isPresent()
				&& (this.maintenanceReturning || this.quarry.ownsMaintenance(this.rat));
		return this.checkTheBasics(false, false) && this.quarry != null && !this.quarry.isRemoved() && !this.quarry.isPaused()
				&& this.rat.getDepositPos().filter(pos -> pos.dimension().equals(this.rat.level().dimension()) && pos.pos().equals(this.quarry.getBlockPos())).isPresent()
				&& this.getTargetBlock() != null && this.quarry.ownsTarget(this.getTargetBlock(), this.rat.getUUID())
				&& (!this.oreTarget || this.rat.level().getBlockState(this.getTargetBlock()).is(net.minecraftforge.common.Tags.Blocks.ORES))
				&& this.canMine(this.getTargetBlock()) && !this.rat.quarryReturning;
	}
	@Override
	public void stop() {
		if (this.maintenance != null && this.quarry != null && !this.maintenanceReturning) this.quarry.finishMaintenance(this.rat, false);
		this.maintenance = null; this.maintenanceReturning = false;
		if (this.quarry != null) this.quarry.releaseTarget(this.rat.getUUID());
		super.stop();
		this.breakingTime = 0;
		this.previousBreakProgress = -1;
		this.miningState = null;
		this.navigationTick = 0;
		this.approachTicks = 0;
	}
	@Override
	public void tick() {
		if (this.maintenance != null) { this.tickMaintenance(); return; }
		BlockPos target = this.getTargetBlock();
		if (target == null || this.quarry == null) return;
		this.quarry.renewTarget(target, this.rat.getUUID());
		if (++this.navigationTick % 10 == 1) {
			if (this.oreTarget) this.quarry.claimVein(this.rat);
			if (!QuarrySafety.prepareSlice(this.quarry, this.rat, this.oreTarget ? this.quarry.getProgress() : this.quarry.depthOf(target))) {
				this.quarry.markBlocked();
				this.deferCurrentTarget();
				this.requestDeposit();
				this.stop();
				return;
			}
		}
		Vec3 approach = this.approach(target);
		boolean withinShaft = !this.oreTarget && this.quarry.getDigDirection() == Direction.DOWN
				&& Math.abs(this.rat.getY() - target.getY()) <= 3
				&& Math.abs(this.rat.getX() - this.quarry.getBlockPos().getX()) <= this.quarry.getWidth() / 2.0 + 0.5
				&& Math.abs(this.rat.getZ() - this.quarry.getBlockPos().getZ()) <= this.quarry.getWidth() / 2.0 + 0.5;
		double dx = this.rat.getX() - target.getX() - 0.5, dz = this.rat.getZ() - target.getZ() - 0.5;
		// Quarry upgrade works across its configured height; do not demand a
		// ground navigator climb onto the ceiling of every 6-high cross section.
		Direction direction = this.quarry.getDigDirection();
		double along = dx * direction.getStepX() + dz * direction.getStepZ();
		double across = dx * direction.getStepZ() - dz * direction.getStepX();
		boolean frontReach = !this.oreTarget && direction != Direction.DOWN && Math.abs(along) <= 3.0D
				&& Math.abs(across) <= this.quarry.getWidth() + 1
				&& Math.abs(this.rat.getEyeY() - target.getY() - 0.5) <= this.quarry.getWidth() + 1;
		boolean oreReach = this.oreTarget && this.rat.distanceToSqr(target.getCenter()) <= 16;
		if (!withinShaft && !frontReach && !oreReach && (this.oreTarget || this.rat.distanceToSqr(approach) > 4.0D)) {
			if (this.oreTarget) {
				var path = this.rat.getNavigation().createPath(BlockPos.containing(approach), 0);
				var back = this.rat.getNavigation().createPath(this.quarry.getBlockPos().above(), 0);
				if (path == null || !path.canReach() || back == null || !back.canReach()) { this.rat.oreVein.remove(target); this.quarry.completeOre(target); this.requestDeposit(); this.stop(); return; }
			}
			this.lastTickDecision = "approach " + target + " via " + approach + "; path=" + this.rat.getNavigation().getPath();
			if (this.navigationTick % 10 == 1) QuarryTravel.moveTo(this.rat, this.quarry, approach);
			this.rat.crafting = false;
			// Count time without excavation, not displacement: walking in
			// circles must not indefinitely renew a mining reservation.
			if (++this.approachTicks > 200) { if (this.oreTarget) this.rat.oreVein.remove(target); this.deferCurrentTarget(); this.stop(); }
			return;
		}
		this.approachTicks = 0;
		this.rat.getNavigation().stop();
		this.lastTickDecision = "mining " + target + "; elapsed=" + this.breakingTime;
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
		if (!this.canMine(target) || !QuarrySafety.prepareSlice(this.quarry, this.rat, this.oreTarget ? this.quarry.getProgress() : this.quarry.depthOf(target)) || (this.oreTarget && !QuarrySafety.prepareOreBlock(this.rat, target))) { this.rat.oreVein.remove(target); this.stop(); return; }
		List<ItemStack> drops = this.drops(target);
		if (!this.rat.cargo.isEmpty() && !this.rat.cargo.canFit(drops)) { this.requestDeposit(); this.stop(); return; }
		if (this.rat.level().destroyBlock(target, false)) {
			this.rat.oreVein.remove(target);
			this.quarry.completeOre(target);
			if (this.oreTarget && this.quarry.getDigDirection() != Direction.DOWN) QuarrySafety.ensureHorizontalFloor(this.quarry, this.rat, this.quarry.depthOf(target));
			if (!this.oreTarget && com.github.alexthe666.rats.server.misc.RatUpgradeUtils.hasUpgrade(this.rat, com.github.alexthe666.rats.registry.RatsItemRegistry.RAT_UPGRADE_ORE_HUNTER.get())) this.rat.oreVein.discover(this.rat.level(), this.quarry.getBlockPos(), target);
			this.quarry.claimVein(this.rat);
			this.rat.recordWorkProgress();
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
	private void tickMaintenance() {
		var task = this.maintenance;
		Vec3 destination = this.maintenanceReturning ? Vec3.atBottomCenterOf(this.quarry.getBlockPos().above())
				: task.direction() == Direction.DOWN ? Vec3.atBottomCenterOf(QuarryGeometry.stair(this.quarry.getBlockPos(), this.quarry.getWidth(), Math.max(0, task.depth() - 1)).above())
				: Vec3.atBottomCenterOf(QuarryGeometry.cell(this.quarry.getBlockPos(), task.direction(), this.quarry.getWidth(), task.depth(), this.quarry.getWidth() / 2, 0));
		double distance = this.rat.distanceToSqr(destination);
		if (distance < this.maintenanceDistance - 0.05) { this.maintenanceDistance = distance; this.maintenanceIdle = 0; } else this.maintenanceIdle++;
		if (this.maintenanceIdle > 200) { this.stop(); return; }
		if (this.maintenanceReturning) {
			if (distance <= 4) { this.stop(); return; }
			QuarryTravel.moveTo(this.rat, this.quarry, task.direction(), destination); return;
		}
		this.quarry.renewMaintenance(this.rat);
		this.lastTickDecision = "maintenance " + task;
		if (this.rat.distanceToSqr(task.target().getCenter()) <= 16) {
			boolean repaired = QuarrySafety.repairSlice(this.quarry, this.rat, task.direction(), task.depth());
			boolean complete = repaired && com.github.alexthe666.rats.server.misc.QuarryAudit.inspect(this.quarry, task.direction(), task.depth()) == null;
			this.quarry.finishMaintenance(this.rat, complete);
			if (repaired) this.rat.recordWorkProgress();
			this.maintenanceReturning = true; this.maintenanceIdle = 0; this.maintenanceDistance = Double.MAX_VALUE;
			return;
		}
		if (++this.navigationTick % 10 == 1) QuarryTravel.moveTo(this.rat, this.quarry, task.direction(), destination);
	}
	private Vec3 approach(BlockPos target) {
		if (this.oreTarget) {
			return net.minecraft.core.Direction.stream().map(target::relative).filter(pos -> this.rat.level().hasChunkAt(pos) && this.rat.level().getBlockState(pos).isAir() && this.rat.level().getFluidState(pos).isEmpty() && !this.rat.level().getBlockState(pos.below()).getCollisionShape(this.rat.level(), pos.below()).isEmpty()).min(java.util.Comparator.comparingDouble(pos -> this.rat.distanceToSqr(pos.getCenter()))).map(Vec3::atBottomCenterOf).orElse(this.rat.position());
		}
		if (this.quarry.getDigDirection() == Direction.DOWN) {
			int depth = Math.max(0, this.quarry.depthOf(target) - 1);
			if (this.quarry.depthOf(target) == 0) return Vec3.atBottomCenterOf(this.quarry.getBlockPos().above());
			return Vec3.atBottomCenterOf(QuarryGeometry.stair(this.quarry.getBlockPos(), this.quarry.getWidth(), depth).above());
		}
		BlockPos behind = target.relative(this.quarry.getDigDirection().getOpposite());
		// Follow the current traversable level, not the base inside unmined stone
		// or the ceiling above the ground navigator's jump limit.
		int y = net.minecraft.util.Mth.clamp(this.rat.blockPosition().getY(), this.quarry.getBlockPos().getY(), this.quarry.getBlockPos().getY() + this.quarry.getWidth() - 1);
		return Vec3.atBottomCenterOf(new BlockPos(behind.getX(), y, behind.getZ()));
	}
}
