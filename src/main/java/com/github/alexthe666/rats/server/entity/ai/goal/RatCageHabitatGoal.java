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
	public void stop() { this.route.clear(); this.destination = null; this.rat.setCageResting(false); this.rat.setDeltaMovement(Vec3.ZERO); }
	@Override
	public void tick() {
		this.rat.fallDistance = 0;
		this.rat.setDeltaMovement(Vec3.ZERO);
		this.rat.setSpeed(0); this.rat.setZza(0); this.rat.setXxa(0);
		if (this.rat.tickCount < this.waitUntil && CageHabitat.hasRestSupport(this.rat.level(), this.rat.blockPosition())) return;
		this.rat.setCageResting(false);
		if (this.route.isEmpty() || this.rat.tickCount >= this.routeExpires) this.chooseRoute();
		if (this.route.isEmpty()) return;
		BlockPos next = this.route.peekFirst();
		if (!CageHabitat.isHabitat(this.rat.level(), next)) { this.route.clear(); this.waitUntil = this.rat.tickCount + 40; return; }
		Vec3 target = new Vec3(next.getX() + 0.5, next.getY() + 0.125, next.getZ() + 0.5);
		Vec3 distance = target.subtract(this.rat.position());
		if (distance.lengthSqr() < 0.0004) {
			this.rat.setPos(target.x, target.y, target.z);
			this.route.removeFirst();
			if (this.route.isEmpty()) this.visitDecoration();
			return;
		}
		Vec3 step = distance.normalize().scale(Math.min(0.09, distance.length()));
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
				if (habitat.nodes().contains(next) && !previous.containsKey(next)) { previous.put(next, current); pending.add(next); }
			}
		}
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
