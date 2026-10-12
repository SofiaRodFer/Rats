package com.github.alexthe666.rats.client.gui;

import com.github.alexthe666.rats.server.inventory.RatQuarryMenu;
import com.github.alexthe666.rats.server.block.entity.RatQuarryBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class RatQuarryScreen extends AbstractContainerScreen<RatQuarryMenu> {
	private final Button[] directions = new Button[5];
	private Button pause, decrease, increase;
	public RatQuarryScreen(RatQuarryMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = 276;
		this.imageHeight = 280;
		this.inventoryLabelY = 187;
	}
	@Override
	protected void init() {
		super.init();
		for (int i = 0; i < 5; i++) {
			int action = i;
			this.directions[i] = this.addRenderableWidget(Button.builder(Component.translatable("quarry.rats.direction." + RatQuarryBlockEntity.DIRECTIONS[i].getName()), button -> this.send(action)).bounds(this.leftPos + 8 + i * 44, this.topPos + 18, 42, 20).build());
		}
		this.pause = this.addRenderableWidget(Button.builder(Component.empty(), button -> this.send(7)).bounds(this.leftPos + 230, this.topPos + 18, 38, 20).build());
		this.decrease = this.addRenderableWidget(Button.builder(Component.literal("-16"), button -> this.send(5)).bounds(this.leftPos + 180, this.topPos + 96, 42, 20).build());
		this.increase = this.addRenderableWidget(Button.builder(Component.literal("+16"), button -> this.send(6)).bounds(this.leftPos + 226, this.topPos + 96, 42, 20).build());
	}
	private void send(int action) {
		if (this.minecraft != null && this.minecraft.gameMode != null) this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, action);
	}
	@Override
	protected void containerTick() {
		super.containerTick();
		for (int i = 0; i < this.directions.length; i++) this.directions[i].active = this.menu.data.get(0) != i;
		boolean bottom = this.menu.data.get(0) == 0 && this.menu.data.get(4) == 4;
		this.decrease.active = !bottom && this.menu.data.get(1) > 16;
		this.increase.active = !bottom && this.menu.data.get(1) < (this.menu.data.get(4) == 6 ? 512 : 256);
		this.pause.setMessage(Component.literal(this.menu.data.get(3) == 1 ? "▶" : "Ⅱ"));
	}
	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		int x = this.leftPos, y = this.topPos;
		graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF37343D);
		graphics.fill(x + 2, y + 2, x + this.imageWidth - 2, y + this.imageHeight - 2, 0xFFC6C6C6);
		graphics.fill(x + 3, y + 3, x + this.imageWidth - 3, y + 4, 0xFFFFFFFF);
		for (Slot slot : this.menu.slots) {
			graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF373737);
			graphics.fill(x + slot.x, y + slot.y, x + slot.x + 17, y + slot.y + 17, 0xFFFFFFFF);
			graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFF8B8B8B);
		}
	}
	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		super.renderLabels(graphics, mouseX, mouseY);
		boolean bottom = this.menu.data.get(0) == 0 && this.menu.data.get(4) == 4;
		graphics.drawWordWrap(this.font, Component.translatable(bottom ? "quarry.rats.world_bottom" : "quarry.rats.length", this.menu.data.get(1)), 180, 78, 90, 0x404040);
		graphics.drawWordWrap(this.font, Component.translatable("quarry.rats.progress", this.menu.data.get(2), this.menu.data.get(5)), 180, 122, 90, 0x404040);
		graphics.drawWordWrap(this.font, Component.translatable("quarry.rats.status." + this.menu.data.get(3)), 180, 152, 90, 0x404040);
	}
	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);
		this.renderTooltip(graphics, mouseX, mouseY);
	}
}
