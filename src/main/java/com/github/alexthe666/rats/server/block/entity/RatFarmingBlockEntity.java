package com.github.alexthe666.rats.server.block.entity;

import com.github.alexthe666.rats.registry.*;
import com.github.alexthe666.rats.server.block.RatFarmingBlock;
import com.github.alexthe666.rats.server.entity.rat.*;
import com.github.alexthe666.rats.server.inventory.RatFarmingMenu;
import com.github.alexthe666.rats.server.misc.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.*;
import net.minecraftforge.items.wrapper.InvWrapper;
import java.util.*;

public class RatFarmingBlockEntity extends BaseContainerBlockEntity {
	private NonNullList<ItemStack> items = NonNullList.withSize(54, ItemStack.EMPTY);
	private final NonNullList<ItemStack> plan = NonNullList.withSize(256, ItemStack.EMPTY);
	private FarmLayout layout = new FarmLayout(9, 9);
	private final BitSet prepared = new BitSet(256);
	private final List<UUID> workers = new ArrayList<>();
	private final Map<Integer, Lease> leases = new HashMap<>();
	private record Lease(UUID rat, long expires) { }
	public enum Action { CLEAR, FILL, WATER, LIGHT, TILL, HARVEST, PLANT }
	public record Task(int cell, Action action, BlockPos target) { }
	private final LazyOptional<IItemHandler> handler = LazyOptional.of(() -> new InvWrapper(this));
	public final Container ghost = new SimpleContainer(256) {
		@Override public ItemStack getItem(int slot) { return RatFarmingBlockEntity.this.plan.get(slot); }
		@Override public void setItem(int slot, ItemStack stack) { RatFarmingBlockEntity.this.plan.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1)); RatFarmingBlockEntity.this.setChanged(); }
	};
	public final ContainerData data = new ContainerData() {
		public int get(int index) { return switch(index) { case 0 -> layout.width; case 1 -> layout.height; case 2 -> facing().get2DDataValue(); case 3 -> workerIds().size(); default -> 0; }; }
		public void set(int index, int value) { }
		public int getCount() { return 4; }
	};
	public RatFarmingBlockEntity(BlockPos pos, BlockState state) { super(RatsBlockEntityRegistry.RAT_FARMING.get(), pos, state); }
	public FarmLayout layout() { return this.layout; }
	public Direction facing() { return this.getBlockState().getValue(RatFarmingBlock.FACING); }
	public BlockPos ground(int index) { return this.layout.ground(this.worldPosition, this.facing(), index); }
	public void configure(int width, int height) { this.layout = new FarmLayout(width, height); this.prepared.clear(); this.leases.clear(); this.setChanged(); }
	public ItemStack seedAt(int index) { return this.plan.get(index); }
	public boolean paint(int index, ItemStack seed) {
		if (!this.layout.cropCell(index) || (!seed.isEmpty() && !FarmCrops.seed(seed))) return false;
		this.ghost.setItem(index, seed); this.leases.remove(index); return true;
	}
	public List<UUID> workerIds() {
		if (this.level instanceof net.minecraft.server.level.ServerLevel server) this.workers.removeIf(id -> {
			var entity = server.getEntity(id);
			return entity != null && (!(entity instanceof TamedRat rat) || !rat.isAlive() || !RatUpgradeUtils.hasUpgrade(rat, RatsItemRegistry.RAT_UPGRADE_FARMER.get()) || rat.getDepositPos().filter(pos -> pos.dimension().equals(server.dimension()) && pos.pos().equals(this.worldPosition)).isEmpty());
		});
		return List.copyOf(this.workers);
	}
	public boolean assignWorker(TamedRat rat) {
		this.workerIds(); if (this.workers.contains(rat.getUUID())) return true;
		if (this.workers.size() >= 2 || !RatUpgradeUtils.hasUpgrade(rat, RatsItemRegistry.RAT_UPGRADE_FARMER.get())) return false;
		this.workers.add(rat.getUUID()); this.setChanged(); return true;
	}
	public boolean ownsHalf(TamedRat rat, int cell) {
		if (!this.layout.contains(cell) || !(this.level instanceof net.minecraft.server.level.ServerLevel server)) return false;
		var active = this.workerIds().stream().map(server::getEntity).filter(TamedRat.class::isInstance).map(TamedRat.class::cast).filter(worker -> worker.getCommand() == RatCommand.HARVEST && !worker.isInCage() && !worker.isInTube()).toList();
		int index = active.indexOf(rat), order = cell / 16 * this.layout.width + cell % 16;
		return index >= 0 && order * active.size() / (this.layout.width * this.layout.height) == index;
	}
	public void release(TamedRat rat) { this.leases.entrySet().removeIf(entry -> entry.getValue().rat.equals(rat.getUUID())); }
	public boolean owns(Task task, TamedRat rat) { var lease = this.leases.get(task.cell); return this.level != null && lease != null && lease.rat.equals(rat.getUUID()) && lease.expires >= this.level.getGameTime() && this.ownsHalf(rat, task.cell) && task.equals(this.taskAt(task.cell, rat)); }
	public void renew(Task task, TamedRat rat) { if (this.owns(task, rat)) this.leases.put(task.cell, new Lease(rat.getUUID(), this.level.getGameTime() + 100)); }
	public Task claim(TamedRat rat, Set<Integer> deferred) {
		if (this.level == null || !this.assignWorker(rat)) return null; this.release(rat);
		Task best = null;
		for (int cell = 0; cell < 256; cell++) {
			if (!this.ownsHalf(rat, cell) || deferred.contains(cell)) continue;
			var lease = this.leases.get(cell); if (lease != null && lease.expires >= this.level.getGameTime()) continue;
			Task task = this.taskAt(cell, rat); if (task == null) continue;
			if (best == null || priority(task) < priority(best) || (priority(task) == priority(best) && rat.distanceToSqr(task.target.getCenter()) < rat.distanceToSqr(best.target.getCenter()))) best = task;
		}
		if (best != null) this.leases.put(best.cell, new Lease(rat.getUUID(), this.level.getGameTime() + 100)); return best;
	}
	private static int priority(Task task) { return switch(task.action) { case CLEAR, FILL -> 0; case WATER, LIGHT -> 1; case TILL -> 2; case HARVEST -> 3; case PLANT -> 4; }; }
	public Task taskAt(int cell, TamedRat rat) {
		if (!this.layout.contains(cell) || this.level == null) return null;
		BlockPos ground = this.ground(cell); if (!this.level.hasChunkAt(ground) || this.level.isOutsideBuildHeight(ground) || !this.level.hasChunkAt(ground.above())) return null;
		BlockState soil = this.level.getBlockState(ground);
		boolean repair = RatUpgradeUtils.hasUpgrade(rat, RatsItemRegistry.RAT_UPGRADE_REPLANTER.get());
		boolean initial = !this.prepared.get(cell);
		if (initial || repair) {
			// Clear natural terrain bottom-up; leave buildings, roofs, machinery
			// and crops alone. The fixed ground plane never follows local hills.
			int top = this.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, ground.getX(), ground.getZ());
			for (int y = ground.getY() + 1; y < top; y++) {
				BlockPos pos = new BlockPos(ground.getX(), y, ground.getZ()); var above = this.level.getBlockState(pos);
				if (terrain(above) && this.level.getBlockEntity(pos) == null && RatUtils.canRatBreakBlock(this.level, pos, rat) && !RatUtils.isBlockProtected(this.level, pos, rat)) return new Task(cell, Action.CLEAR, pos);
				// Never tunnel through or above a roof/protected obstruction.
				if (!above.isAir() && !above.is(net.minecraft.tags.BlockTags.CROPS) && !above.canBeReplaced()) break;
			}
			if (soil.isAir() || soil.is(Blocks.WATER) && !this.layout.water.get(cell)) return new Task(cell, Action.FILL, ground);
			if (terrain(soil) && !soil.is(Blocks.DIRT) && !soil.is(Blocks.GRASS_BLOCK) && !soil.is(Blocks.FARMLAND) && RatUtils.canRatBreakBlock(this.level, ground, rat) && !RatUtils.isBlockProtected(this.level, ground, rat)) return new Task(cell, Action.CLEAR, ground);
			if (this.layout.water.get(cell) && this.level.getBlockState(ground.below()).isAir()) return new Task(cell, Action.FILL, ground.below());
		}
		if (this.layout.water.get(cell)) {
			if (soil.is(Blocks.WATER) && soil.getFluidState().isSource()) { this.prepared.set(cell); return null; }
			return (initial || repair) && this.canPrepare(ground, rat) ? new Task(cell, Action.WATER, ground) : null;
		}
		if (this.layout.lights.get(cell)) {
			if (soil.is(RatsBlockRegistry.RAT_FARM_LIGHT.get())) { this.prepared.set(cell); return null; }
			return (initial || repair) && this.canPrepare(ground, rat) ? new Task(cell, Action.LIGHT, ground) : null;
		}
		if (!soil.is(Blocks.FARMLAND)) return (initial || repair) && this.canPrepare(ground, rat) ? new Task(cell, Action.TILL, ground) : null;
		this.prepared.set(cell);
		BlockPos crop = ground.above();
		if (FarmCrops.mature(this.level, crop) && RatUtils.canRatBreakBlock(this.level, crop, rat) && !RatUtils.isBlockProtected(this.level, crop, rat)) return new Task(cell, Action.HARVEST, crop);
		ItemStack seed = this.seedAt(cell);
		if (!seed.isEmpty() && this.level.isEmptyBlock(crop) && FarmCrops.plant(seed).canSurvive(this.level, crop) && !RatUtils.isBlockProtected(this.level, crop, rat) && RatUtils.canRatPlaceBlock(this.level, crop, rat)) return new Task(cell, Action.PLANT, crop);
		return null;
	}
	public boolean canPrepare(BlockPos pos, TamedRat rat) {
		BlockState state = this.level.getBlockState(pos);
		return (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.FARMLAND)) && this.level.isEmptyBlock(pos.above()) && this.level.getBlockEntity(pos) == null && !RatUtils.isBlockProtected(this.level, pos, rat) && RatUtils.canRatPlaceBlock(this.level, pos, rat);
	}
	private static boolean terrain(BlockState state) { return state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || state.is(net.minecraft.tags.BlockTags.DIRT) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK); }
	public void unassign(UUID id) { if (this.workers.remove(id)) { this.leases.entrySet().removeIf(entry -> entry.getValue().rat.equals(id)); this.setChanged(); } }
	public void prepared(int cell) { this.prepared.set(cell); this.setChanged(); }
	public void rotate() { if (this.level != null) { this.level.setBlockAndUpdate(this.worldPosition, this.getBlockState().setValue(RatFarmingBlock.FACING, this.facing().getClockWise())); this.prepared.clear(); this.leases.clear(); this.setChanged(); } }
	@Override public int getContainerSize() { return 54; }
	@Override public boolean isEmpty() { return this.items.stream().allMatch(ItemStack::isEmpty); }
	@Override public ItemStack getItem(int slot) { return this.items.get(slot); }
	@Override public ItemStack removeItem(int slot, int amount) { ItemStack out = ContainerHelper.removeItem(this.items, slot, amount); if (!out.isEmpty()) this.setChanged(); return out; }
	@Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(this.items, slot); }
	@Override public void setItem(int slot, ItemStack stack) { this.items.set(slot, stack); stack.setCount(Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), this.getMaxStackSize()))); this.setChanged(); }
	@Override public void clearContent() { this.items.clear(); }
	@Override public boolean stillValid(Player player) { return this.level != null && this.level.getBlockEntity(this.worldPosition) == this && player.distanceToSqr(this.worldPosition.getCenter()) <= 64; }
	@Override protected Component getDefaultName() { return Component.translatable("block.rats.rat_farming_block"); }
	@Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return new RatFarmingMenu(id, inventory, this); }
	@Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction facing) { return capability == ForgeCapabilities.ITEM_HANDLER && !this.isRemoved() ? this.handler.cast() : super.getCapability(capability, facing); }
	@Override public void invalidateCaps() { super.invalidateCaps(); this.handler.invalidate(); }
	@Override protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag); ContainerHelper.saveAllItems(tag, this.items);
		tag.putInt("FarmWidth", this.layout.width); tag.putInt("FarmHeight", this.layout.height);
		CompoundTag ghosts = new CompoundTag(); ContainerHelper.saveAllItems(ghosts, this.plan); tag.put("FarmPlan", ghosts); tag.putLongArray("FarmPrepared", this.prepared.toLongArray());
		ListTag crew = new ListTag(); for (UUID id : this.workers) { CompoundTag worker = new CompoundTag(); worker.putUUID("Id", id); crew.add(worker); } tag.put("FarmWorkers", crew);
	}
	@Override public void load(CompoundTag tag) {
		super.load(tag); this.items = NonNullList.withSize(54, ItemStack.EMPTY); ContainerHelper.loadAllItems(tag, this.items);
		this.layout = new FarmLayout(tag.contains("FarmWidth") ? tag.getInt("FarmWidth") : 9, tag.contains("FarmHeight") ? tag.getInt("FarmHeight") : 9);
		for (int i = 0; i < 256; i++) this.plan.set(i, ItemStack.EMPTY); ContainerHelper.loadAllItems(tag.getCompound("FarmPlan"), this.plan);
		for (int i = 0; i < 256; i++) { var seed = this.plan.get(i); if (!seed.isEmpty()) this.plan.set(i, FarmCrops.seed(seed) ? seed.copyWithCount(1) : ItemStack.EMPTY); }
		this.prepared.clear(); this.prepared.or(BitSet.valueOf(tag.getLongArray("FarmPrepared"))); this.prepared.clear(256, Math.max(256, this.prepared.length())); this.leases.clear(); this.workers.clear();
		for (Tag entry : tag.getList("FarmWorkers", Tag.TAG_COMPOUND)) { var worker = (CompoundTag)entry; if (this.workers.size() < 2 && worker.hasUUID("Id") && !this.workers.contains(worker.getUUID("Id"))) this.workers.add(worker.getUUID("Id")); }
	}
}
