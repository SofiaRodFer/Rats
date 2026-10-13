package com.github.alexthe666.rats.server.block.entity;

import com.github.alexthe666.rats.registry.*;
import com.github.alexthe666.rats.server.block.RatStorageBlock;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.inventory.RatStorageMenu;
import com.github.alexthe666.rats.server.misc.RatUpgradeUtils;
import com.github.alexthe666.rats.server.storage.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.*;
import net.minecraftforge.items.wrapper.InvWrapper;
import java.util.*;

public class RatStorageBlockEntity extends BaseContainerBlockEntity {
	private NonNullList<ItemStack> items = NonNullList.withSize(54, ItemStack.EMPTY);
	public UUID owner;
	public UUID network = UUID.randomUUID();
	public BlockPos controller;
	public UUID controllerNetwork;
	public final List<BlockPos> endpoints = new ArrayList<>();
	public final List<UUID> workers = new ArrayList<>();
	public final Set<UUID> recalling = new HashSet<>();
	public final Map<UUID, StorageTask> tasks = new HashMap<>();
	public final List<StorageRequest> requests = new ArrayList<>();
	public final Map<BlockPos, String> categories = new HashMap<>();
	public final Map<UUID, SimpleContainer> sessions = new HashMap<>();
	public boolean organize;
	private final LazyOptional<IItemHandler> handler = LazyOptional.of(() -> new InvWrapper(this));
	public RatStorageBlockEntity(BlockPos pos, BlockState state) { super(RatsBlockEntityRegistry.RAT_STORAGE.get(), pos, state); }
	public RatStorageBlock.Kind kind() { return ((RatStorageBlock)this.getBlockState().getBlock()).kind; }
	public IItemHandler inventory() { return this.handler.resolve().orElseThrow(); }
	public SimpleContainer session(UUID player) { return this.sessions.computeIfAbsent(player, id -> { var container = new SimpleContainer(18); container.addListener(c -> this.setChanged()); return container; }); }
	public void dropSessions() { if (this.level != null) this.sessions.values().forEach(c -> Containers.dropContents(this.level, this.worldPosition, c)); }
	public void destroyNetwork() {
		if (this.kind() != RatStorageBlock.Kind.CONTROLLER || !(this.level instanceof ServerLevel server)) return;
		for (BlockPos pos : this.endpoints) { StorageLedger.get(server).release(StorageStock.identity(server, pos), this.network); if (server.getBlockEntity(pos) instanceof RatStorageBlockEntity port) { port.controller = null; port.controllerNetwork = null; port.setChanged(); } }
		for (UUID id : this.workers) if (server.getEntity(id) instanceof TamedRat rat) { rat.getPersistentData().remove("RatStorageNetwork"); rat.getPersistentData().remove("RatStorageController"); rat.getPersistentData().remove("RatStorageDimension"); }
	}
	public boolean connect(BlockPos pos) {
		if (this.level == null || !StorageRoutes.within(this.worldPosition, pos) || pos.equals(this.worldPosition) || this.endpoints.size() >= 128) return false;
		if (this.level.getBlockEntity(pos) instanceof RatStorageBlockEntity port) {
			if (port.kind() == RatStorageBlock.Kind.CONTROLLER || this.worldPosition.distManhattan(pos) != 1 || port.controller != null && (!port.controller.equals(this.worldPosition) || !this.network.equals(port.controllerNetwork))) return false;
			port.controller = this.worldPosition; port.controllerNetwork = this.network; port.setChanged();
		} else {
			if (StorageRoutes.port(this.level, pos, StorageRoutes.graph(this.level, this.worldPosition)) == null) return false;
			if (!(this.level instanceof ServerLevel server) || !StorageLedger.get(server).claim(StorageStock.identity(this.level, pos), this.network)) return false;
		}
		if (!this.endpoints.contains(pos)) this.endpoints.add(pos.immutable()); this.setChanged(); return true;
	}
	public boolean disconnect(BlockPos pos) {
		if (!this.endpoints.contains(pos) || this.tasks.values().stream().anyMatch(t -> t.source.equals(pos) || t.destination.equals(pos))) return false;
		this.endpoints.remove(pos); this.categories.remove(pos);
		if (this.level instanceof ServerLevel server) { StorageLedger.get(server).release(StorageStock.identity(this.level, pos), this.network); if (server.getBlockEntity(pos) instanceof RatStorageBlockEntity port) { port.controller = null; port.controllerNetwork = null; port.setChanged(); } }
		this.setChanged(); return true;
	}
	public boolean assign(TamedRat rat) {
		if (rat.isBaby() || rat.isInCage() || rat.isPassenger() || !RatUpgradeUtils.hasUpgrade(rat, RatsItemRegistry.RAT_UPGRADE_STORAGE_KEEPER.get()) || this.workers.size() >= 10 && !this.workers.contains(rat.getUUID())) return false;
		CompoundTag tag = rat.getPersistentData();
		if (tag.hasUUID("RatStorageNetwork") && !tag.getUUID("RatStorageNetwork").equals(this.network)) return false;
		tag.putUUID("RatStorageNetwork", this.network); tag.putLong("RatStorageController", this.worldPosition.asLong()); tag.putString("RatStorageDimension", this.level.dimension().location().toString());
		if (!this.workers.contains(rat.getUUID())) this.workers.add(rat.getUUID()); this.setChanged(); return true;
	}
	public StorageTask task(TamedRat rat) { return this.tasks.get(rat.getUUID()); }
	public List<StorageStock.Entry> catalog() { return StorageStock.scan(this); }
	public boolean request(UUID player, ItemStack item, int amount, BlockPos output, int craftSlot) {
		if (amount < 1 || amount > 100000 || item.isEmpty() || this.requests.size() >= 64 || craftSlot < -1 || craftSlot >= 9) return false;
		if (craftSlot < 0 && !StorageStock.output(this, output)) return false;
		long available = this.catalog().stream().filter(e -> StorageInventory.same(e.item(), item)).mapToLong(StorageStock.Entry::available).sum();
		if (available < amount) return false;
		this.requests.add(new StorageRequest(UUID.randomUUID(), player, item.copyWithCount(1), amount, 0, output, craftSlot)); this.setChanged(); return true;
	}
	@Override protected Component getDefaultName() { return this.getBlockState().getBlock().getName(); }
	@Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return new RatStorageMenu(id, inventory, this); }
	@Override public int getContainerSize() { return 54; }
	@Override public boolean isEmpty() { return this.items.stream().allMatch(ItemStack::isEmpty); }
	@Override public ItemStack getItem(int slot) { return this.items.get(slot); }
	@Override public ItemStack removeItem(int slot, int count) { var result = ContainerHelper.removeItem(this.items, slot, count); if (!result.isEmpty()) this.setChanged(); return result; }
	@Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(this.items, slot); }
	@Override public void setItem(int slot, ItemStack stack) { this.items.set(slot, stack); this.setChanged(); }
	@Override public void clearContent() { this.items.clear(); this.setChanged(); }
	@Override public boolean stillValid(Player player) { return this.level != null && this.level.getBlockEntity(this.worldPosition) == this && player.distanceToSqr(this.worldPosition.getCenter()) <= 64; }
	@Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) { return capability == ForgeCapabilities.ITEM_HANDLER ? this.handler.cast() : super.getCapability(capability, side); }
	@Override public void invalidateCaps() { super.invalidateCaps(); this.handler.invalidate(); }
	@Override protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag); ContainerHelper.saveAllItems(tag, this.items); tag.putInt("StorageVersion", 1); tag.putUUID("Network", this.network); if (this.owner != null) tag.putUUID("Owner", this.owner);
		if (this.controller != null) { tag.putLong("Controller", this.controller.asLong()); if (this.controllerNetwork != null) tag.putUUID("ControllerNetwork", this.controllerNetwork); }
		tag.putLongArray("Endpoints", this.endpoints.stream().mapToLong(BlockPos::asLong).toArray());
		ListTag crew = new ListTag(); for (UUID id : this.workers) { var t = new CompoundTag(); t.putUUID("Id", id); t.putBoolean("Recall", this.recalling.contains(id)); var task = this.tasks.get(id); if (task != null) t.put("Task", task.save()); crew.add(t); } tag.put("Workers", crew);
		ListTag queue = new ListTag(); this.requests.forEach(r -> queue.add(r.save())); tag.put("Requests", queue);
		ListTag labels = new ListTag(); this.categories.forEach((pos, category) -> { var t = new CompoundTag(); t.putLong("Pos", pos.asLong()); t.putString("Category", category); labels.add(t); }); tag.put("Categories", labels); tag.putBoolean("Organize", this.organize);
		ListTag users = new ListTag(); this.sessions.forEach((id, container) -> { var t = new CompoundTag(); t.putUUID("Id", id); var contents = NonNullList.withSize(18, ItemStack.EMPTY); for (int i = 0; i < 18; i++) contents.set(i, container.getItem(i)); ContainerHelper.saveAllItems(t, contents); users.add(t); }); tag.put("Sessions", users);
	}
	@Override public void load(CompoundTag tag) {
		super.load(tag); this.items = NonNullList.withSize(54, ItemStack.EMPTY); ContainerHelper.loadAllItems(tag, this.items);
		if (tag.hasUUID("Network")) this.network = tag.getUUID("Network"); this.owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
		this.controller = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null; this.controllerNetwork = tag.hasUUID("ControllerNetwork") ? tag.getUUID("ControllerNetwork") : null;
		this.endpoints.clear(); for (long p : tag.getLongArray("Endpoints")) { BlockPos pos = BlockPos.of(p); if (this.endpoints.size() < 128 && StorageRoutes.within(this.worldPosition, pos) && !this.endpoints.contains(pos)) this.endpoints.add(pos); }
		this.workers.clear(); this.tasks.clear(); this.recalling.clear(); for (Tag value : tag.getList("Workers", Tag.TAG_COMPOUND)) { var t = (CompoundTag)value; if (!t.hasUUID("Id") || this.workers.size() >= 10) continue; UUID id = t.getUUID("Id"); if (this.workers.contains(id)) continue; this.workers.add(id); if (t.getBoolean("Recall")) this.recalling.add(id); if (t.contains("Task")) this.tasks.put(id, StorageTask.load(t.getCompound("Task"))); }
		this.requests.clear(); for (Tag value : tag.getList("Requests", Tag.TAG_COMPOUND)) if (this.requests.size() < 64) this.requests.add(StorageRequest.load((CompoundTag)value));
		this.categories.clear(); for (Tag value : tag.getList("Categories", Tag.TAG_COMPOUND)) { var t = (CompoundTag)value; this.categories.put(BlockPos.of(t.getLong("Pos")), t.getString("Category")); } this.organize = tag.getBoolean("Organize");
		this.sessions.clear(); for (Tag value : tag.getList("Sessions", Tag.TAG_COMPOUND)) { var t = (CompoundTag)value; if (!t.hasUUID("Id")) continue; var contents = NonNullList.withSize(18, ItemStack.EMPTY); ContainerHelper.loadAllItems(t, contents); var container = this.session(t.getUUID("Id")); for (int i = 0; i < 18; i++) container.setItem(i, contents.get(i)); }
	}
}
