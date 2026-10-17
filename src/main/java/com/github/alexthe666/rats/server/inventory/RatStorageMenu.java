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
	public final boolean output;
	public final boolean compact;
	private final Container inventory, receiving;
	private final Player viewer;
	public int page;
	public CompoundTag snapshot = new CompoundTag();
	private final CraftingContainer crafting = new TransientCraftingContainer(this, 3, 3);
	private final ResultContainer result = new ResultContainer();
	private List<StorageStock.Entry> entries = List.of();
	private final Map<StorageStock.Key, Integer> catalogIds = new HashMap<>();
	private final Map<Integer, StorageStock.Entry> catalogById = new HashMap<>();
	private final Map<UUID, Integer> workerIds = new HashMap<>();
	private final Map<BlockPos,Integer> endpointIds = new HashMap<>();
	private int refreshAt, actionAt, selectedOutput;
	private String message = "";
	private int messageAt;
	private boolean initializing = true;
	private long revision, receivingRevision = -1;
	private int receivingPart;
	private CompoundTag partialSnapshot;
	private CompoundTag sentSnapshot;
	public void acceptSnapshot(StorageSnapshotPacket packet) {
		if (packet.part() == 0) { this.receivingRevision = packet.revision(); this.receivingPart = 0; this.partialSnapshot = packet.data().copy(); }
		if (packet.revision() != this.receivingRevision || packet.part() != this.receivingPart || this.partialSnapshot == null) return;
		if (packet.part() > 0) this.partialSnapshot.getList("Stock", Tag.TAG_COMPOUND).addAll(packet.data().getList("Stock", Tag.TAG_COMPOUND));
		this.receivingPart++; if (this.receivingPart == packet.parts()) { this.snapshot = this.partialSnapshot; this.partialSnapshot = null; }
	}
	public RatStorageMenu(int id, Inventory player, RatStorageBlockEntity storage) { this(id, player, storage, storage.kind() == RatStorageBlock.Kind.CONTROLLER, storage.kind() == RatStorageBlock.Kind.OUTPUT); }
	private RatStorageMenu(int id, Inventory player, RatStorageBlockEntity storage, boolean controller, boolean output) {
		super(RatsMenuRegistry.RAT_STORAGE_CONTAINER.get(), id); this.storage = storage; this.controller = controller; this.output = output; this.viewer = player.player; this.compact = this.viewer.level().isClientSide() && ClientLayout.compact(); this.inventory = storage == null ? new SimpleContainer(54) : storage;
		this.receiving = storage == null ? new SimpleContainer(18) : storage.session(player.player.getUUID());
		if (controller) {
			this.addSlot(new ResultSlot(player.player, this.crafting, this.result, 0, this.compact ? 268 : 336, 82) { @Override public boolean isActive() { return RatStorageMenu.this.page == 0; } });
			for (int i = 0; i < 9; i++) { this.crafting.setItem(i, this.receiving.getItem(i).copy()); this.addSlot(new Slot(this.crafting, i, (this.compact ? 174 : 242) + i % 3 * 18, 64 + i / 3 * 18) { @Override public boolean isActive() { return RatStorageMenu.this.page == 0; } }); }
			for (int i = 0; i < 9; i++) this.addSlot(new Slot(this.receiving, i + 9, this.compact ? 246 + i % 3 * 18 : 222 + i * 18, this.compact ? 118 + i / 3 * 18 : 129) { @Override public boolean isActive() { return RatStorageMenu.this.page == 0 && RatStorageMenu.this.hasLegacyReceipt(); } @Override public boolean mayPlace(ItemStack item) { return false; } });
		} else for (int i = 0; i < 54; i++) this.addSlot(new Slot(this.inventory, i, 8 + i % 9 * 18, 32 + i / 9 * 18) { @Override public boolean mayPlace(ItemStack item) { return !RatStorageMenu.this.output; } });
		int x = controller ? this.compact ? 74 : 112 : 8, y = controller ? 164 : 154;
		for (int i = 0; i < 27; i++) this.addSlot(new Slot(player, i + 9, x + i % 9 * 18, y + i / 9 * 18));
		for (int i = 0; i < 9; i++) this.addSlot(new Slot(player, i, x + i * 18, y + 58));
		this.initializing = false; this.slotsChanged(this.crafting);
	}
	public RatStorageMenu(int id, Inventory inventory, FriendlyByteBuf buf) { this(id, inventory, null, buf.readBoolean(), buf.readBoolean()); }
	public boolean hasLegacyReceipt() { for(int i=9;i<18;i++)if(!this.receiving.getItem(i).isEmpty())return true;return false; }
	@Override public boolean stillValid(Player player) { return this.storage == null || this.storage.stillValid(player); }
	@Override public ItemStack quickMoveStack(Player player, int index) {
		if (player != this.viewer || !this.stillValid(player) || index < 0 || index >= this.slots.size()) return ItemStack.EMPTY;
		Slot slot = this.slots.get(index); if (!slot.hasItem() || !slot.isActive()) return ItemStack.EMPTY; ItemStack held = slot.getItem(), copy = held.copy(); int start = this.controller ? 19 : 54;
		if (index < start) { if (!this.moveItemStackTo(held, start, start + 36, true)) return ItemStack.EMPTY; if (this.controller && index == 0) slot.onQuickCraft(held, copy); }
		else if (this.controller) { if (this.page == 0) { if (!this.moveItemStackTo(held, 1, 10, false)) return ItemStack.EMPTY; } else return ItemStack.EMPTY; }
		else if (this.output || !this.moveItemStackTo(held, 0, 54, false)) return ItemStack.EMPTY;
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
		if (!this.controller || this.storage == null || player != this.viewer || !this.stillValid(player)) return;
		if (action == 2 && index >= 0 && index <= 2) { this.page = index; return; }
		if (player.tickCount < this.actionAt) return;
		this.actionAt = player.tickCount + 3;
		if (action == 0 && this.catalogById.containsKey(index)) { var outputs = this.storage.endpoints.stream().filter(p -> StorageStock.output(this.storage, p)).toList(); this.message = outputs.isEmpty() ? "no_output" : this.storage.request(player.getUUID(), this.catalogById.get(index).item(), amount, outputs.get(Math.floorMod(this.selectedOutput, outputs.size())), -1) ? "queued" : "rejected"; this.messageAt = player.tickCount + 100; }
		else if (action == 1 && this.workerIds.containsValue(index)) { UUID id = this.workerIds.entrySet().stream().filter(e -> e.getValue() == index).map(Map.Entry::getKey).findFirst().orElseThrow(); var rat = player.serverLevel().getEntity(id); if (this.storage.workers.contains(id) && (rat instanceof TamedRat tame && tame.isOwnedBy(player) || player.getUUID().equals(this.storage.owner))) { this.storage.recalling.add(id); this.storage.setChanged(); } }
		else if (action == 2 && index >= 0 && index <= 2) this.page = index;
		// Action 3 (recipe refill) deliberately retired. Persisted old deliveries
		// are still drained safely into their private receipt slots.
		else if (action == 4 && player.getUUID().equals(this.storage.owner)) { this.storage.organize = true; this.storage.setChanged(); }
		else if (action == 5) this.selectedOutput++;
		else if (action == 6 && this.endpointIds.containsValue(index) && player.getUUID().equals(this.storage.owner)) { BlockPos endpoint = this.endpointIds.entrySet().stream().filter(e->e.getValue()==index).map(Map.Entry::getKey).findFirst().orElseThrow(); if (this.storage.endpoints.contains(endpoint) && player.level().hasChunkAt(endpoint) && !(player.level().getBlockEntity(endpoint) instanceof RatStorageBlockEntity)) { this.storage.categories.put(endpoint, "mixed"); this.storage.setChanged(); } }
		this.refreshAt = 0; this.broadcastChanges();
	}
	@Override public void broadcastChanges() {
		if (!this.initializing && this.controller && this.storage != null && this.viewer instanceof ServerPlayer player && this.stillValid(player) && player.containerMenu == this && player.tickCount >= this.refreshAt) {
			this.refreshAt = player.tickCount + 10; this.entries = this.storage.catalog(false); this.snapshot = this.makeSnapshot(player); if(!this.snapshot.equals(this.sentSnapshot)) { this.sentSnapshot=this.snapshot; var stock = this.snapshot.getList("Stock", Tag.TAG_COMPOUND); int parts = Math.max(1, (stock.size() + 31) / 32); long version = ++this.revision;
			for (int part = 0; part < parts; part++) { var data = part == 0 ? this.snapshot.copy() : new CompoundTag(); ListTag chunk = new ListTag(); for (int i = part * 32; i < Math.min(stock.size(), (part + 1) * 32); i++) chunk.add(stock.get(i).copy()); data.put("Stock", chunk); RatsNetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new StorageSnapshotPacket(this.containerId, version, part, parts, data)); }
			}
		}
		super.broadcastChanges();
	}
	private CompoundTag makeSnapshot(ServerPlayer player) {
		var root = new CompoundTag(); ListTag stock = new ListTag();
		this.catalogById.clear();
		for (var e : this.entries) { int id = this.catalogIds.computeIfAbsent(StorageStock.Key.of(e.item()), key -> this.catalogIds.size()); this.catalogById.put(id, e); var tag = new CompoundTag(); tag.putInt("Index", id); tag.put("Item",displayItem(e.item())); tag.putLong("Available", e.available()); tag.putLong("Reserved", e.reserved()); tag.putLong("Transit", e.transit()); tag.putLong("Input", e.input()); stock.add(tag); } root.put("Stock", stock);
		ListTag workers = new ListTag(); for (UUID id : this.storage.workers) { var tag = new CompoundTag(); var rat = player.serverLevel().getEntity(id); tag.putInt("Index", this.workerIds.computeIfAbsent(id, key -> this.workerIds.size())); tag.putString("Name", rat == null ? "—" : rat.getName().getString()); tag.putInt("Entity", rat == null ? -1 : rat.getId()); tag.putString("Status", rat == null ? "unloaded" : rat.getPersistentData().getString("RatStorageStatus")); workers.add(tag); } root.put("Workers", workers);
		ListTag nodes = new ListTag(); var graph = this.storage.graph(); for (BlockPos pos : this.storage.endpoints) { var t = new CompoundTag(); t.putInt("Index",this.endpointIds.computeIfAbsent(pos,key->this.endpointIds.size())); t.putLong("Pos", pos.asLong()); t.putString("Category", this.storage.categories.getOrDefault(pos, "unclassified")); t.putBoolean("Loaded", player.level().hasChunkAt(pos)); t.putBoolean("Route", StorageRoutes.linkedInventory(this.storage,pos) && (player.level().getBlockEntity(pos) instanceof RatStorageBlockEntity || StorageRoutes.port(player.level(), pos, graph) != null)); nodes.add(t); } root.put("Endpoints", nodes); root.putString("Message", player.tickCount < this.messageAt ? this.message : "");
		ListTag queue = new ListTag(); for (var r : this.storage.requests) { var t = new CompoundTag(); t.putString("Name", r.item().getHoverName().getString()); t.putInt("Amount", r.amount()); t.putInt("Delivered", r.delivered()); t.putString("Status", this.storage.tasks.values().stream().anyMatch(task -> r.id().equals(task.request) && task.collected) ? "delivering" : this.storage.tasks.values().stream().anyMatch(task -> r.id().equals(task.request)) ? "collecting" : "idle"); queue.add(t); } root.put("Queue", queue);
		root.putInt("Requests", this.storage.requests.size()); root.putInt("Tubes", (int)graph.keySet().stream().filter(p -> StorageRoutes.tube(player.level(),this.storage.getBlockPos(),p)).count()); root.putInt("Output", this.selectedOutput); var outputs = this.storage.endpoints.stream().filter(p -> StorageStock.output(this.storage, p)).toList(); if (!outputs.isEmpty()) root.putLong("OutputPos", outputs.get(Math.floorMod(this.selectedOutput, outputs.size())).asLong()); return root;
	}
	private static class ClientLayout { static boolean compact() { return net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth() < 394; } }
	private static CompoundTag displayItem(ItemStack item) {
		var data=item.save(new CompoundTag()); if(data.toString().length()<=8192) return data;
		var small=new CompoundTag(); small.putString("id",net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem()).toString()); small.putByte("Count",(byte)1);
		var tag=new CompoundTag(); var display=new CompoundTag(); String name=item.getHoverName().getString(); display.putString("Name",net.minecraft.network.chat.Component.Serializer.toJson(net.minecraft.network.chat.Component.literal(name.substring(0,Math.min(64,name.length()))))); tag.put("display",display); small.put("tag",tag); return small;
	}
}
