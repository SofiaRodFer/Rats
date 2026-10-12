package com.github.alexthe666.rats.client.gui;

import com.github.alexthe666.rats.server.inventory.RatFarmingMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.core.Direction;

public class RatFarmingScreen extends AbstractContainerScreen<RatFarmingMenu> {
	private static final int STEP = 10;
	public RatFarmingScreen(RatFarmingMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); this.imageWidth = 330; this.imageHeight = 230; }
	@Override protected void init() {
		super.init(); this.addRenderableWidget(Button.builder(Component.translatable("gui.rats.farm.storage"), b -> this.menu.layoutView = false).bounds(this.leftPos + 8, this.topPos + 12, 78, 18).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.rats.farm.plan"), b -> this.menu.layoutView = true).bounds(this.leftPos + 90, this.topPos + 12, 78, 18).build());
		for (int i = 0; i < 4; i++) { final int action = i; this.addRenderableWidget(Button.builder(Component.literal(i % 2 == 0 ? "−" : "+"), b -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, action)).bounds(this.leftPos + 182 + i % 2 * 70, this.topPos + 48 + i / 2 * 46, 64, 20).build()); }
		this.addRenderableWidget(Button.builder(Component.translatable("gui.rats.farm.rotate"), b -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 4)).bounds(this.leftPos + 182, this.topPos + 140, 134, 20).build());
	}
	@Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xFFC6C6C6);
		if (this.menu.layoutView) {
			var layout = this.menu.layout();
			for (int z = 0; z < layout.height; z++) for (int x = 0; x < layout.width; x++) {
				int index = z * 16 + x, px = this.leftPos + 8 + x * STEP, py = this.topPos + 34 + z * STEP;
				graphics.fill(px, py, px + STEP - 1, py + STEP - 1, layout.water.get(index) ? 0xFF326CC0 : layout.lights.get(index) ? 0xFFE4D970 : 0xFF76543B);
				var seed = this.menu.ghosts.getItem(index);
				if (!seed.isEmpty()) { graphics.pose().pushPose(); graphics.pose().translate(px + 1, py + 1, 0); graphics.pose().scale(0.5F, 0.5F, 1); graphics.renderItem(seed, 0, 0); graphics.pose().popPose(); }
			}
		} else for (int i = 0; i < 90; i++) { var slot = this.menu.slots.get(i); graphics.fill(this.leftPos + slot.x - 1, this.topPos + slot.y - 1, this.leftPos + slot.x + 17, this.topPos + slot.y + 17, 0xFF777777); graphics.fill(this.leftPos + slot.x, this.topPos + slot.y, this.leftPos + slot.x + 16, this.topPos + slot.y + 16, 0xFF999999); }
	}
	@Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(this.font, this.title, 8, 2, 0x404040, false);
		graphics.drawString(this.font, Component.translatable("gui.rats.farm.width", this.menu.data.get(0)), 182, 35, 0x404040, false);
		graphics.drawString(this.font, Component.translatable("gui.rats.farm.height", this.menu.data.get(1)), 182, 81, 0x404040, false);
		graphics.drawString(this.font, Component.translatable("quarry.rats.direction." + Direction.from2DDataValue(this.menu.data.get(2)).getName()), 182, 123, 0x404040, false);
		graphics.drawString(this.font, Component.translatable("gui.rats.farm.workers", this.menu.data.get(3)), 182, 174, 0x404040, false);
		if (this.menu.layoutView) { graphics.drawString(this.font, Component.translatable("gui.rats.farm.paint"), 8, 200, 0x404040, false); graphics.drawString(this.font, Component.translatable("gui.rats.farm.clear"), 8, 214, 0x404040, false); }
	}
	@Override public boolean mouseClicked(double x, double y, int button) {
		if (this.menu.layoutView && (button == 0 || button == 1)) {
			int col = (int)Math.floor((x - this.leftPos - 8) / STEP), row = (int)Math.floor((y - this.topPos - 34) / STEP);
			var layout = this.menu.layout();
			if (col >= 0 && row >= 0 && col < layout.width && row < layout.height) {
				int index = row * 16 + col;
				if (layout.cropCell(index)) this.minecraft.gameMode.handleInventoryMouseClick(this.menu.containerId, 90 + index, button, ClickType.PICKUP, this.minecraft.player);
				return true;
			}
		} return super.mouseClicked(x, y, button);
	}
	@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(graphics); super.render(graphics, mouseX, mouseY, partialTick); this.renderTooltip(graphics, mouseX, mouseY);
		if (this.menu.layoutView) { int x = (mouseX - this.leftPos - 8) / STEP, z = (mouseY - this.topPos - 34) / STEP; var layout = this.menu.layout(); if (mouseX >= this.leftPos + 8 && mouseY >= this.topPos + 34 && x < layout.width && z < layout.height) { int cell = z * 16 + x; if (layout.water.get(cell)) graphics.renderTooltip(this.font, new ItemStack(Items.WATER_BUCKET), mouseX, mouseY); else if (layout.lights.get(cell)) graphics.renderTooltip(this.font, Component.translatable("block.rats.rat_farm_light"), mouseX, mouseY); else if (!this.menu.ghosts.getItem(cell).isEmpty()) graphics.renderTooltip(this.font, this.menu.ghosts.getItem(cell), mouseX, mouseY); } }
	}
}
