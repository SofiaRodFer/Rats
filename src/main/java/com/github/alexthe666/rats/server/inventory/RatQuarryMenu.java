package com.github.alexthe666.rats.server.inventory;

import com.github.alexthe666.rats.registry.RatsMenuRegistry;
import com.github.alexthe666.rats.server.block.entity.RatQuarryBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public class RatQuarryMenu extends AbstractContainerMenu {
	public final Container container;
	public final ContainerData data;
	public RatQuarryMenu(int id, Inventory inventory) {
		this(id, inventory, new SimpleContainer(64), new SimpleContainerData(6));
	}
	public RatQuarryMenu(int id, Inventory inventory, Container container, ContainerData data) {
		super(RatsMenuRegistry.RAT_QUARRY_CONTAINER.get(), id);
		checkContainerSize(container, 64);
		checkContainerDataCount(data, 6);
		this.container = container;
		this.data = data;
		container.startOpen(inventory.player);
		// Keep the saved slot indices unchanged; only their screen positions move.
		for (int i = 0; i < 64; i++) this.addSlot(new Slot(container, i, 8 + i % 8 * 18, 42 + i / 8 * 18));
		for (int i = 0; i < 27; i++) this.addSlot(new Slot(inventory, 9 + i, 8 + i % 9 * 18, 198 + i / 9 * 18));
		for (int i = 0; i < 9; i++) this.addSlot(new Slot(inventory, i, 8 + i * 18, 256));
		this.addDataSlots(data);
	}
	@Override
	public boolean stillValid(Player player) { return this.container.stillValid(player); }
	@Override
	public void removed(Player player) { super.removed(player); this.container.stopOpen(player); }
	@Override
	public boolean clickMenuButton(Player player, int button) {
		if (button < 0 || button > 7 || !this.stillValid(player) || !(this.container instanceof RatQuarryBlockEntity quarry)) return false;
		quarry.configure(button);
		this.broadcastChanges();
		return true;
	}
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		if (index < 0 || index >= this.slots.size()) return ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) return ItemStack.EMPTY;
		ItemStack stack = slot.getItem(), original = stack.copy();
		if (!this.moveItemStackTo(stack, index < 64 ? 64 : 0, index < 64 ? this.slots.size() : 64, index < 64)) return ItemStack.EMPTY;
		if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
		slot.onTake(player, stack);
		return original;
	}
}
