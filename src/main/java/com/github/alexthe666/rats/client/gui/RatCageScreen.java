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
	private final Button[] rows = new Button[4];
	private final RatPortrait portrait = new RatPortrait();
	private Button previous, next, release, home, pair, breed, rename, autoBreed;
	private EditBox name;
	private int page, partner = -1, lastSelected = -1;
	public RatCageScreen(RatCageMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); this.imageWidth = 310; this.imageHeight = 240; }
	@Override protected void init() {
		super.init();
		for (int i = 0; i < 4; i++) {
			int row = i;
			this.rows[i] = this.addRenderableWidget(Button.builder(Component.empty(), button -> { var rat = this.at(row); if (rat != null) this.send(0, rat.id()); }).bounds(this.leftPos + 8, this.topPos + 38 + i * 24, 146, 22).build());
		}
		this.previous = this.button("<", 8, 134, 30, () -> this.page = Math.max(0, this.page - 1));
		this.next = this.button(">", 124, 134, 30, () -> this.page++);
		this.autoBreed = this.button("cage.rats.auto_off", 192, 12, 110, () -> this.send(6, -1));
		this.autoBreed.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("cage.rats.auto_help")));
		this.name = this.addRenderableWidget(new EditBox(this.font, this.leftPos + 162, this.topPos + 38, 88, 18, Component.translatable("cage.rats.name")));
		this.name.setMaxLength(32);
		this.rename = this.button("cage.rats.rename", 254, 38, 48, () -> this.sendSelected(5));
		this.release = this.button("cage.rats.release", 162, 58, 68, () -> this.sendSelected(1));
		this.home = this.button("cage.rats.home", 234, 58, 68, () -> { var rat = this.selected(); if (rat != null) this.send(rat.home() ? 3 : 2, rat.id()); });
		this.pair = this.button("cage.rats.pair", 162, 78, 68, this::cyclePartner);
		this.breed = this.button("cage.rats.breed", 234, 78, 68, () -> this.sendSelected(4));
		this.containerTick();
	}
	private Button button(String key, int x, int y, int width, Runnable action) {
		return this.addRenderableWidget(Button.builder(Component.translatable(key), button -> action.run()).bounds(this.leftPos + x, this.topPos + y, width, 20).build());
	}
	private CageRosterPacket.Resident at(int row) { return this.menu.snapshot != null && this.page * 4 + row < this.menu.snapshot.residents().size() ? this.menu.snapshot.residents().get(this.page * 4 + row) : null; }
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
		if (snapshot != null) this.page = Math.min(this.page, Math.max(0, (snapshot.residents().size() - 1) / 4));
		for (int i = 0; i < 4; i++) this.rows[i].visible = this.at(i) != null;
		this.previous.active = this.page > 0; this.next.active = snapshot != null && (this.page + 1) * 4 < snapshot.residents().size();
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
		VanillaRatGui.panel(graphics,this.leftPos,this.topPos,this.imageWidth,this.imageHeight);
		for (int i = 0; i < this.menu.slots.size(); i++) {
			var slot = this.menu.slots.get(i); if (i < 13 && this.selected() == null) continue;
			int x = this.leftPos + slot.x - 1, y = this.topPos + slot.y - 1;
			graphics.blit(new net.minecraft.resources.ResourceLocation("minecraft","textures/gui/container/inventory.png"),x,y,7,83,18,18);if(!slot.isActive())graphics.fill(x+1,y+1,x+17,y+17,0x88555555);
			if (!slot.isActive()) { graphics.fill(x + 3, y + 8, x + 15, y + 10, 0xFF737373); }
		}
	}
	@Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(this.font, this.title, 8, 8, 0x404040, false); var snapshot = this.menu.snapshot; if (snapshot == null) return;
		graphics.drawString(this.font, this.playerInventoryTitle,74,154,0x404040,false);
		graphics.drawString(this.font,this.font.plainSubstrByWidth(Component.translatable(snapshot.complete()?"cage.rats.own_capacity":"cage.rats.incomplete",snapshot.residents().size(),snapshot.capacity()).getString(),176),8,24,snapshot.complete()?0x404040:0x9A3020,false);
		graphics.drawString(this.font,(this.page+1)+"/"+Math.max(1,(snapshot.residents().size()+3)/4),68,140,0x404040,false);
		var selected = this.selected();
		if(selected==null){graphics.drawWordWrap(this.font,Component.translatable("cage.rats.select"),162,42,140,0x404040);return;}
		graphics.drawString(this.font,Component.translatable("cage.rats.cargo"),162,100,0x404040,false);
		graphics.drawString(this.font,this.font.plainSubstrByWidth(Component.translatable("cage.rats.upgrades").getString(),80),220,150,0x404040,false);
	}
	@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(graphics); super.render(graphics, mouseX, mouseY, partialTick);
		for (int i = 0; i < 4; i++) {
			var rat = this.at(i); if (rat == null) continue; int y = this.topPos + 38 + i * 24;
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
