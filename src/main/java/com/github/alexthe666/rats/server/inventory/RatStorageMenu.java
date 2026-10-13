package com.github.alexthe666.rats.server.inventory;
import com.github.alexthe666.rats.registry.RatsMenuRegistry;
import com.github.alexthe666.rats.server.block.RatStorageBlock;
import com.github.alexthe666.rats.server.block.entity.RatStorageBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public class RatStorageMenu extends AbstractContainerMenu {
	public final RatStorageBlockEntity storage;
	public final boolean controller;
	private final Container inventory;
	public RatStorageMenu(int id, Inventory player, RatStorageBlockEntity storage) { this(id, player, storage, storage.kind() == RatStorageBlock.Kind.CONTROLLER); }
	private RatStorageMenu(int id, Inventory player, RatStorageBlockEntity storage, boolean controller) {
		super(RatsMenuRegistry.RAT_STORAGE_CONTAINER.get(), id); this.storage = storage; this.controller = controller; this.inventory = storage == null ? new SimpleContainer(54) : storage;
		for (int i = 0; i < 54; i++) this.addSlot(new Slot(this.inventory, i, 8 + i % 9 * 18, 32 + i / 9 * 18));
		for (int i = 0; i < 27; i++) this.addSlot(new Slot(player, i + 9, 8 + i % 9 * 18, 154 + i / 9 * 18));
		for (int i = 0; i < 9; i++) this.addSlot(new Slot(player, i, 8 + i * 18, 212));
	}
	public RatStorageMenu(int id, Inventory inventory, FriendlyByteBuf buf) { this(id, inventory, null, buf.readBoolean()); }
	@Override public boolean stillValid(Player player) { return this.storage == null || this.storage.stillValid(player); }
	@Override public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY; ItemStack held = slot.getItem(), copy = held.copy();
		if (!this.moveItemStackTo(held, index < 54 ? 54 : 0, index < 54 ? 90 : 54, index < 54)) return ItemStack.EMPTY;
		if (held.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged(); slot.onTake(player, held); return copy;
	}
}
