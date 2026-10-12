package com.github.alexthe666.rats.server.entity.ai.goal;

import com.github.alexthe666.rats.registry.RatsItemRegistry;
import com.github.alexthe666.rats.server.block.entity.DecoratedRatCageBlockEntity;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.items.RatHammockItem;
import com.github.alexthe666.rats.server.items.RatIglooItem;
import com.github.alexthe666.rats.server.misc.CageHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Cage-only movement, including vertical connections; never seeks outside cells. */
public class RatCageHabitatGoal extends Goal {
	private final TamedRat rat;
	private final ArrayDeque<BlockPos> route = new ArrayDeque<>();
	private BlockPos destination;
	private int waitUntil, routeExpires;

	public RatCageHabitatGoal(TamedRat rat) {
		this.rat = rat;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}
	@Override
	public boolean canUse() { return this.rat.canMove() && !this.rat.isInWheel() && (this.rat.isInCage() || this.rat.isInTube()); }
	@Override
	public boolean canContinueToUse() { return this.canUse(); }
	@Override
	public void start() { this.rat.getNavigation().stop(); this.rat.setInSittingPose(false); }
	@Override
	public void stop() { this.route.clear(); this.destination = null; this.rat.setCageResting(false); this.rat.setCageClimbing(false); this.rat.setDeltaMovement(Vec3.ZERO); }
	@Override
	public void tick() {
		this.rat.fallDistance = 0;
		this.rat.setDeltaMovement(Vec3.ZERO);
		this.rat.setSpeed(0); this.rat.setZza(0); this.rat.setXxa(0);
		this.rat.setCageClimbing(false);
		if (CageHabitat.settleUnsupported(this.rat)) { this.route.clear(); return; }
		if (this.rat.tickCount < this.waitUntil && CageHabitat.hasRestSupport(this.rat.level(), this.rat.blockPosition())) return;
		this.rat.setCageResting(false);
		if (this.route.isEmpty() || this.rat.tickCount >= this.routeExpires) this.chooseRoute();
		if (this.route.isEmpty()) return;
		BlockPos next = this.route.peekFirst();
		if (!CageHabitat.isHabitat(this.rat.level(), next)) { this.route.clear(); this.waitUntil = this.rat.tickCount + 40; return; }
		BlockPos current = this.rat.blockPosition();
		if (!current.equals(next) && !CageHabitat.canTraverse(this.rat.level(), current, next)) { this.route.clear(); return; }
		var state = this.rat.level().getBlockState(next);
		boolean ladder = state.getBlock() instanceof com.github.alexthe666.rats.server.block.RatCageBlock && state.getValue(com.github.alexthe666.rats.server.block.RatCageBlock.LADDER);
		boolean tubePort = state.getBlock() instanceof com.github.alexthe666.rats.server.block.RatCageBlock && state.getValue(com.github.alexthe666.rats.server.block.RatCageBlock.SHELF) && state.getValue(com.github.alexthe666.rats.server.block.RatCageBlock.DOWN) == 2;
		var currentState = this.rat.level().getBlockState(current);
		boolean leavingPort = currentState.getBlock() instanceof com.github.alexthe666.rats.server.block.RatCageBlock && currentState.getValue(com.github.alexthe666.rats.server.block.RatCageBlock.SHELF) && currentState.getValue(com.github.alexthe666.rats.server.block.RatCageBlock.DOWN) == 2;
		Vec3 target = this.route.size() == 1 ? CageHabitat.restPoint(this.rat.level(), next) : new Vec3(next.getX() + 0.5, next.getY() + 0.125, next.getZ() + (ladder ? 0.32 : 0.5));
		if ((ladder || tubePort || leavingPort) && Math.abs(this.rat.getY() - next.getY() - 0.125) > 0.03) {
			// Align below the access opening BEFORE crossing the new floor with head/body.
			double alignment = tubePort || leavingPort ? 0.5 : 0.32;
			if (Math.abs(this.rat.getZ() - current.getZ() - alignment) > 0.02 || Math.abs(this.rat.getX() - current.getX() - 0.5) > 0.02) target = new Vec3(current.getX() + 0.5, this.rat.getY(), current.getZ() + alignment);
			else target = new Vec3(next.getX() + 0.5, next.getY() + 0.125, next.getZ() + alignment);
		}
		Vec3 distance = target.subtract(this.rat.position());
		if (distance.lengthSqr() < 0.0004) {
			this.rat.setPos(target.x, target.y, target.z);
			if (this.route.size() == 1 && target.distanceToSqr(CageHabitat.restPoint(this.rat.level(), next)) > 0.0004) return;
			if (!this.rat.blockPosition().equals(next)) return;
			this.route.removeFirst();
			if (this.route.isEmpty()) this.visitDecoration();
			return;
		}
		Vec3 step = distance.normalize().scale(Math.min(0.09, distance.length()));
		this.rat.setCageClimbing(Math.abs(step.y) > 0.01 && (CageHabitat.isAccess(this.rat.level(), current) || CageHabitat.isAccess(this.rat.level(), next)));
		if (this.rat.isCageClimbing()) this.rat.setInSittingPose(false);
		this.rat.setYRot((float) (Math.atan2(distance.z, distance.x) * 180 / Math.PI - 90));
		Vec3 before = this.rat.position();
		this.rat.move(MoverType.SELF, step);
		this.rat.fallDistance = 0;
		this.rat.setDeltaMovement(Vec3.ZERO);
		if (this.rat.position().distanceToSqr(before) < 0.000001) { this.route.clear(); this.waitUntil = this.rat.tickCount + 40; }
	}
	private void chooseRoute() {
		this.route.clear();
		CageHabitat habitat = CageHabitat.discover(this.rat.level(), this.rat.blockPosition());
		com.github.alexthe666.rats.server.misc.CageLayout.synchronize(this.rat.level(), habitat);
		List<BlockPos> choices = new ArrayList<>(habitat.nodes().stream().filter(pos -> CageHabitat.hasRestSupport(this.rat.level(), pos)).toList());
		if (choices.size() > 1) choices.remove(this.rat.blockPosition());
		if (choices.isEmpty()) return;
		List<BlockPos> decorated = choices.stream().filter(pos -> this.rat.level().getBlockEntity(pos) instanceof DecoratedRatCageBlockEntity).toList();
		if (!decorated.isEmpty() && this.rat.getRandom().nextBoolean()) choices = decorated;
		this.destination = choices.get(this.rat.getRandom().nextInt(choices.size()));
		Map<BlockPos, BlockPos> previous = new HashMap<>();
		ArrayDeque<BlockPos> pending = new ArrayDeque<>();
		BlockPos start = this.rat.blockPosition(); pending.add(start); previous.put(start, start);
		while (!pending.isEmpty()) {
			BlockPos current = pending.removeFirst();
			if (current.equals(this.destination)) break;
			for (Direction dir : Direction.values()) {
				BlockPos next = current.relative(dir);
				if (habitat.nodes().contains(next) && !previous.containsKey(next) && CageHabitat.canTraverse(this.rat.level(), current, next)) { previous.put(next, current); pending.add(next); }
			}
		}
		if (!previous.containsKey(this.destination)) { this.destination = null; this.waitUntil = this.rat.tickCount + 40; return; }
		for (BlockPos pos = this.destination; !pos.equals(start); pos = previous.get(pos)) this.route.addFirst(pos);
		if (this.route.isEmpty()) this.route.add(start);
		this.routeExpires = this.rat.tickCount + 400;
	}
	private void visitDecoration() {
		this.waitUntil = this.rat.tickCount + 20;
		if (!(this.rat.level().getBlockEntity(this.destination) instanceof DecoratedRatCageBlockEntity deco)) return;
		ItemStack item = deco.getContainedItem();
		if (item.getItem() instanceof RatIglooItem || item.getItem() instanceof RatHammockItem) {
			this.rat.setCageResting(true); this.waitUntil = this.rat.tickCount + 100;
		} else if (item.is(RatsItemRegistry.RAT_SEED_BOWL.get())) {
			this.rat.setAnimation(TamedRat.ANIMATION_EAT); this.waitUntil = this.rat.tickCount + 40;
		} else if (item.is(RatsItemRegistry.RAT_WATER_BOTTLE.get())) {
			this.rat.setAnimation(TamedRat.ANIMATION_IDLE_SNIFF); this.waitUntil = this.rat.tickCount + 40;
		}
	}
}
