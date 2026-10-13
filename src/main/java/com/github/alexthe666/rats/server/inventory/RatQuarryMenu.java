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
	private final Player viewer;
	private int refreshAt;
	public com.github.alexthe666.rats.server.message.QuarryRosterPacket snapshot;
	public boolean storageView = true;
	public RatQuarryMenu(int id, Inventory inventory) {
		this(id, inventory, new SimpleContainer(64), new SimpleContainerData(6));
	}
	public RatQuarryMenu(int id, Inventory inventory, Container container, ContainerData data) {
		super(RatsMenuRegistry.RAT_QUARRY_CONTAINER.get(), id);
		checkContainerSize(container, 64);
		checkContainerDataCount(data, 6);
		this.container = container;
		this.data = data;
		this.viewer = inventory.player;
		container.startOpen(inventory.player);
		// Keep the saved slot indices unchanged; only their screen positions move.
		for (int i = 0; i < 64; i++) this.addSlot(new Slot(container, i, 8 + i % 11 * 18, 42 + i / 11 * 18) {
			@Override public boolean isActive() { return RatQuarryMenu.this.storageView; }
		});
		for (int i = 0; i < 27; i++) this.addSlot(new Slot(inventory, 9 + i, 8 + i % 9 * 18, 164 + i / 9 * 18));
		for (int i = 0; i < 9; i++) this.addSlot(new Slot(inventory, i, 8 + i * 18, 222));
		this.addDataSlots(data);
	}
	@Override
	public boolean stillValid(Player player) { return this.container.stillValid(player); }
	@Override
	public void removed(Player player) { super.removed(player); this.container.stopOpen(player); }
	@Override
	public boolean clickMenuButton(Player player, int button) {
		if (player != this.viewer || button < 0 || button > 8 || !this.stillValid(player) || !(this.container instanceof RatQuarryBlockEntity quarry)) return false;
		if (button == 8) quarry.recallWorkers(player); else quarry.configure(button);
		this.refreshAt = 0;
		this.broadcastChanges();
		return true;
	}
	@Override public void broadcastChanges() {
		if (this.viewer instanceof net.minecraft.server.level.ServerPlayer server && this.container instanceof RatQuarryBlockEntity quarry && this.stillValid(server) && server.tickCount >= this.refreshAt) {
			this.refreshAt = server.tickCount + 20;
			this.snapshot = new com.github.alexthe666.rats.server.message.QuarryRosterPacket(this.containerId, quarry.workerIds().size(), quarry.getWorkerLimit(), quarry.workerDescriptions(server));
			com.github.alexthe666.rats.server.message.RatsNetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> server), this.snapshot);
		}
		super.broadcastChanges();
	}
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		if (player != this.viewer || !this.stillValid(player) || index < 0 || index >= this.slots.size()) return ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
		ItemStack stack = slot.getItem(), original = stack.copy();
		if (!this.moveItemStackTo(stack, index < 64 ? 64 : 0, index < 64 ? this.slots.size() : 64, index < 64)) return ItemStack.EMPTY;
		if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
		slot.onTake(player, stack);
		return original;
	}
}
