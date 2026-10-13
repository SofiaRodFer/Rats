package com.github.alexthe666.rats.server.entity.ai.goal;

import com.github.alexthe666.rats.registry.*;
import com.github.alexthe666.rats.server.block.RatStorageBlock;
import com.github.alexthe666.rats.server.block.entity.RatStorageBlockEntity;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.misc.RatUpgradeUtils;
import com.github.alexthe666.rats.server.storage.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.*;
import java.util.*;

/** Owns the whole delivery until completion. No generic pickup/deposit goals. */
public class RatStorageGoal extends Goal {
	private final TamedRat rat;
	private List<BlockPos> route = List.of();
	private BlockPos destination;
	private int retryAt, stalled;
	private boolean exiting;
	public String status = "idle";
	public RatStorageGoal(TamedRat rat) { this.rat = rat; this.setFlags(EnumSet.allOf(Flag.class)); }
	@Override public boolean canUse() { return this.rat.getPersistentData().hasUUID("RatStorageNetwork") && !this.rat.isPassenger() && !this.rat.isInCage() && this.rat.getRespawnCountdown() <= 0; }
	@Override public boolean requiresUpdateEveryTick() { return true; }
	public static RatStorageBlockEntity controller(TamedRat rat) {
		var tag = rat.getPersistentData(); if (!tag.hasUUID("RatStorageNetwork") || !tag.getString("RatStorageDimension").equals(rat.level().dimension().location().toString())) return null;
		BlockPos pos = BlockPos.of(tag.getLong("RatStorageController")); if (!rat.level().hasChunkAt(pos)) return null;
		return rat.level().getBlockEntity(pos) instanceof RatStorageBlockEntity storage && storage.kind() == RatStorageBlock.Kind.CONTROLLER && storage.network.equals(tag.getUUID("RatStorageNetwork")) ? storage : null;
	}
	@Override public void start() { this.rat.getNavigation().stop(); this.route = List.of(); }
	@Override public void stop() { this.rat.getNavigation().stop(); this.rat.setDeltaMovement(Vec3.ZERO); this.route = List.of(); }
	@Override public void tick() {
		this.rat.getPersistentData().putString("RatStorageStatus", this.status);
		if (this.rat.isInTube() || this.rat.level().getBlockState(this.rat.blockPosition()).getBlock() instanceof RatStorageBlock) this.rat.getPersistentData().putBoolean("RatStorageInside", true);
		this.rat.setTarget(null); this.rat.getMoveControl().setWantedPosition(this.rat.getX(), this.rat.getY(), this.rat.getZ(), 0);
		var controller = controller(this.rat); if (controller == null) { this.status = "controller_unavailable"; this.rat.getNavigation().stop(); var tag = this.rat.getPersistentData(); BlockPos pos = BlockPos.of(tag.getLong("RatStorageController")); if (!tag.getString("RatStorageDimension").equals(this.rat.level().dimension().location().toString()) || this.rat.level().hasChunkAt(pos) && !(this.rat.level().getBlockEntity(pos) instanceof RatStorageBlockEntity current && current.network.equals(tag.getUUID("RatStorageNetwork")))) this.clearAssignment(); return; }
		if (!controller.workers.contains(this.rat.getUUID())) { this.clearAssignment(); return; }
		if (!RatUpgradeUtils.hasUpgrade(this.rat, RatsItemRegistry.RAT_UPGRADE_STORAGE_KEEPER.get())) controller.recalling.add(this.rat.getUUID());
		if (this.rat.tickCount < this.retryAt || this.rat.isInventoryOpen()) return;
		if (this.exiting) { this.exit(controller); return; }
		if (!this.rat.getPersistentData().getBoolean("RatStorageInside") && !this.rat.isInTube() && !this.rat.blockPosition().equals(controller.getBlockPos())) { this.status = "entering"; this.enter(controller); return; }
		StorageTask task = controller.task(this.rat);
		if (task != null && controller.recalling.contains(this.rat.getUUID())) {
			this.status = "recalling";
			if (!task.collected) { controller.tasks.remove(this.rat.getUUID()); controller.setChanged(); return; }
			if (this.travel(controller,controller.getBlockPos())) { this.deposit(controller.inventory()); task.capture(this.rat); controller.setChanged(); if (this.rat.cargo.isEmpty()) this.finish(controller); else this.block("recovery_full"); }
			return;
		}
		if (task == null) {
			if (!this.rat.cargo.isEmpty()) { this.status = "returning_cargo"; if (this.travel(controller, controller.getBlockPos())) { this.deposit(controller.inventory()); if (!this.rat.cargo.isEmpty()) this.block("recovery_full"); } return; }
			if (controller.recalling.contains(this.rat.getUUID())) {
				this.status = "recalling";
				if (this.travel(controller, controller.getBlockPos())) {
					Direction front = controller.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING); BlockPos exit = controller.getBlockPos().relative(front);
					if (!this.rat.level().hasChunkAt(exit) || !this.rat.level().getBlockState(exit).getCollisionShape(this.rat.level(), exit).isEmpty()) { this.block("exit_blocked"); return; }
					this.exiting = true; this.exit(controller);
				} return;
			}
			if (!this.rat.blockPosition().equals(controller.getBlockPos()) && !this.rat.isInTube()) { this.status = "entering"; this.enter(controller); return; }
			task = StorageScheduler.claim(controller, this.rat);
			if (task == null) { this.status = "idle"; this.retryAt = this.rat.tickCount + 20; this.rat.getNavigation().stop(); this.rat.setDeltaMovement(Vec3.ZERO); return; }
		}
		BlockPos inventory = task.collected ? task.destination : task.source; Direction face = task.collected ? task.destinationFace : task.sourceFace;
		BlockPos dock = inventory.equals(controller.getBlockPos()) || this.rat.level().getBlockEntity(inventory) instanceof RatStorageBlockEntity ? controller.getBlockPos() : inventory.relative(face);
		this.status = task.collected ? "delivering" : "collecting";
		if (!this.travel(controller, dock)) return;
		IItemHandler handler = StorageRoutes.handler(this.rat.level(), inventory, face);
		if (task.collected && task.destination.equals(controller.getBlockPos()) && task.request != null) {
			UUID requestId = task.request; var request = controller.requests.stream().filter(r -> r.id().equals(requestId)).findFirst().orElse(null);
			if (request != null && request.craftSlot() >= 0) handler = new net.minecraftforge.items.wrapper.RangedWrapper(new net.minecraftforge.items.wrapper.InvWrapper(controller.session(request.player())), 9 + request.craftSlot(), 10 + request.craftSlot());
		}
		if (handler == null) { if (!task.collected) this.finish(controller); else this.block("inventory_unavailable"); return; }
		if (!task.collected) {
			int collected = 0;
			for (int i = 0; i < this.rat.cargo.slots() && collected < task.maximum; i++) {
				int amount = Math.min(task.item.getMaxStackSize(), task.maximum - collected);
				int fits = amount - this.rat.cargo.insert(task.item.copyWithCount(amount), true).getCount(); if (fits <= 0) break;
				ItemStack extracted = StorageInventory.extract(handler, task.item, fits); if (extracted.isEmpty()) break;
				ItemStack remainder = this.rat.cargo.insert(extracted, false); if (!remainder.isEmpty()) this.rat.spawnAtLocation(remainder); collected += extracted.getCount() - remainder.getCount();
			}
			if (collected == 0) { this.finish(controller); return; }
			task.collected = true; task.loaded = collected; controller.setChanged(); this.route = List.of(); this.rat.recordWorkProgress();
			// Fill spare tray slots with other compatible items from this input.
			// Extraction is synchronous and revalidated; other reserved parcels are
			// excluded. No secondary collection once the delivery has started.
			if (task.request == null && this.rat.cargo.hasTray() && this.rat.level().getBlockEntity(task.source) instanceof RatStorageBlockEntity input && input.kind() == RatStorageBlock.Kind.INPUT) {
				var destinationInventory = StorageRoutes.handler(this.rat.level(), task.destination, task.destinationFace);
				if (destinationInventory != null) for (int slot = 0; slot < handler.getSlots() && !this.rat.cargo.isFull(); slot++) {
					var sample = handler.getStackInSlot(slot).copy(); if (sample.isEmpty() || !StorageStock.category(sample).equals(StorageStock.category(task.item))) continue;
					int reserved = 0; for (var other : controller.tasks.values()) if (other != task && !other.collected && other.source.equals(task.source) && StorageInventory.same(other.item, sample)) reserved += other.maximum;
					int available = (int)Math.max(0, Math.min(sample.getMaxStackSize(), StorageInventory.count(handler, sample) - reserved));
					int fitsCargo = available - this.rat.cargo.insert(sample.copyWithCount(available), true).getCount(); int fitsChest = StorageInventory.capacity(destinationInventory, sample, fitsCargo);
					if (fitsChest <= 0) continue; var extracted = StorageInventory.extract(handler, sample, fitsChest); var remainder = this.rat.cargo.insert(extracted, false); if (!remainder.isEmpty()) this.rat.spawnAtLocation(remainder); task.loaded += extracted.getCount() - remainder.getCount();
				}
				controller.setChanged();
			}
			task.capture(this.rat); controller.setChanged();
		} else {
			int count = this.deposit(handler, task.request == null ? ItemStack.EMPTY : task.item); task.delivered += count;
			task.capture(this.rat);
			if (task.request != null && count > 0) for (int i = 0; i < controller.requests.size(); i++) if (controller.requests.get(i).id().equals(task.request)) controller.requests.set(i, controller.requests.get(i).deliver(count));
			controller.setChanged();
			if (this.rat.cargo.isEmpty()) this.finish(controller); else this.block("destination_full");
		}
	}
	private int deposit(IItemHandler handler) { return this.deposit(handler,ItemStack.EMPTY); }
	private int deposit(IItemHandler handler, ItemStack expected) { int amount = 0; for (int i = 0; i < 10; i++) { var cargo = this.rat.cargo.get(i); if (cargo.isEmpty() || !expected.isEmpty() && !StorageInventory.same(cargo,expected)) continue; var left = StorageInventory.insert(handler, cargo, false); amount += cargo.getCount() - left.getCount(); this.rat.cargo.set(i, left); } this.rat.cargo.promote(); return amount; }
	private void finish(RatStorageBlockEntity controller) { controller.tasks.remove(this.rat.getUUID()); controller.requests.removeIf(r -> r.delivered() >= r.amount()); controller.setChanged(); this.route = List.of(); this.destination = null; this.rat.recordWorkProgress(); }
	private void clearAssignment() { detach(this.rat); }
	public static void detach(TamedRat rat) { var tag = rat.getPersistentData(); try { rat.setCommand(com.github.alexthe666.rats.server.entity.rat.RatCommand.valueOf(tag.getString("RatStoragePreviousCommand"))); } catch (IllegalArgumentException ignored) { rat.setCommand(com.github.alexthe666.rats.server.entity.rat.RatCommand.WANDER); } tag.remove("RatStoragePreviousCommand"); tag.remove("RatStorageNetwork"); tag.remove("RatStorageController"); tag.remove("RatStorageDimension"); tag.remove("RatStorageStatus"); tag.remove("RatStorageInside"); rat.setNoGravity(false); rat.setDeltaMovement(Vec3.ZERO); }
	private void exit(RatStorageBlockEntity controller) {
		BlockPos pos = controller.getBlockPos().relative(controller.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
		if (!this.rat.level().hasChunkAt(pos) || !this.rat.level().getBlockState(pos).getCollisionShape(this.rat.level(), pos).isEmpty()) { this.block("exit_blocked"); return; }
		Vec3 target = at(pos), delta = target.subtract(this.rat.position()); this.rat.setDeltaMovement(Vec3.ZERO); this.rat.setPos(this.rat.position().add(delta.normalize().scale(Math.min(0.14, delta.length())))); this.rat.fallDistance = 0;
		if (this.rat.position().distanceToSqr(target) < 0.03) { controller.workers.remove(this.rat.getUUID()); controller.recalling.remove(this.rat.getUUID()); controller.setChanged(); this.exiting = false; this.clearAssignment(); }
	}
	private void block(String reason) { this.status = reason; this.route = List.of(); this.destination = null; this.retryAt = this.rat.tickCount + 40; this.rat.setDeltaMovement(Vec3.ZERO); this.rat.getNavigation().stop(); }
	private static Vec3 at(BlockPos pos) { return new Vec3(pos.getX() + 0.5, pos.getY() + 0.27, pos.getZ() + 0.5); }
	private void enter(RatStorageBlockEntity controller) {
		BlockPos entrance = controller.getBlockPos().relative(controller.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING)); Vec3 point = at(entrance);
		if (this.rat.position().distanceToSqr(point) > 0.4) { this.rat.getNavigation().moveTo(point.x, point.y, point.z, 1.0); if (++this.stalled > 400) { this.stalled = 0; this.block("entry_unreachable"); } return; }
		this.rat.getNavigation().stop(); Vec3 inside = at(controller.getBlockPos()), step = inside.subtract(this.rat.position()); this.rat.setPos(this.rat.position().add(step.normalize().scale(Math.min(0.14, step.length())))); this.rat.fallDistance = 0;
	}
	private boolean travel(RatStorageBlockEntity controller, BlockPos target) {
		this.rat.getNavigation().stop(); this.rat.setDeltaMovement(Vec3.ZERO); this.rat.fallDistance = 0;
		if (!this.rat.blockPosition().equals(controller.getBlockPos()) && !StorageRoutes.tube(this.rat.level(),controller.getBlockPos(),this.rat.blockPosition())) { this.block("tube_broken"); return false; }
		if (!target.equals(controller.getBlockPos()) && !StorageRoutes.tube(this.rat.level(), controller.getBlockPos(), target)) { this.block("tube_broken"); return false; }
		if (this.rat.position().distanceToSqr(at(target)) < 0.01) { this.route = List.of(); this.destination = null; return true; }
		if (!target.equals(this.destination) || this.route.isEmpty()) { this.destination = target; this.route = new ArrayList<>(StorageRoutes.route(this.rat.level(), controller.getBlockPos(), this.rat.blockPosition(), target)); if (!this.route.isEmpty() && !this.route.get(0).equals(this.rat.blockPosition())) this.route.add(0,this.rat.blockPosition()); }
		if (this.route.isEmpty()) { this.block("no_route"); return false; }
		BlockPos next = this.route.get(0);
		if (!next.equals(controller.getBlockPos()) && !StorageRoutes.tube(this.rat.level(), controller.getBlockPos(), next)) { this.block("tube_broken"); return false; }
		Vec3 delta = at(next).subtract(this.rat.position()); if (delta.lengthSqr() < 0.01) { this.rat.setPos(at(next)); this.route.remove(0); return false; }
		this.rat.setNoGravity(true); this.rat.setYRot((float)(Math.atan2(delta.z, delta.x) * 180 / Math.PI - 90)); this.rat.setPos(this.rat.position().add(delta.normalize().scale(Math.min(0.14, delta.length())))); this.rat.recordWorkProgress(); return false;
	}
}
