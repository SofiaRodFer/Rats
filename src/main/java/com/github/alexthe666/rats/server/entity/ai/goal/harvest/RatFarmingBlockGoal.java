package com.github.alexthe666.rats.server.entity.ai.goal.harvest;

import com.github.alexthe666.rats.registry.*;
import com.github.alexthe666.rats.server.block.entity.RatFarmingBlockEntity;
import com.github.alexthe666.rats.server.block.entity.RatFarmingBlockEntity.*;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.misc.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** One task lease per worker, supplies fetched physically, never ghost items. */
public class RatFarmingBlockGoal extends BaseRatHarvestGoal {
	private RatFarmingBlockEntity farm;
	private Task task;
	private ItemStack supply = ItemStack.EMPTY;
	private final Map<Integer, Integer> deferred = new HashMap<>();
	private int attempts;
	public String debugState() { return "task=" + this.task + "; supply=" + this.supply + "; attempts=" + this.attempts + "; deferred=" + this.deferred; }
	public RatFarmingBlockGoal(TamedRat rat) { super(rat); }
	public static RatFarmingBlockEntity station(TamedRat rat) {
		return rat.getDepositPos().filter(pos -> pos.dimension().equals(rat.level().dimension()) && rat.level().hasChunkAt(pos.pos())).map(pos -> rat.level().getBlockEntity(pos.pos())).filter(RatFarmingBlockEntity.class::isInstance).map(RatFarmingBlockEntity.class::cast).orElse(null);
	}
	@Override public boolean canUse() {
		if (!this.checkTheBasics(false, false) || this.rat.farmReturning) return false;
		this.farm = station(this.rat); if (this.farm == null || this.farm.editing() || !this.farm.assignWorker(this.rat)) return false;
		this.deferred.entrySet().removeIf(entry -> entry.getValue() <= this.rat.tickCount);
		this.task = this.farm.claim(this.rat, this.deferred.keySet());
		if (this.task == null) { this.returnCargo(); return false; }
		if (this.rat.cargo.isFull() && (this.task.action() == Action.HARVEST || this.task.action() == Action.CLEAR)) { this.farm.release(this.rat); this.returnCargo(); return false; }
		ItemStack needed = this.task.action() == Action.FILL ? new ItemStack(Items.DIRT) : this.task.action() == Action.PLANT ? this.farm.seedAt(this.task.cell()) : this.task.action()==Action.GROW?new ItemStack(Items.BONE_MEAL):ItemStack.EMPTY;
		this.supply = ItemStack.EMPTY;
		if (!needed.isEmpty() && this.cargoSlot(needed) < 0) {
			boolean stock = java.util.stream.IntStream.range(0, 54).anyMatch(slot -> ItemStack.isSameItemSameTags(this.farm.getItem(slot), needed));
			if (!stock) { this.defer(60); this.returnCargo(); return false; }
			if (!this.rat.cargo.canAccept(needed)) { this.farm.release(this.rat); this.returnCargo(); return false; }
			this.supply = needed.copyWithCount(1);
		}
		this.attempts = 0; this.setTargetBlock(this.task.target()); return true;
	}
	private void returnCargo() { if (!this.rat.cargo.isEmpty()) this.rat.farmReturning = true; }
	private int cargoSlot(ItemStack stack) { for (int i = 0; i < this.rat.cargo.slots(); i++) if (ItemStack.isSameItemSameTags(this.rat.cargo.get(i), stack)) return i; return -1; }
	@Override public boolean canContinueToUse() { return this.checkTheBasics(false, false) && !this.rat.farmReturning && this.farm != null && station(this.rat) == this.farm && this.task != null && this.farm.owns(this.task, this.rat); }
	@Override public void stop() { if (this.farm != null) this.farm.release(this.rat); this.task = null; this.supply = ItemStack.EMPTY; super.stop(); }
	private void defer(int ticks) { if (this.task != null) this.deferred.put(this.task.cell(), this.rat.tickCount + ticks); this.stop(); }
	private void moveTo(Vec3 destination) {
		Vec3 point = this.rat.distanceToSqr(destination) > 100 ? this.rat.position().add(destination.subtract(this.rat.position()).normalize().scale(8)) : destination;
		if (!this.rat.level().hasChunkAt(BlockPos.containing(point))) return;
		this.rat.getNavigation().moveTo(this.rat.getNavigation().createPath(BlockPos.containing(point), 1), 1.25);
	}
	@Override public void tick() {
		if (this.task == null) return;
		this.farm.renew(this.task, this.rat);
		if (++this.attempts > 200) { this.defer(200); return; }
		if (!this.supply.isEmpty()) {
			Vec3 destination = Vec3.atBottomCenterOf(this.farm.getBlockPos().above());
			if (this.rat.distanceToSqr(destination) > 6.25) { if (this.attempts % 10 == 1) this.moveTo(destination); return; }
			int remainingSupply=this.supply.is(Items.BONE_MEAL)?8:this.rat.cargo.slots()*this.supply.getMaxStackSize();
			for (int slot = 0; slot < 54&&remainingSupply>0; slot++) {
				var stored = this.farm.getItem(slot); if (!ItemStack.isSameItemSameTags(stored, this.supply)) continue;
				var candidate = stored.copyWithCount(Math.min(stored.getCount(), Math.min(stored.getMaxStackSize(),remainingSupply)));
				int amount = candidate.getCount() - this.rat.cargo.insert(candidate, true).getCount();
				if (amount > 0) {this.rat.cargo.insert(this.farm.removeItem(slot, amount), false);remainingSupply-=amount;}
			}
			if (this.cargoSlot(this.supply) < 0) { this.defer(60); return; }
			this.supply = ItemStack.EMPTY; this.attempts = 0; this.rat.recordWorkProgress(); return;
		}
		BlockPos target = this.task.target();
		if (!this.rat.level().hasChunkAt(target) || this.rat.level().isOutsideBuildHeight(target)) { this.defer(100); return; }
		double dx = this.rat.getX() - target.getX() - 0.5, dz = this.rat.getZ() - target.getZ() - 0.5;
		boolean columnReach = this.task.action() == Action.CLEAR && dx * dx + dz * dz <= 9 && Math.abs(this.rat.getY() - this.farm.getBlockPos().getY()) <= 2;
		if (!columnReach && this.rat.distanceToSqr(target.getCenter()) > 9) {
			if (this.attempts % 10 == 1) {
				BlockPos destination = Direction.stream().map(target::relative).filter(pos -> this.rat.level().hasChunkAt(pos) && this.rat.level().getBlockState(pos).isAir() && this.rat.level().getFluidState(pos).isEmpty() && !this.rat.level().getBlockState(pos.below()).getCollisionShape(this.rat.level(), pos.below()).isEmpty()).min(Comparator.comparingDouble(pos -> this.rat.distanceToSqr(pos.getCenter()))).orElse(target.above());
				this.moveTo(Vec3.atBottomCenterOf(destination));
			} return;
		}
		this.rat.getNavigation().stop(); this.rat.getLookControl().setLookAt(target.getCenter());
		if (!this.farm.owns(this.task, this.rat) || RatUtils.isBlockProtected(this.rat.level(), target, this.rat) || this.rat.level().getBlockEntity(target) != null) { this.defer(200); return; }
		boolean changed = false;
		switch(this.task.action()) {
			case CLEAR, HARVEST -> {
				if (!RatUtils.canRatBreakBlock(this.rat.level(), target, this.rat)) { this.defer(200); return; }
				var drops = Block.getDrops(this.rat.level().getBlockState(target), (ServerLevel)this.rat.level(), target, null, this.rat, ItemStack.EMPTY);
				// A single-stack platter may hold wheat but not its extra seeds.
				// Keep harvesting accepted drops; preserve overflow on the ground.
				if (!this.rat.cargo.isEmpty() && !drops.isEmpty() && drops.stream().noneMatch(this.rat.cargo::canAccept)) { this.returnCargo(); this.stop(); return; }
				changed = this.rat.level().destroyBlock(target, false);
				if (changed) for (var stack : drops) { var left = this.rat.cargo.insert(stack, false); if (!left.isEmpty()) { var entity = new ItemEntity(this.rat.level(), target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, left); entity.setNoPickUpDelay(); entity.setExtendedLifetime(); this.rat.level().addFreshEntity(entity); } }
			}
			case FILL -> {
				int slot = this.cargoSlot(new ItemStack(Items.DIRT));
				if (slot >= 0 && (this.rat.level().getBlockState(target).isAir() || this.rat.level().getBlockState(target).is(Blocks.WATER)) && RatUtils.canRatPlaceBlock(this.rat.level(), target, this.rat)) { changed = this.rat.level().setBlockAndUpdate(target, Blocks.DIRT.defaultBlockState()); if (changed) this.rat.cargo.get(slot).shrink(1); }
			}
			case TILL, LIGHT, WATER -> {
				if(this.task.action()==Action.TILL&&!this.farm.canPrepare(target,this.rat))break;
				if(this.task.action()==Action.WATER&&!this.farm.canPrepareWater(target,this.rat))break;
				if(this.task.action()==Action.LIGHT&&!this.rat.level().isEmptyBlock(target))break;
				var state = this.task.action() == Action.WATER ? RatsBlockRegistry.RAT_FARM_LIGHT.get().defaultBlockState() : this.task.action() == Action.LIGHT ? RatsBlockRegistry.RAT_FARM_LIGHT.get().defaultBlockState() : Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7);
				changed = this.rat.level().setBlockAndUpdate(target, state); if (changed&&this.task.action()!=Action.WATER) this.farm.prepared(this.task.cell());
			}
			case PLANT -> {
				var desired = this.farm.seedAt(this.task.cell()); int slot = this.cargoSlot(desired);
				if (slot >= 0 && this.rat.level().isEmptyBlock(target) && RatUtils.canRatPlaceBlock(this.rat.level(), target, this.rat) && desired.getItem() instanceof BlockItem seed) changed = seed.place(new DirectionalPlaceContext(this.rat.level(), target, Direction.DOWN, this.rat.cargo.get(slot), Direction.UP)).consumesAction();
			}
			case GROW -> {int slot=this.cargoSlot(new ItemStack(Items.BONE_MEAL));if(slot>=0)changed=BoneMealItem.growCrop(this.rat.cargo.get(slot),this.rat.level(),target);}
		}
		if (changed) { this.rat.recordWorkProgress(); this.rat.cargo.promote(); if (this.rat.cargo.isFull() && (this.task.action() == Action.HARVEST || this.task.action() == Action.CLEAR)) this.returnCargo(); } else if (this.task != null) this.deferred.put(this.task.cell(), this.rat.tickCount + 100);
		this.stop();
		if (!this.rat.farmReturning && this.canUse()) this.start();
	}
}
