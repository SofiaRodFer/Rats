package com.github.alexthe666.rats.client.gui;

import com.github.alexthe666.rats.server.inventory.RatStorageMenu;
import com.github.alexthe666.rats.server.message.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public class RatStorageScreen extends AbstractContainerScreen<RatStorageMenu> {
	private EditBox search, amount;
	private int offset, selected = -1;
	private boolean queueView;
	private int columns() { return this.menu.compact ? 6 : 8; }
	private int cellWidth() { return this.menu.compact ? 23 : 25; }
	private int side() { return this.menu.compact ? 174 : 222; }
	private int sideWidth() { return this.menu.compact ? 128 : 164; }
	private final List<AbstractWidget> mainWidgets = new ArrayList<>();
	public RatStorageScreen(RatStorageMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); this.imageWidth = menu.controller ? menu.compact ? 310 : 394 : 176; this.imageHeight = menu.controller ? menu.compact ? 300 : 276 : 236; }
	private void action(int action, int index, int count) { RatsNetworkHandler.CHANNEL.sendToServer(new StorageActionPacket(this.menu.containerId, action, index, count)); }
	private Button button(String key, int x, int y, int width, Runnable callback) { return this.addRenderableWidget(Button.builder(Component.translatable("storage.rats." + key), b -> callback.run()).bounds(this.leftPos + x, this.topPos + y, width, 18).build()); }
	@Override protected void init() {
		super.init(); this.mainWidgets.clear(); if (!this.menu.controller) return;
		this.button("storage_crafting", 8, 17, this.menu.compact ? 148 : 180, () -> this.tab(0)); this.button("rats", this.menu.compact ? 160 : 192, 17, this.menu.compact ? 60 : 88, () -> this.tab(1)); this.button("system", this.menu.compact ? 224 : 284, 17, this.menu.compact ? 78 : 102, () -> this.tab(2));
		this.search = new EditBox(this.font, this.leftPos + 8, this.topPos + 39, this.menu.compact ? 140 : 198, 16, Component.translatable("storage.rats.search")); this.search.setHint(Component.translatable("storage.rats.search")); this.search.setMaxLength(80); this.search.setResponder(s -> this.offset = 0); this.addRenderableWidget(this.search); this.mainWidgets.add(this.search);
		this.amount = new EditBox(this.font, this.leftPos + 8, this.topPos + 169, 55, 16, Component.translatable("storage.rats.amount")); this.amount.setMaxLength(6); this.amount.setFilter(s -> s.matches("[0-9]*")); this.amount.setValue("64"); this.addRenderableWidget(this.amount); this.mainWidgets.add(this.amount);
		this.mainWidgets.add(this.button("request", 68, 168, this.menu.compact ? 80 : 138, () -> { int count; try { count = Integer.parseInt(this.amount.getValue()); } catch (NumberFormatException e) { return; } this.action(0, this.selected, count); }));
		this.mainWidgets.add(this.button("fetch", this.side(), 39, this.sideWidth(), () -> this.action(3, 0, 0)));
		this.mainWidgets.add(this.button("output", this.side(), this.menu.compact ? 181 : 162, this.sideWidth(), () -> this.action(5, 0, 0)));
		this.tab(this.menu.page);
	}
	private void tab(int page) { this.menu.page = page; this.offset = 0; this.mainWidgets.forEach(w -> w.visible = page == 0); this.action(2, page, 0); }
	private List<CompoundTag> stock() {
		String query = this.search == null ? "" : this.search.getValue().toLowerCase(Locale.ROOT); var entries = new ArrayList<CompoundTag>();
		for (Tag value : this.menu.snapshot.getList("Stock", Tag.TAG_COMPOUND)) { var t = (CompoundTag)value; var item = ItemStack.of(t.getCompound("Item")); String name = item.getHoverName().getString().toLowerCase(Locale.ROOT); String id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem()).toString(); if (query.startsWith("@") ? id.startsWith(query.substring(1) + ":") : name.contains(query)) entries.add(t); }
		entries.sort(Comparator.comparing((CompoundTag t) -> ItemStack.of(t.getCompound("Item")).getHoverName().getString(), String.CASE_INSENSITIVE_ORDER).thenComparingInt(t -> t.getInt("Index"))); return entries;
	}
	@Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
		g.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xFFC6C6C6);
		for (var slot : this.menu.slots) if (slot.isActive()) { int x = this.leftPos + slot.x, y = this.topPos + slot.y; g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF555555); g.fill(x, y, x + 16, y + 16, 0xFF999999); }
		if (!this.menu.controller) return;
		if (this.menu.page == 0) {
			var entries = this.stock(); this.offset = Math.min(this.offset, Math.max(0, (entries.size() - 1) / this.columns() - 4));
			for (int i = 0; i < this.columns() * 5; i++) {
				int x = this.leftPos + 8 + i % this.columns() * this.cellWidth(), y = this.topPos + 60 + i / this.columns() * 21, index = this.offset * this.columns() + i;
				g.fill(x, y, x + this.cellWidth() - 2, y + 20, 0xFF777777);
				if (index >= entries.size()) continue; var tag = entries.get(index); var item = ItemStack.of(tag.getCompound("Item")); if (tag.getInt("Index") == this.selected) g.fill(x, y, x + 23, y + 20, 0xFFBEAA56);
				g.renderItem(item, x + 3, y); String count = compact(tag.getLong("Available")); g.pose().pushPose(); g.pose().translate(x + 1, y + 14, 200); g.pose().scale(0.65F, 0.65F, 1); g.drawString(this.font, count, 0, 0, 0xFFFFFFFF, true); g.pose().popPose();
			}
		} else if (this.menu.page == 1) {
			var workers = this.menu.snapshot.getList("Workers", Tag.TAG_COMPOUND);
			for (int i = 0; i < Math.min(5, workers.size() - this.offset); i++) { var t = workers.getCompound(this.offset + i); int y = this.topPos + 45 + i * 27; g.fill(this.leftPos + 8, y, this.leftPos + this.imageWidth - 8, y + 25, 0xFF999999); var entity = this.minecraft.level.getEntity(t.getInt("Entity")); if (entity instanceof LivingEntity living) InventoryScreen.renderEntityInInventoryFollowsMouse(g, this.leftPos + 24, y + 23, 24, 0, 0, living); g.drawString(this.font, this.font.plainSubstrByWidth(t.getString("Name"), this.imageWidth - 140), this.leftPos + 45, y + 3, 0xFF333333, false); g.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("storage.rats.state." + t.getString("Status")).getString(), this.imageWidth - 140), this.leftPos + 45, y + 14, 0xFF444444, false); g.drawString(this.font, Component.translatable("storage.rats.remove"), this.leftPos + this.imageWidth - 85, y + 9, 0xFF333333, false); }
		} else {
			g.drawString(this.font, Component.translatable("storage.rats.nodes_queue"), this.leftPos + 190, this.topPos + 43, 0xFF333333, false);
			var nodes = this.menu.snapshot.getList(this.queueView ? "Queue" : "Endpoints", Tag.TAG_COMPOUND);
			for (int i = 0; i < Math.min(9, nodes.size() - this.offset); i++) { var t = nodes.getCompound(this.offset + i); var p = BlockPos.of(t.getLong("Pos")); int y = this.topPos + 59 + i * 13; String line = this.queueView ? t.getString("Name") + " " + t.getInt("Delivered") + "/" + t.getInt("Amount") : p.getX() + ", " + p.getY() + ", " + p.getZ() + " " + Component.translatable("storage.rats.category." + t.getString("Category")).getString(); g.drawString(this.font, this.font.plainSubstrByWidth(line, this.imageWidth - 198), this.leftPos + 190, y, this.queueView || t.getBoolean("Loaded") && t.getBoolean("Route") ? 0xFF334433 : 0xFFAA3333, false); }
			g.drawString(this.font, Component.translatable("storage.rats.organize"), this.leftPos + 190, this.topPos + 177, 0xFF333333, false);
		}
	}
	private static String compact(long count) { return count >= 1000000 ? String.format(Locale.ROOT, "%.1fM", count / 1000000D) : count >= 1000 ? String.format(Locale.ROOT, "%.1fk", count / 1000D) : Long.toString(count); }
	@Override protected void renderLabels(GuiGraphics g, int mx, int my) {
		g.drawString(this.font, this.title, 8, 4, 0x404040, false);
		if (!this.menu.controller) return;
		if (this.menu.page == 0) { g.drawString(this.font, Component.translatable("storage.rats.receiving"), this.side(), this.menu.compact ? 114 : 125, 0x404040, false); if (this.menu.snapshot.contains("OutputPos")) { var pos = BlockPos.of(this.menu.snapshot.getLong("OutputPos")); g.drawString(this.font, pos.getX() + "," + pos.getY() + "," + pos.getZ(), this.side(), this.menu.compact ? 203 : 184, 0x404040, false); } }
		else if (this.menu.page == 2) g.drawString(this.font, Component.translatable("storage.rats.recovery"), 8, 43, 0x404040, false);
		g.drawString(this.font, Component.translatable("storage.rats.queue", this.menu.snapshot.getInt("Requests")), 8, 184, 0x404040, false);
	}
	@Override public boolean mouseClicked(double mx, double my, int button) {
		int x = (int)mx - this.leftPos, y = (int)my - this.topPos;
		if (this.menu.controller && button == 0) {
			if (this.menu.page == 0 && x >= 8 && x < 8 + this.columns() * this.cellWidth() && y >= 60 && y < 165) { int index = this.offset * this.columns() + (y - 60) / 21 * this.columns() + (x - 8) / this.cellWidth(); var entries = this.stock(); if (index < entries.size()) this.selected = entries.get(index).getInt("Index"); return true; }
			if (this.menu.page == 1 && x >= this.imageWidth - 85 && y >= 45 && y < 180) { var workers = this.menu.snapshot.getList("Workers", Tag.TAG_COMPOUND); int index = this.offset + (y - 45) / 27; if (index < workers.size()) this.action(1, workers.getCompound(index).getInt("Index"), 0); return true; }
			if (this.menu.page == 2 && x >= 190 && y >= 39 && y < 56) { this.queueView = !this.queueView; this.offset = 0; return true; }
			if (this.menu.page == 2 && !this.queueView && x >= 190 && y >= 59 && y < 176) { this.action(6, this.offset + (y - 59) / 13, 0); return true; }
			if (this.menu.page == 2 && x >= 190 && y >= 176 && y < 189) { this.action(4, 0, 0); return true; }
		}
		return super.mouseClicked(mx, my, button);
	}
	@Override public boolean mouseScrolled(double mx, double my, double delta) { if (this.menu.controller) { int max = this.menu.page == 0 ? Math.max(0, (this.stock().size() - 1) / this.columns() - 4) : this.menu.snapshot.getList(this.menu.page == 1 ? "Workers" : this.queueView ? "Queue" : "Endpoints", Tag.TAG_COMPOUND).size() - (this.menu.page == 1 ? 5 : 9); this.offset = Math.max(0, Math.min(max, this.offset - (int)Math.signum(delta))); return true; } return super.mouseScrolled(mx, my, delta); }
	@Override public boolean keyPressed(int key, int scan, int modifiers) { if (this.search != null && this.search.isFocused() || this.amount != null && this.amount.isFocused()) { if (key == 256) this.onClose(); else (this.search.isFocused() ? this.search : this.amount).keyPressed(key, scan, modifiers); return true; } return super.keyPressed(key, scan, modifiers); }
	@Override public void render(GuiGraphics g, int mx, int my, float partial) {
		this.renderBackground(g); super.render(g, mx, my, partial); this.renderTooltip(g, mx, my);
		int x = mx - this.leftPos, y = my - this.topPos;
		if (this.menu.controller && this.menu.page == 0 && x >= 8 && x < 8 + this.columns() * this.cellWidth() && y >= 60 && y < 165) { int index = this.offset * this.columns() + (y - 60) / 21 * this.columns() + (x - 8) / this.cellWidth(); var entries = this.stock(); if (index < entries.size()) { var t = entries.get(index); g.renderComponentTooltip(this.font, List.of(ItemStack.of(t.getCompound("Item")).getHoverName(), Component.translatable("storage.rats.counts", t.getLong("Available"), t.getLong("Reserved"), t.getLong("Transit"), t.getLong("Input"))), mx, my); } }
	}
}
