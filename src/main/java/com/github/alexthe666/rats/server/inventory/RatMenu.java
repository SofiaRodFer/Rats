package com.github.alexthe666.rats.server.inventory;

import com.github.alexthe666.rats.RatsMod;
import com.github.alexthe666.rats.registry.RatsMenuRegistry;
import com.github.alexthe666.rats.registry.RatsUpgradeConflictRegistry;
import com.github.alexthe666.rats.server.inventory.container.RatContainer;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.items.upgrades.BaseRatUpgradeItem;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;

public class RatMenu extends AbstractContainerMenu {
	private final Container ratInventory;
	private final Player viewer;
	private final ContainerData cargoData;
	public TamedRat getRat() { return this.ratInventory instanceof RatContainer source && source.getRat() instanceof TamedRat rat ? rat : null; }
	private boolean canChangeUpgrade(int index, ItemStack replacement) { return RatCargo.canReplaceUpgrade(index, replacement, i -> this.ratInventory.getItem(3 + i), i -> this.slots.get(i == 0 ? 0 : 41 + i).getItem()); }
	private static final ResourceLocation EMPTY_HELMET_SLOT = new ResourceLocation(RatsMod.MODID, "item/empty_rat_helmet_slot");
	private static final ResourceLocation EMPTY_BANNER_SLOT = new ResourceLocation(RatsMod.MODID, "item/empty_rat_banner_slot");
	private static final ResourceLocation EMPTY_ITEM_SLOT = new ResourceLocation(RatsMod.MODID, "item/empty_rat_item_slot");
	private static final ResourceLocation EMPTY_UPGRADE_SLOT = new ResourceLocation(RatsMod.MODID, "item/empty_rat_upgrade_slot");

	public RatMenu(int id, Container ratInventory, Inventory playerInventory) {
		super(RatsMenuRegistry.RAT_CONTAINER.get(), id);
		this.ratInventory = ratInventory;
		this.viewer = playerInventory.player;
		this.cargoData = this.getRat() == null ? new SimpleContainerData(2) : new ContainerData() {
			public int get(int index) { return index == 0 ? getRat().cargo.slots() : getRat().cargo.limit(new ItemStack(net.minecraft.world.item.Items.COBBLESTONE)); }
			public void set(int index, int value) {}
			public int getCount() { return 2; }
		};
		this.addDataSlots(this.cargoData);
		ratInventory.startOpen(playerInventory.player);
		this.addSlot(new Slot(ratInventory, 0, 70, 54) {
			@Override public int getMaxStackSize(ItemStack stack) { return Math.min(stack.getMaxStackSize(), RatMenu.this.cargoData.get(1)); }
			@Override public boolean mayPlace(ItemStack stack) { return RatMenu.this.stillValid(viewer) && (getRat() == null || getRat().canRatPickupItem(stack)); }
			@Override
			public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
				return Pair.of(InventoryMenu.BLOCK_ATLAS, EMPTY_ITEM_SLOT);
			}
		});
		this.addSlot(new Slot(ratInventory, 1, 70, 18) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return super.mayPlace(stack) && !stack.isEmpty() && stack.getItem().canEquip(stack, EquipmentSlot.HEAD, playerInventory.player);
			}

			@Override
			public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
				return Pair.of(InventoryMenu.BLOCK_ATLAS, EMPTY_HELMET_SLOT);
			}
		});
		this.addSlot(new Slot(ratInventory, 2, 70, 36) {
			public boolean mayPlace(ItemStack stack) {
				return super.mayPlace(stack) && !stack.isEmpty() && stack.getItem() instanceof BannerItem;
			}

			@Override
			public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
				return Pair.of(InventoryMenu.BLOCK_ATLAS, EMPTY_BANNER_SLOT);
			}
		});

		for (int i = 0; i <= 2; ++i) {
			int finalI = i;
			this.addSlot(new Slot(ratInventory, 3 + finalI, -2, 18 + finalI * 18) {

				@Override
				public int getMaxStackSize() {
					return 1;
				}

				public boolean mayPlace(ItemStack stack) {
					return RatMenu.this.stillValid(viewer) && super.mayPlace(stack) && !stack.isEmpty() && stack.getItem() instanceof BaseRatUpgradeItem && RatMenu.canCoexistUpgrade(this.container, stack, finalI) && RatMenu.this.canChangeUpgrade(finalI, stack);
				}
				@Override public boolean mayPickup(Player player) { return RatMenu.this.stillValid(player) && RatMenu.this.canChangeUpgrade(finalI, ItemStack.EMPTY); }

				@Override
				public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
					return Pair.of(InventoryMenu.BLOCK_ATLAS, EMPTY_UPGRADE_SLOT);
				}
			});
		}

		for (int j = 0; j < 3; ++j) {
			for (int k1 = 0; k1 < 9; ++k1) {
				this.addSlot(new Slot(playerInventory, k1 + j * 9 + 9, 8 + k1 * 18, 102 + j * 18 - 18));
			}
		}

		for (int k = 0; k < 9; ++k) {
			this.addSlot(new Slot(playerInventory, k, 8 + k * 18, 142));
		}
		Container cargo = ratInventory instanceof RatContainer source && source.getRat() instanceof TamedRat rat ? rat.cargo.extraContainer() : new SimpleContainer(9);
		for (int i = 0; i < 9; i++) {
			this.addSlot(new Slot(cargo, i, 8 + i * 18, 174) {
				@Override public int getMaxStackSize(ItemStack stack) { return Math.min(stack.getMaxStackSize(), RatMenu.this.cargoData.get(1)); }
				@Override public boolean mayPlace(ItemStack stack) {
					return RatMenu.this.stillValid(viewer) && RatMenu.this.cargoData.get(0) == 10 && (getRat() == null || getRat().canRatPickupItem(stack));
				}
			});
		}
	}

	public RatMenu(int i, Inventory inventory) {
		this(i, new SimpleContainer(6), inventory);
	}

	public static boolean canCoexistUpgrade(Container inventory, ItemStack upgrade, int ourSlot) {
		return switch (ourSlot) {
			case 0 ->
					!RatsUpgradeConflictRegistry.doesConflict(upgrade, inventory.getItem(4)) && !RatsUpgradeConflictRegistry.doesConflict(upgrade, inventory.getItem(5));
			case 1 ->
					!RatsUpgradeConflictRegistry.doesConflict(upgrade, inventory.getItem(3)) && !RatsUpgradeConflictRegistry.doesConflict(upgrade, inventory.getItem(5));
			case 2 ->
					!RatsUpgradeConflictRegistry.doesConflict(upgrade, inventory.getItem(3)) && !RatsUpgradeConflictRegistry.doesConflict(upgrade, inventory.getItem(4));
			default -> false;
		};
	}

	@Override
	public boolean stillValid(Player player) {
		if (player != this.viewer) return false;
		var rat = this.getRat();
		return rat == null ? player.level().isClientSide() : rat.isAlive() && !rat.isRemoved() && rat.level() == player.level() && rat.isOwnedBy(player) && player.distanceToSqr(rat) <= 64;
	}
	@Override public void clicked(int slot, int button, ClickType type, Player player) {
		if (this.stillValid(player)) super.clicked(slot, button, type, player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		if (!this.stillValid(player) || index < 0 || index >= this.slots.size() || !this.slots.get(index).mayPickup(player)) return ItemStack.EMPTY;
		ItemStack itemstack = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot.hasItem()) {
			ItemStack itemstack1 = slot.getItem();
			itemstack = itemstack1.copy();
			if (index >= 42) {
				if (!this.moveItemStackTo(itemstack1, 6, 42, true)) return ItemStack.EMPTY;
			} else if (index < this.ratInventory.getContainerSize()) {
				if (!this.moveItemStackTo(itemstack1, this.ratInventory.getContainerSize(), 42, true)) {
					return ItemStack.EMPTY;
				}
			} else if (this.getSlot(1).mayPlace(itemstack1) && !this.getSlot(1).hasItem()) {
				if (!this.moveItemStackTo(itemstack1, 1, 2, false)) {
					return ItemStack.EMPTY;
				}
			} else if (this.getSlot(2).mayPlace(itemstack1) && !this.getSlot(2).hasItem()) {
				if (!this.moveItemStackTo(itemstack1, 2, 3, false)) {
					return ItemStack.EMPTY;
				}
			} else if (this.getSlot(3).mayPlace(itemstack1) && !this.getSlot(3).hasItem()) {
				if (!this.moveItemStackTo(itemstack1, 3, 4, false)) {
					return ItemStack.EMPTY;
				}
			} else if (this.getSlot(4).mayPlace(itemstack1) && !this.getSlot(4).hasItem()) {
				if (!this.moveItemStackTo(itemstack1, 4, 5, false)) {
					return ItemStack.EMPTY;
				}
			} else if (this.getSlot(5).mayPlace(itemstack1) && !this.getSlot(5).hasItem()) {
				if (!this.moveItemStackTo(itemstack1, 5, 6, false)) {
					return ItemStack.EMPTY;
				}
			} else if (this.getSlot(0).mayPlace(itemstack1)) {
				if (!this.moveItemStackTo(itemstack1, 0, 1, false)) {
					return ItemStack.EMPTY;
				}
			} else if (this.ratInventory.getContainerSize() <= 6 || !this.moveItemStackTo(itemstack1, 6, this.ratInventory.getContainerSize(), false)) {
				return ItemStack.EMPTY;
			}

			if (itemstack1.isEmpty()) {
				slot.set(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}

		return itemstack;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.ratInventory.stopOpen(player);
		if (this.ratInventory instanceof RatContainer container) {
			container.getRat().closeInventory();
		}
	}
}
