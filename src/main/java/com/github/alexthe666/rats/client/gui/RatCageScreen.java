package com.github.alexthe666.rats.client.gui;

import com.github.alexthe666.rats.registry.RatsItemRegistry;
import com.github.alexthe666.rats.server.inventory.RatCageMenu;
import com.github.alexthe666.rats.server.message.CageActionPacket;
import com.github.alexthe666.rats.server.message.CageRosterPacket;
import com.github.alexthe666.rats.server.message.RatsNetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Master/detail: list is selection-only; mutations belong to the detail pane. */
public class RatCageScreen extends AbstractContainerScreen<RatCageMenu> {
	private final Button[] rows = new Button[6];
	private final RatPortrait portrait = new RatPortrait();
	private Button previous, next, release, home, pair, breed, rename, inventoryTab, autoBreed;
	private EditBox name;
	private int page, partner = -1, lastSelected = -1;
	public RatCageScreen(RatCageMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); this.imageWidth = 280; this.imageHeight = 238; }
	@Override protected void init() {
		super.init();
		for (int i = 0; i < 6; i++) {
			int row = i;
			this.rows[i] = this.addRenderableWidget(Button.builder(Component.empty(), button -> { var rat = this.at(row); if (rat != null) this.send(0, rat.id()); }).bounds(this.leftPos + 8, this.topPos + 42 + i * 26, 148, 24).build());
		}
		this.previous = this.button("<", 8, 204, 30, () -> this.page = Math.max(0, this.page - 1));
		this.next = this.button(">", 126, 204, 30, () -> this.page++);
		this.inventoryTab = this.button("container.inventory", 42, 204, 80, () -> this.menu.showPlayerInventory = !this.menu.showPlayerInventory);
		this.autoBreed = this.button("cage.rats.auto_off", 168, 16, 80, () -> this.send(6, -1));
		this.autoBreed.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("cage.rats.auto_help")));
		this.name = this.addRenderableWidget(new EditBox(this.font, this.leftPos + 168, this.topPos + 70, 104, 18, Component.translatable("cage.rats.name")));
		this.name.setMaxLength(32);
		this.rename = this.button("cage.rats.rename", 168, 90, 104, () -> this.sendSelected(5));
		this.release = this.button("cage.rats.release", 168, 112, 50, () -> this.sendSelected(1));
		this.home = this.button("cage.rats.home", 220, 112, 52, () -> { var rat = this.selected(); if (rat != null) this.send(rat.home() ? 3 : 2, rat.id()); });
		this.pair = this.button("cage.rats.pair", 168, 214, 50, this::cyclePartner);
		this.breed = this.button("cage.rats.breed", 220, 214, 52, () -> this.sendSelected(4));
		this.containerTick();
	}
	private Button button(String key, int x, int y, int width, Runnable action) {
		return this.addRenderableWidget(Button.builder(Component.translatable(key), button -> action.run()).bounds(this.leftPos + x, this.topPos + y, width, 20).build());
	}
	private CageRosterPacket.Resident at(int row) { return this.menu.snapshot != null && this.page * 6 + row < this.menu.snapshot.residents().size() ? this.menu.snapshot.residents().get(this.page * 6 + row) : null; }
	private CageRosterPacket.Resident selected() { return this.menu.snapshot == null ? null : this.menu.snapshot.residents().stream().filter(rat -> rat.id() == this.menu.snapshot.selected()).findFirst().orElse(null); }
	private List<CageRosterPacket.Resident> mates() {
		var selected = this.selected();
		return selected == null ? List.of() : this.menu.snapshot.residents().stream().filter(rat -> rat.id() != selected.id() && rat.owned() && !rat.baby() && rat.male() != selected.male() && rat.cooldown() <= 0).toList();
	}
	private void cyclePartner() {
		var mates = this.mates(); if (mates.isEmpty()) { this.partner = -1; return; }
		int index = -1; for (int i = 0; i < mates.size(); i++) if (mates.get(i).id() == this.partner) index = i;
		this.partner = mates.get((index + 1) % mates.size()).id();
	}
	private void sendSelected(int action) { var rat = this.selected(); if (rat != null) this.send(action, rat.id()); }
	private void send(int action, int id) { RatsNetworkHandler.CHANNEL.sendToServer(new CageActionPacket(this.menu.containerId, action, id, this.partner, this.name == null ? "" : this.name.getValue())); }
	@Override protected void containerTick() {
		super.containerTick(); var snapshot = this.menu.snapshot;
		this.autoBreed.visible = snapshot != null && snapshot.lanternCooldown() >= 0;
		this.autoBreed.active = snapshot != null && snapshot.complete() && snapshot.canAutoBreed();
		this.autoBreed.setMessage(Component.translatable(snapshot != null && snapshot.autoBreed() ? "cage.rats.auto_on" : "cage.rats.auto_off"));
		if (snapshot != null) this.page = Math.min(this.page, Math.max(0, (snapshot.residents().size() - 1) / 6));
		for (int i = 0; i < 6; i++) this.rows[i].visible = !this.menu.showPlayerInventory && this.at(i) != null;
		this.previous.visible = !this.menu.showPlayerInventory; this.next.visible = !this.menu.showPlayerInventory;
		this.inventoryTab.setMessage(Component.translatable(this.menu.showPlayerInventory ? "cage.rats.roster" : "container.inventory"));
		this.previous.active = this.page > 0; this.next.active = snapshot != null && (this.page + 1) * 6 < snapshot.residents().size();
		var selected = this.selected(); boolean details = selected != null, owned = details && selected.owned();
		this.release.visible = details; this.home.visible = details; this.rename.visible = details; this.name.visible = details;
		this.release.active = owned; this.home.active = owned; this.rename.active = owned; this.name.setEditable(owned);
		int id = details ? selected.id() : -1;
		if (id != this.lastSelected) { this.lastSelected = id; this.partner = -1; this.name.setValue(details ? selected.name().getString().substring(0, Math.min(32, selected.name().getString().length())) : ""); }
		if (details) this.home.setMessage(Component.translatable(selected.home() ? "cage.rats.clear_home" : "cage.rats.home"));
		boolean lantern = details && snapshot.lanternCooldown() >= 0;
		this.pair.visible = lantern; this.breed.visible = lantern;
		var mates = this.mates(); var mate = mates.stream().filter(rat -> rat.id() == this.partner).findFirst().orElse(null);
		if (mate == null) this.partner = -1;
		Component partnerLabel = Component.translatable("cage.rats.partner", mate == null ? "—" : mate.name().getString());
		this.pair.setMessage(Component.literal(this.font.plainSubstrByWidth(partnerLabel.getString(), 44)));
		this.pair.setTooltip(net.minecraft.client.gui.components.Tooltip.create(partnerLabel));
		this.pair.active = owned && !selected.baby() && !mates.isEmpty();
		this.breed.active = lantern && owned && mate != null && !selected.baby() && selected.cooldown() <= 0 && snapshot.complete() && snapshot.lanternCooldown() == 0 && snapshot.residents().size() < snapshot.capacity();
	}
	@Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xFF373737);
		graphics.fill(this.leftPos + 1, this.topPos + 1, this.leftPos + this.imageWidth - 1, this.topPos + this.imageHeight - 1, 0xFFFFFFFF);
		graphics.fill(this.leftPos + 3, this.topPos + 3, this.leftPos + this.imageWidth - 3, this.topPos + this.imageHeight - 3, 0xFFC6C6C6);
		if (this.selected() != null) {
			graphics.fill(this.leftPos + 162, this.topPos + 37, this.leftPos + 274, this.topPos + 210, 0xFFA9A9A9);
		}
		for (int i = 0; i < this.menu.slots.size(); i++) {
			var slot = this.menu.slots.get(i); if (i < 13 && this.selected() == null) continue;
			if (i >= 13 && !this.menu.showPlayerInventory) continue;
			int x = this.leftPos + slot.x - 1, y = this.topPos + slot.y - 1;
			graphics.fill(x, y, x + 18, y + 18, 0xFF373737); graphics.fill(x + 1, y + 1, x + 17, y + 17, slot.isActive() ? 0xFF8B8B8B : 0xFF555555);
			if (!slot.isActive()) { graphics.fill(x + 3, y + 8, x + 15, y + 10, 0xFF737373); }
		}
		if (this.menu.snapshot != null && this.menu.snapshot.lanternCooldown() >= 0) graphics.renderItem(new ItemStack(RatsItemRegistry.RAT_BREEDING_LANTERN.get()), this.leftPos + 252, this.topPos + 12);
	}
	@Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(this.font, this.title, 8, 8, 0x404040, false); var snapshot = this.menu.snapshot; if (snapshot == null) return;
		if (this.menu.showPlayerInventory) graphics.drawString(this.font, this.playerInventoryTitle, 22, 44, 0x404040, false);
		graphics.drawString(this.font, Component.translatable(snapshot.complete() ? "cage.rats.capacity" : "cage.rats.incomplete", snapshot.residents().size(), snapshot.capacity()), 8, 24, snapshot.complete() ? 0x404040 : 0x9A3020, false);
		if (!this.menu.showPlayerInventory) graphics.drawString(this.font, (this.page + 1) + "/" + Math.max(1, (snapshot.residents().size() + 5) / 6), 68, 226, 0x404040, false);
		var selected = this.selected();
		if (selected == null) { graphics.drawWordWrap(this.font, Component.translatable("cage.rats.select"), 168, 42, 104, 0x404040); return; }
		graphics.drawString(this.font, this.font.plainSubstrByWidth(selected.name().getString(), 104), 168, 42, 0x404040, false);
		graphics.drawString(this.font, Component.translatable(selected.baby() ? "cage.rats.baby" : selected.male() ? "cage.rats.male" : "cage.rats.female"), 168, 56, 0x404040, false);
		graphics.drawString(this.font, Component.translatable("cage.rats.cargo"), 168, 134, 0x404040, false);
		graphics.drawString(this.font, Component.translatable("cage.rats.upgrades"), 168, 184, 0x404040, false);
	}
	@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(graphics); super.render(graphics, mouseX, mouseY, partialTick);
		for (int i = 0; i < 6; i++) {
			var rat = this.at(i); if (rat == null || this.menu.showPlayerInventory) continue; int y = this.topPos + 42 + i * 26;
			graphics.enableScissor(this.leftPos + 10, y + 1, this.leftPos + 40, y + 23);
			this.portrait.drawResident(graphics, this.leftPos + 24, y + 21, rat.baby() ? 42 : 28, rat);
			graphics.disableScissor();
			graphics.drawString(this.font, this.font.plainSubstrByWidth(rat.name().getString(), 106), this.leftPos + 44, y + 3, 0xFFFFFF, true);
			graphics.drawString(this.font, Component.translatable(rat.baby() ? "cage.rats.baby" : rat.male() ? "cage.rats.male" : "cage.rats.female"), this.leftPos + 44, y + 13, 0xFFFFFF, true);
		}
		this.renderTooltip(graphics, mouseX, mouseY);
	}
	@Override public boolean keyPressed(int key, int scan, int modifiers) { if (this.name.visible && this.name.isFocused() && key != 256) return this.name.keyPressed(key, scan, modifiers); return super.keyPressed(key, scan, modifiers); }
}
