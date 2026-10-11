package com.github.alexthe666.rats.server.entity.ai.goal.harvest;

import com.github.alexthe666.rats.RatConfig;
import com.github.alexthe666.rats.registry.RatsItemRegistry;
import com.github.alexthe666.rats.server.entity.ai.goal.RatWorkGoal;
import com.github.alexthe666.rats.server.entity.rat.RatCommand;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.misc.RatUpgradeUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public abstract class BaseRatHarvestGoal extends Goal implements RatWorkGoal {

	private final BlockSorter targetSorter;
	@Nullable
	private BlockPos targetBlock = null;
	@Nullable
	private Entity targetEntity = null;
	protected final TamedRat rat;

	protected int nextStartTick;
	private final Map<BlockPos, Integer> deferredTargets = new HashMap<>();

	protected BaseRatHarvestGoal(TamedRat rat) {
		this.rat = rat;
		this.targetSorter = new BlockSorter(rat);
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Flag.LOOK));
	}

	public boolean checkTheBasics(boolean checkForItems, boolean checkForItemHolding) {
		if (this.rat.tickCount < this.rat.workRetryAt) return false;
		if (!this.rat.canMove() || this.rat.getCommand() != RatCommand.HARVEST || this.rat.isInCage() || this.rat.isInTube()) return false;
		if (checkForItemHolding && !this.checkIfItemCanBeHeld()) return false;
		return !checkForItems || !this.anyHoldableItemsAround();
	}

	@Override
	public boolean canUse() {
		return this.isReadyToFire();
	}

	public boolean isReadyToFire() {
		if (this.nextStartTick > 0) {
			this.nextStartTick--;
			return false;
		}
		this.nextStartTick = this.adjustedTickDelay(RatConfig.ratHarvestDelay);
		return true;
	}

	protected boolean checkIfItemCanBeHeld() {
		return !this.rat.cargo.isFull();
	}

	private boolean anyHoldableItemsAround() {
		List<ItemEntity> items = this.rat.level().getEntitiesOfClass(ItemEntity.class, this.getTargetableArea(), item ->
				(item.onGround() || item.isInWater()) &&
						this.rat.canRatPickupItem(item.getItem()));
		if (items.isEmpty()) return false;

		items.sort(Comparator.comparingDouble(this.rat::distanceToSqr));

		List<ItemEntity> revisedList = new ArrayList<>();
		for (ItemEntity item : items.stream().limit(16).toList()) {
			if (!this.rat.cargo.canAccept(item.getItem()) || !this.rat.shouldCollectItem(item.getItem()) || this.rat.deferredItemTargets.getOrDefault(item.getUUID(), 0) > this.rat.tickCount) continue;
			//please, only go after items you can actually reach
			Path toPath = this.rat.getNavigation().createPath(item, 1);
			if (toPath != null && toPath.canReach()) {
				revisedList.add(item);
			}
		}

		return !revisedList.isEmpty();
	}

	protected AABB getTargetableArea() {
		if (this.rat.isTame() && this.rat.getRadiusCenter().isPresent()) {
			Vec3 center = new Vec3(this.rat.getSearchCenter().getX() + 0.5D, this.rat.getSearchCenter().getY() + 0.5D, this.rat.getSearchCenter().getZ() + 0.5D);
			double radius = this.rat.getRadius();
			AABB aabb = new AABB(-radius, -radius, -radius, radius, radius, radius);
			return aabb.move(center);
		}
		return this.rat.getBoundingBox().inflate(RatConfig.defaultRatRadius);
	}

	@Override
	public void start() {
		this.rat.isCurrentlyWorking = true;
	}

	@Override
	public void stop() {
		if (this.targetBlock != null) this.rat.level().destroyBlockProgress(this.rat.getId(), this.targetBlock, -1);
		this.rat.crafting = false;
		this.rat.level().broadcastEntityEvent(this.rat, (byte) 86);
		this.rat.isCurrentlyWorking = false;
		this.rat.getNavigation().stop();
		this.setTargetBlock(null);
		this.setTargetEntity(null);
	}

	public BlockSorter getTargetSorter() {
		return this.targetSorter;
	}

	@Nullable
	public BlockPos getTargetBlock() {
		return this.targetBlock;
	}

	public void setTargetBlock(@Nullable BlockPos pos) {
		this.targetBlock = pos == null || this.canTargetBlock(pos) ? pos : null;
	}

	public boolean canTargetBlock(BlockPos pos) {
		this.deferredTargets.entrySet().removeIf(entry -> entry.getValue() <= this.rat.tickCount);
		return this.rat.level().hasChunkAt(pos) && !this.deferredTargets.containsKey(pos);
	}

	public void deferCurrentTarget() {
		if (this.targetBlock != null) this.deferredTargets.put(this.targetBlock.immutable(), this.rat.tickCount + 600);
	}

	@Nullable
	public Entity getTargetEntity() {
		return this.targetEntity;
	}

	public void setTargetEntity(@Nullable Entity entity) {
		this.targetEntity = entity;
	}

	public void holdItemHarvestedIfPossible(TamedRat rat, List<ItemStack> items) {
		for (ItemStack stack : items) {
			ItemStack remainder = rat.cargo.insert(stack, false);
			if (!remainder.isEmpty()) {
				ItemEntity item = new ItemEntity(this.rat.level(), this.rat.getX(), this.rat.getY(), this.rat.getZ(), remainder);
				item.setExtendedLifetime();
				item.setNoPickUpDelay();
				this.rat.level().addFreshEntity(item);
			}
		}
	}

	@Override
	public TaskType getRatTaskType() {
		return TaskType.HARVEST;
	}

	public record BlockSorter(Entity entity) implements Comparator<BlockPos> {

		@Override
		public int compare(BlockPos pos1, BlockPos pos2) {
			double distance1 = this.distanceTo(pos1);
			double distance2 = this.distanceTo(pos2);
			return Double.compare(distance1, distance2);
		}

		private double distanceTo(BlockPos pos) {
			double deltaX = this.entity.getX() - (pos.getX() + 0.5);
			double deltaY = this.entity.getY() + this.entity.getEyeHeight() - (pos.getY() + 0.5);
			double deltaZ = this.entity.getZ() - (pos.getZ() + 0.5);
			return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
		}
	}
}
