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

/** Native beveled panels and original mod item art; no texture scaling artifacts. */
public class RatCageScreen extends AbstractContainerScreen<RatCageMenu> {
	private final Button[] rows = new Button[6], partners = new Button[6];
	private Button previous, next, release, home, breed, rename;
	private EditBox name;
	private int page, partner = -1, lastSelected = -1;

	public RatCageScreen(RatCageMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); this.imageWidth = 280; this.imageHeight = 238; }
	@Override protected void init() {
		super.init();
		for (int i = 0; i < 6; i++) {
			int row = i;
			this.rows[i] = this.addRenderableWidget(Button.builder(Component.empty(), button -> { CageRosterPacket.Resident rat = this.at(row); if (rat != null) this.send(0, rat.id()); }).bounds(this.leftPos + 8, this.topPos + 42 + i * 20, 110, 18).build());
			this.partners[i] = this.addRenderableWidget(Button.builder(Component.translatable("cage.rats.pair"), button -> { CageRosterPacket.Resident rat = this.at(row); if (rat != null) this.partner = rat.id(); }).bounds(this.leftPos + 120, this.topPos + 42 + i * 20, 36, 18).build());
		}
		this.previous = this.addRenderableWidget(Button.builder(Component.literal("<"), button -> this.page = Math.max(0, this.page - 1)).bounds(this.leftPos + 8, this.topPos + 166, 30, 20).build());
		this.next = this.addRenderableWidget(Button.builder(Component.literal(">"), button -> this.page++).bounds(this.leftPos + 126, this.topPos + 166, 30, 20).build());
		this.release = this.addRenderableWidget(Button.builder(Component.translatable("cage.rats.release"), button -> this.send(1, this.menu.snapshot.selected())).bounds(this.leftPos + 168, this.topPos + 70, 50, 20).build());
		this.home = this.addRenderableWidget(Button.builder(Component.translatable("cage.rats.home"), button -> { CageRosterPacket.Resident rat = this.selected(); if (rat != null) this.send(rat.home() ? 3 : 2, rat.id()); }).bounds(this.leftPos + 220, this.topPos + 70, 52, 20).build());
		this.breed = this.addRenderableWidget(Button.builder(Component.translatable("cage.rats.breed"), button -> this.send(4, this.menu.snapshot.selected())).bounds(this.leftPos + 8, this.topPos + 194, 148, 20).build());
		this.name = this.addRenderableWidget(new EditBox(this.font, this.leftPos + 168, this.topPos + 180, 104, 18, Component.translatable("cage.rats.name")));
		this.name.setMaxLength(32);
		this.rename = this.addRenderableWidget(Button.builder(Component.translatable("cage.rats.rename"), button -> this.send(5, this.menu.snapshot.selected())).bounds(this.leftPos + 168, this.topPos + 204, 104, 20).build());
		this.containerTick();
	}
	private CageRosterPacket.Resident at(int row) { return this.menu.snapshot != null && this.page * 6 + row < this.menu.snapshot.residents().size() ? this.menu.snapshot.residents().get(this.page * 6 + row) : null; }
	private CageRosterPacket.Resident selected() { return this.menu.snapshot == null ? null : this.menu.snapshot.residents().stream().filter(rat -> rat.id() == this.menu.snapshot.selected()).findFirst().orElse(null); }
	private void send(int action, int id) { RatsNetworkHandler.CHANNEL.sendToServer(new CageActionPacket(this.menu.containerId, action, id, this.partner, this.name == null ? "" : this.name.getValue())); }
	@Override protected void containerTick() {
		super.containerTick();
		CageRosterPacket snapshot = this.menu.snapshot;
		if (snapshot != null) this.page = Math.min(this.page, Math.max(0, (snapshot.residents().size() - 1) / 6));
		for (int i = 0; i < 6; i++) {
			CageRosterPacket.Resident rat = this.at(i);
			this.rows[i].visible = rat != null; this.partners[i].visible = rat != null;
			if (rat != null) {
				this.rows[i].setMessage(Component.literal((rat.male() ? "M " : "F ") + (rat.baby() ? "* " : "") + this.font.plainSubstrByWidth(rat.name().getString(), 78)));
				this.partners[i].active = rat.owned() && !rat.baby() && rat.cooldown() <= 0;
			}
		}
		this.previous.active = this.page > 0;
		this.next.active = snapshot != null && (this.page + 1) * 6 < snapshot.residents().size();
		CageRosterPacket.Resident selected = this.selected();
		this.release.active = selected != null && selected.owned();
		this.home.active = this.release.active; this.rename.active = this.release.active; this.name.setEditable(this.release.active);
		if (selected != null) {
			this.home.setMessage(Component.translatable(selected.home() ? "cage.rats.clear_home" : "cage.rats.home"));
			if (selected.id() != this.lastSelected) { this.lastSelected = selected.id(); this.name.setValue(this.font.plainSubstrByWidth(selected.name().getString(), 200).substring(0, Math.min(32, this.font.plainSubstrByWidth(selected.name().getString(), 200).length()))); }
		}
		CageRosterPacket.Resident mate = snapshot == null ? null : snapshot.residents().stream().filter(rat -> rat.id() == this.partner).findFirst().orElse(null);
		this.breed.active = selected != null && mate != null && selected.id() != mate.id() && selected.owned() && mate.owned() && selected.male() != mate.male() && !selected.baby() && !mate.baby() && selected.cooldown() <= 0 && mate.cooldown() <= 0 && snapshot.complete() && snapshot.lanternCooldown() == 0 && snapshot.residents().size() < snapshot.capacity();
	}
	@Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xFF373737);
		graphics.fill(this.leftPos + 1, this.topPos + 1, this.leftPos + this.imageWidth - 1, this.topPos + this.imageHeight - 1, 0xFFFFFFFF);
		graphics.fill(this.leftPos + 3, this.topPos + 3, this.leftPos + this.imageWidth - 3, this.topPos + this.imageHeight - 3, 0xFFC6C6C6);
		graphics.fill(this.leftPos + 162, this.topPos + 37, this.leftPos + 274, this.topPos + 170, 0xFFA9A9A9);
		for (int i = 0; i < 15; i++) {
			int x = this.leftPos + 167 + i % 5 * 18, y = this.topPos + 108 + i / 5 * 18;
			graphics.fill(x, y, x + 18, y + 18, 0xFF373737); graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
		}
		graphics.renderItem(new ItemStack(RatsItemRegistry.RAT_BREEDING_LANTERN.get()), this.leftPos + 252, this.topPos + 12);
	}
	@Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(this.font, this.title, 8, 8, 0x404040, false);
		if (this.menu.snapshot == null) return;
		CageRosterPacket snapshot = this.menu.snapshot;
		graphics.drawString(this.font, Component.translatable(snapshot.complete() ? "cage.rats.capacity" : "cage.rats.incomplete", snapshot.residents().size(), snapshot.capacity()), 8, 24, snapshot.complete() ? 0x404040 : 0x9A3020, false);
		graphics.drawString(this.font, (this.page + 1) + "/" + Math.max(1, (snapshot.residents().size() + 5) / 6), 62, 172, 0x404040, false);
		CageRosterPacket.Resident selected = this.selected();
		if (selected != null) {
			graphics.drawString(this.font, this.font.plainSubstrByWidth(selected.name().getString(), 104), 168, 42, 0x404040, false);
			graphics.drawString(this.font, Component.translatable(selected.baby() ? "cage.rats.baby" : selected.male() ? "cage.rats.male" : "cage.rats.female"), 168, 56, 0x404040, false);
		}
		graphics.drawString(this.font, Component.translatable("cage.rats.inventory"), 168, 96, 0x404040, false);
		CageRosterPacket.Resident mate = snapshot.residents().stream().filter(rat -> rat.id() == this.partner).findFirst().orElse(null);
		graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("cage.rats.partner", mate == null ? "—" : mate.name().getString()).getString(), 148), 8, 220, 0x404040, false);
	}
	@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { this.renderBackground(graphics); super.render(graphics, mouseX, mouseY, partialTick); this.renderTooltip(graphics, mouseX, mouseY); }
	@Override public boolean keyPressed(int key, int scan, int modifiers) { if (this.name.isFocused() && key != 256) return this.name.keyPressed(key, scan, modifiers); return super.keyPressed(key, scan, modifiers); }
}
