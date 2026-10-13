package com.github.alexthe666.rats.server.inventory;
import com.github.alexthe666.rats.registry.RatsMenuRegistry;
import com.github.alexthe666.rats.server.block.RatStorageBlock;
import com.github.alexthe666.rats.server.block.entity.RatStorageBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import com.github.alexthe666.rats.server.storage.*;
import com.github.alexthe666.rats.server.message.*;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

public class RatStorageMenu extends AbstractContainerMenu {
	public final RatStorageBlockEntity storage;
	public final boolean controller;
	private final Container inventory, receiving;
	private final Player viewer;
	public int page;
	public CompoundTag snapshot = new CompoundTag();
	private final CraftingContainer crafting = new TransientCraftingContainer(this, 3, 3);
	private final ResultContainer result = new ResultContainer();
	private List<StorageStock.Entry> entries = List.of();
	private final Map<StorageStock.Key, Integer> catalogIds = new HashMap<>();
	private final Map<Integer, StorageStock.Entry> catalogById = new HashMap<>();
	private int refreshAt, actionAt, selectedOutput;
	private boolean initializing = true;
	public RatStorageMenu(int id, Inventory player, RatStorageBlockEntity storage) { this(id, player, storage, storage.kind() == RatStorageBlock.Kind.CONTROLLER); }
	private RatStorageMenu(int id, Inventory player, RatStorageBlockEntity storage, boolean controller) {
		super(RatsMenuRegistry.RAT_STORAGE_CONTAINER.get(), id); this.storage = storage; this.controller = controller; this.viewer = player.player; this.inventory = storage == null ? new SimpleContainer(54) : storage;
		this.receiving = storage == null ? new SimpleContainer(18) : storage.session(player.player.getUUID());
		if (controller) {
			this.addSlot(new ResultSlot(player.player, this.crafting, this.result, 0, 340, 82) { @Override public boolean isActive() { return RatStorageMenu.this.page == 0; } });
			for (int i = 0; i < 9; i++) { this.crafting.setItem(i, this.receiving.getItem(i).copy()); this.addSlot(new Slot(this.crafting, i, 242 + i % 3 * 18, 64 + i / 3 * 18) { @Override public boolean isActive() { return RatStorageMenu.this.page == 0; } }); }
			for (int i = 0; i < 9; i++) this.addSlot(new Slot(this.receiving, i + 9, 222 + i * 18, 138) { @Override public boolean isActive() { return RatStorageMenu.this.page == 0; } });
			for (int i = 0; i < 54; i++) this.addSlot(new Slot(this.inventory, i, 8 + i % 9 * 18, 58 + i / 9 * 18) { @Override public boolean isActive() { return RatStorageMenu.this.page == 2; } });
		} else for (int i = 0; i < 54; i++) this.addSlot(new Slot(this.inventory, i, 8 + i % 9 * 18, 32 + i / 9 * 18));
		int x = controller ? 112 : 8, y = controller ? 194 : 154;
		for (int i = 0; i < 27; i++) this.addSlot(new Slot(player, i + 9, x + i % 9 * 18, y + i / 9 * 18));
		for (int i = 0; i < 9; i++) this.addSlot(new Slot(player, i, x + i * 18, y + 58));
		this.initializing = false; this.slotsChanged(this.crafting);
	}
	public RatStorageMenu(int id, Inventory inventory, FriendlyByteBuf buf) { this(id, inventory, null, buf.readBoolean()); }
	@Override public boolean stillValid(Player player) { return this.storage == null || this.storage.stillValid(player); }
	@Override public ItemStack quickMoveStack(Player player, int index) {
		if (index < 0 || index >= this.slots.size()) return ItemStack.EMPTY;
		Slot slot = this.slots.get(index); if (!slot.hasItem() || !slot.isActive()) return ItemStack.EMPTY; ItemStack held = slot.getItem(), copy = held.copy(); int start = this.controller ? 73 : 54;
		if (index < start) { if (!this.moveItemStackTo(held, start, start + 36, true)) return ItemStack.EMPTY; if (this.controller && index == 0) slot.onQuickCraft(held, copy); }
		else if (this.controller) { if (this.page == 0) { if (!this.moveItemStackTo(held, 1, 10, false)) return ItemStack.EMPTY; } else if (this.page == 2) { if (!this.moveItemStackTo(held, 19, 73, false)) return ItemStack.EMPTY; } else return ItemStack.EMPTY; }
		else if (!this.moveItemStackTo(held, 0, 54, false)) return ItemStack.EMPTY;
		if (held.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged(); slot.onTake(player, held); this.saveCraft(); return copy;
	}
	@Override public void slotsChanged(Container changed) {
		if (this.initializing || !this.controller || this.viewer == null || this.viewer.level().isClientSide()) return;
		this.saveCraft(); var recipe = this.viewer.level().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, this.crafting, this.viewer.level()); ItemStack output = ItemStack.EMPTY;
		if (recipe.isPresent() && this.result.setRecipeUsed(this.viewer.level(), (ServerPlayer)this.viewer, recipe.get())) { var assembled = recipe.get().assemble(this.crafting, this.viewer.level().registryAccess()); if (assembled.isItemEnabled(this.viewer.level().enabledFeatures())) output = assembled; }
		this.result.setItem(0, output); this.broadcastChanges();
	}
	@Override public void removed(Player player) { super.removed(player); this.saveCraft(); }
	private void saveCraft() { if (this.storage != null && this.controller) { for (int i = 0; i < 9; i++) this.receiving.setItem(i, this.crafting.getItem(i).copy()); this.storage.setChanged(); } }
	public void action(ServerPlayer player, int action, int index, int amount) {
		if (!this.controller || this.storage == null || player != this.viewer || !this.stillValid(player) || player.tickCount < this.actionAt) return;
		this.actionAt = player.tickCount + 3;
		if (action == 0 && this.catalogById.containsKey(index)) { var outputs = this.storage.endpoints.stream().filter(p -> StorageStock.output(this.storage, p)).toList(); if (!outputs.isEmpty()) this.storage.request(player.getUUID(), this.catalogById.get(index).item(), amount, outputs.get(Math.floorMod(this.selectedOutput, outputs.size())), -1); }
		else if (action == 1 && index >= 0 && index < this.storage.workers.size()) { UUID id = this.storage.workers.get(index); var rat = player.serverLevel().getEntity(id); if (rat instanceof TamedRat tame && tame.isOwnedBy(player) || player.getUUID().equals(this.storage.owner)) { this.storage.recalling.add(id); this.storage.setChanged(); } }
		else if (action == 2 && index >= 0 && index <= 2) this.page = index;
		else if (action == 3) this.fetchIngredients(player);
		else if (action == 4 && player.getUUID().equals(this.storage.owner)) { this.storage.organize = true; this.storage.setChanged(); }
		else if (action == 5) this.selectedOutput++;
		else if (action == 6 && index >= 0 && index < this.storage.endpoints.size() && player.getUUID().equals(this.storage.owner)) { BlockPos endpoint = this.storage.endpoints.get(index); if (!(player.level().getBlockEntity(endpoint) instanceof RatStorageBlockEntity)) { this.storage.categories.put(endpoint, "mixed"); this.storage.setChanged(); } }
		this.refreshAt = 0; this.broadcastChanges();
	}
	private void fetchIngredients(ServerPlayer player) {
		for (int i = 0; i < 9; i++) { var ingredient = this.crafting.getItem(i); if (ingredient.isEmpty()) continue; final int cell = i;
			int pending = this.storage.requests.stream().filter(r -> r.player().equals(player.getUUID()) && r.craftSlot() == cell).mapToInt(r -> r.amount() - r.delivered()).sum();
			int wanted = Math.min(16, ingredient.getMaxStackSize()) - ingredient.getCount() - this.receiving.getItem(9 + i).getCount() - pending;
			long available = this.storage.catalog().stream().filter(e -> StorageInventory.same(e.item(), ingredient)).mapToLong(StorageStock.Entry::available).sum();
			if (wanted > 0 && available > 0) this.storage.request(player.getUUID(), ingredient, (int)Math.min(wanted, available), this.storage.getBlockPos(), i);
		}
	}
	@Override public void broadcastChanges() {
		if (!this.initializing && this.controller && this.storage != null && this.viewer instanceof ServerPlayer player && this.stillValid(player) && player.tickCount >= this.refreshAt) { this.refreshAt = player.tickCount + 20; this.entries = this.storage.catalog(); this.snapshot = this.makeSnapshot(player); RatsNetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new StorageSnapshotPacket(this.containerId, this.snapshot)); }
		super.broadcastChanges();
	}
	private CompoundTag makeSnapshot(ServerPlayer player) {
		var root = new CompoundTag(); ListTag stock = new ListTag();
		this.catalogById.clear();
		for (var e : this.entries) { int id = this.catalogIds.computeIfAbsent(StorageStock.Key.of(e.item()), key -> this.catalogIds.size()); this.catalogById.put(id, e); var tag = new CompoundTag(); tag.putInt("Index", id); tag.put("Item", e.item().save(new CompoundTag())); tag.putLong("Available", e.available()); tag.putLong("Reserved", e.reserved()); tag.putLong("Transit", e.transit()); tag.putLong("Input", e.input()); stock.add(tag); } root.put("Stock", stock);
		ListTag workers = new ListTag(); for (UUID id : this.storage.workers) { var tag = new CompoundTag(); var rat = player.serverLevel().getEntity(id); tag.putString("Name", rat == null ? "—" : rat.getName().getString()); tag.putInt("Entity", rat == null ? -1 : rat.getId()); tag.putString("Status", rat == null ? "unloaded" : this.storage.recalling.contains(id) ? "recalling" : this.storage.tasks.containsKey(id) ? this.storage.tasks.get(id).collected ? "delivering" : "collecting" : "idle"); workers.add(tag); } root.put("Workers", workers);
		ListTag nodes = new ListTag(); var graph = StorageRoutes.graph(player.level(), this.storage.getBlockPos()); for (BlockPos pos : this.storage.endpoints) { var t = new CompoundTag(); t.putLong("Pos", pos.asLong()); t.putString("Category", this.storage.categories.getOrDefault(pos, "unclassified")); t.putBoolean("Loaded", player.level().hasChunkAt(pos)); t.putBoolean("Route", player.level().getBlockEntity(pos) instanceof RatStorageBlockEntity || StorageRoutes.port(player.level(), pos, graph) != null); nodes.add(t); } root.put("Endpoints", nodes);
		root.putInt("Requests", this.storage.requests.size()); root.putInt("Tubes", graph.size()); root.putInt("Output", this.selectedOutput); return root;
	}
}
