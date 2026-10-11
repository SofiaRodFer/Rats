package com.github.alexthe666.rats.server.entity.ai.goal;

import com.github.alexthe666.rats.RatConfig;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RatTargetItemsGoal extends Goal {
	private final TamedRat rat;
	private ItemEntity targetItem;
	private final Map<UUID, Integer> deferred = new HashMap<>();
	private int attempts;
	private int nextSearch;

	public RatTargetItemsGoal(TamedRat rat) {
		this.rat = rat;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}
	@Override
	public boolean canUse() {
		if (this.rat.tickCount < this.nextSearch || !this.rat.canMove() || this.rat.isInCage() || this.rat.isCurrentlyWorking || !this.rat.isTargetCommand() || this.rat.getDepositPos().isEmpty() || this.rat.cargo.isFull()) return false;
		this.nextSearch = this.rat.tickCount + 20;
		this.deferred.entrySet().removeIf(entry -> entry.getValue() <= this.rat.tickCount);
		var items = this.rat.level().getEntitiesOfClass(ItemEntity.class, this.getTargetableArea(), item ->
				!this.deferred.containsKey(item.getUUID()) && this.rat.shouldCollectItem(item.getItem()) && this.rat.cargo.canAccept(item.getItem()));
		items.sort(Comparator.comparingDouble(this.rat::distanceToSqr));
		for (ItemEntity item : items) {
			Path path = this.rat.getNavigation().createPath(item, 1);
			if (path != null && path.canReach()) { this.targetItem = item; return true; }
			this.deferred.put(item.getUUID(), this.rat.tickCount + 200);
		}
		return false;
	}
	private AABB getTargetableArea() {
		if (this.rat.getRadiusCenter().isPresent()) {
			Vec3 center = Vec3.atCenterOf(this.rat.getSearchCenter());
			double radius = this.rat.getRadius();
			return new AABB(-radius, -radius, -radius, radius, radius, radius).move(center);
		}
		return this.rat.getBoundingBox().inflate(RatConfig.defaultRatRadius);
	}
	@Override
	public boolean canContinueToUse() {
		return this.rat.canMove() && this.rat.isTargetCommand() && !this.rat.isCurrentlyWorking && this.targetItem != null && this.targetItem.isAlive() && this.rat.cargo.canAccept(this.targetItem.getItem()) && this.attempts < 200;
	}
	@Override
	public void start() { this.attempts = 0; }
	@Override
	public void stop() {
		if (this.targetItem != null && this.attempts >= 200) this.deferred.put(this.targetItem.getUUID(), this.rat.tickCount + 600);
		this.targetItem = null;
		this.rat.getNavigation().stop();
	}
	@Override
	public void tick() {
		if (this.targetItem == null || !this.targetItem.isAlive()) return;
		this.attempts++;
		if (this.rat.distanceToSqr(this.targetItem) < this.rat.getRatHarvestDistance(1.0D)) {
			ItemStack remainder = this.rat.cargo.insert(this.targetItem.getItem(), false);
			if (remainder.isEmpty()) this.targetItem.discard();
			else this.targetItem.setItem(remainder);
			this.stop();
		} else if (this.attempts % 10 == 1) this.rat.getNavigation().moveTo(this.targetItem, 1.25D);
	}
}
