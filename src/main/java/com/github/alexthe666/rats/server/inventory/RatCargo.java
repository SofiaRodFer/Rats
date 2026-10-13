package com.github.alexthe666.rats.server.inventory;

import com.github.alexthe666.rats.registry.RatsItemRegistry;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.misc.RatUpgradeUtils;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.List;

/** Ten cargo slots in total; the visible main hand is slot zero. */
public final class RatCargo {
	private final TamedRat rat;
	private final NonNullList<ItemStack> extra = NonNullList.withSize(9, ItemStack.EMPTY);

	public RatCargo(TamedRat rat) { this.rat = rat; }
	public boolean hasTray() { return RatUpgradeUtils.hasUpgrade(this.rat, RatsItemRegistry.RAT_UPGRADE_SILVER_TRAY.get()); }
	public int slots() { return this.hasTray() ? 10 : 1; }
	public int limit(ItemStack stack) {
		return this.hasTray() || RatUpgradeUtils.hasUpgrade(this.rat, RatsItemRegistry.RAT_UPGRADE_PLATTER.get()) || RatUpgradeUtils.hasUpgrade(this.rat, RatsItemRegistry.RAT_UPGRADE_STORAGE_KEEPER.get()) ? stack.getMaxStackSize() : 1;
	}
	public ItemStack get(int slot) { return slot == 0 ? this.rat.getMainHandItem() : this.extra.get(slot - 1); }
	public void set(int slot, ItemStack stack) {
		if (slot == 0) this.rat.setItemInHand(InteractionHand.MAIN_HAND, stack);
		else this.extra.set(slot - 1, stack);
	}
	public boolean isEmpty() {
		for (int i = 0; i < 10; i++) if (!this.get(i).isEmpty()) return false;
		return true;
	}
	public boolean isFull() {
		for (int i = 0; i < this.slots(); i++) {
			ItemStack stack = this.get(i);
			if (stack.isEmpty() || stack.getCount() < this.limit(stack)) return false;
		}
		return true;
	}
	public boolean canAccept(ItemStack stack) { return this.insert(stack, true).getCount() < stack.getCount(); }
	public ItemStack insert(ItemStack input, boolean simulate) {
		if (!this.rat.canRatPickupItem(input)) return input.copy();
		ItemStack remainder = input.copy();
		for (int pass = 0; pass < 2; pass++) for (int i = 0; i < this.slots() && !remainder.isEmpty(); i++) {
			ItemStack held = this.get(i);
			if (pass == 0 && (held.isEmpty() || !ItemStack.isSameItemSameTags(held, remainder))) continue;
			if (pass == 1 && !held.isEmpty()) continue;
			int amount = Math.min(remainder.getCount(), this.limit(remainder) - held.getCount());
			if (amount <= 0) continue;
			if (!simulate) {
				ItemStack combined = remainder.copy();
				combined.setCount(held.getCount() + amount);
				this.set(i, combined);
			}
			remainder.shrink(amount);
		}
		return remainder;
	}
	public boolean canFit(List<ItemStack> drops) {
		NonNullList<ItemStack> snapshot = NonNullList.withSize(this.slots(), ItemStack.EMPTY);
		for (int i = 0; i < snapshot.size(); i++) snapshot.set(i, this.get(i).copy());
		for (ItemStack drop : drops) {
			if (!this.rat.canRatPickupItem(drop)) continue;
			ItemStack left = drop.copy();
			for (int pass = 0; pass < 2; pass++) for (int i = 0; i < snapshot.size() && !left.isEmpty(); i++) {
				ItemStack held = snapshot.get(i);
				if (pass == 0 && (held.isEmpty() || !ItemStack.isSameItemSameTags(held, left))) continue;
				if (pass == 1 && !held.isEmpty()) continue;
				int amount = Math.min(left.getCount(), this.limit(left) - held.getCount());
				if (amount <= 0) continue;
				ItemStack combined = left.copy();
				combined.setCount(held.getCount() + amount);
				snapshot.set(i, combined);
				left.shrink(amount);
			}
			if (!left.isEmpty()) return false;
		}
		return true;
	}
	public boolean canDeposit(IItemHandler destination) {
		for (int i = 0; i < 10; i++) {
			ItemStack stack = this.get(i);
			if (!stack.isEmpty() && this.rat.shouldDepositItem(stack) && ItemHandlerHelper.insertItem(destination, stack.copy(), true).getCount() < stack.getCount()) return true;
		}
		return false;
	}
	public void deposit(IItemHandler destination) {
		for (int i = 0; i < 10; i++) {
			ItemStack stack = this.get(i);
			if (!stack.isEmpty() && this.rat.shouldDepositItem(stack)) this.set(i, ItemHandlerHelper.insertItem(destination, stack.copy(), false));
		}
		this.promote();
	}
	public void promote() {
		if (!this.get(0).isEmpty()) return;
		for (int i = 1; i < 10; i++) if (!this.get(i).isEmpty()) {
			this.set(0, this.get(i));
			this.set(i, ItemStack.EMPTY);
			break;
		}
	}
	public void unloadExtra() {
		for (int i = 1; i < 10; i++) if (!this.get(i).isEmpty()) {
			this.rat.spawnAtLocation(this.get(i).copy());
			this.set(i, ItemStack.EMPTY);
		}
	}
	public void save(CompoundTag tag) {
		CompoundTag cargo = new CompoundTag();
		ContainerHelper.saveAllItems(cargo, this.extra);
		tag.put("SilverTrayCargo", cargo);
	}
	public void load(CompoundTag tag) {
		for (int i = 0; i < this.extra.size(); i++) this.extra.set(i, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(tag.getCompound("SilverTrayCargo"), this.extra);
	}

	public SimpleContainer extraContainer() {
		return new SimpleContainer(9) {
			@Override public ItemStack getItem(int slot) { return RatCargo.this.get(slot + 1); }
			@Override public void setItem(int slot, ItemStack stack) { RatCargo.this.set(slot + 1, stack); this.setChanged(); }
			@Override public ItemStack removeItem(int slot, int count) {
				ItemStack removed = this.getItem(slot).split(count);
				this.setChanged();
				return removed;
			}
			@Override public ItemStack removeItemNoUpdate(int slot) {
				ItemStack previous = this.getItem(slot);
				this.setItem(slot, ItemStack.EMPTY);
				return previous;
			}
			@Override public boolean isEmpty() { return RatCargo.this.extra.stream().allMatch(ItemStack::isEmpty); }
			@Override public void clearContent() { for (int i = 0; i < 9; i++) this.setItem(i, ItemStack.EMPTY); }
		};
	}
}
