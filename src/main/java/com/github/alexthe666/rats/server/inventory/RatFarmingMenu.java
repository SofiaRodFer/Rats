package com.github.alexthe666.rats.server.inventory;

import com.github.alexthe666.rats.registry.RatsMenuRegistry;
import com.github.alexthe666.rats.server.block.entity.RatFarmingBlockEntity;
import com.github.alexthe666.rats.server.misc.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public class RatFarmingMenu extends AbstractContainerMenu {
	public final Container storage, ghosts;
	public final ContainerData data;
	public boolean layoutView;
	public RatFarmingMenu(int id, Inventory inventory) { this(id, inventory, new SimpleContainer(54), new SimpleContainer(256), new SimpleContainerData(4)); }
	public RatFarmingMenu(int id, Inventory inventory, RatFarmingBlockEntity farm) { this(id, inventory, farm, farm.ghost, farm.data); }
	private RatFarmingMenu(int id, Inventory inventory, Container storage, Container ghosts, ContainerData data) {
		super(RatsMenuRegistry.RAT_FARMING_CONTAINER.get(), id); this.storage = storage; this.ghosts = ghosts; this.data = data;
		checkContainerSize(storage, 54); checkContainerSize(ghosts, 256); checkContainerDataCount(data, 4);
		for (int i = 0; i < 54; i++) this.addSlot(new Slot(storage, i, 8 + i % 9 * 18, 32 + i / 9 * 18) { @Override public boolean isActive() { return !layoutView; } });
		for (int i = 0; i < 27; i++) this.addSlot(new Slot(inventory, i + 9, 8 + i % 9 * 18, 150 + i / 9 * 18) { @Override public boolean isActive() { return !layoutView; } });
		for (int i = 0; i < 9; i++) this.addSlot(new Slot(inventory, i, 8 + i * 18, 208) { @Override public boolean isActive() { return !layoutView; } });
		for (int i = 0; i < 256; i++) this.addSlot(new Slot(ghosts, i, -1000, -1000) { @Override public boolean isActive() { return false; } @Override public boolean mayPlace(ItemStack stack) { return false; } @Override public boolean mayPickup(Player player) { return false; } });
		this.addDataSlots(data);
	}
	public FarmLayout layout() { return new FarmLayout(this.data.get(0), this.data.get(1)); }
	@Override public boolean stillValid(Player player) { return this.storage.stillValid(player); }
	@Override public void clicked(int slot, int button, ClickType type, Player player) {
		if (!this.stillValid(player)) return;
		if (slot >= 90) {
			int cell = slot - 90;
			if (type != ClickType.PICKUP || (button != 0 && button != 1) || !this.layout().cropCell(cell)) return;
			ItemStack seed = button == 1 ? ItemStack.EMPTY : this.getCarried();
			if (!seed.isEmpty() && !FarmCrops.seed(seed)) return;
			if (this.storage instanceof RatFarmingBlockEntity farm) farm.paint(cell, seed); else this.ghosts.setItem(cell, seed.isEmpty() ? ItemStack.EMPTY : seed.copyWithCount(1));
			this.broadcastChanges(); return;
		}
		super.clicked(slot, button, type, player);
	}
	@Override public boolean clickMenuButton(Player player, int button) {
		if (!this.stillValid(player) || !(this.storage instanceof RatFarmingBlockEntity farm)) return false;
		int width = farm.layout().width, height = farm.layout().height;
		switch(button) { case 0 -> width--; case 1 -> width++; case 2 -> height--; case 3 -> height++; case 4 -> { farm.rotate(); this.broadcastChanges(); return true; } default -> { return false; } }
		farm.configure(width, height); this.broadcastChanges(); return true;
	}
	@Override public ItemStack quickMoveStack(Player player, int index) {
		if (!this.stillValid(player) || index < 0 || index >= 90) return ItemStack.EMPTY;
		Slot slot = this.slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
		var stack = slot.getItem(); var copy = stack.copy();
		if (!this.moveItemStackTo(stack, index < 54 ? 54 : 0, index < 54 ? 90 : 54, index < 54)) return ItemStack.EMPTY;
		if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged(); slot.onTake(player, stack); return copy;
	}
}
