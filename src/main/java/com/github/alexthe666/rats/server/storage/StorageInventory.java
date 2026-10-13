package com.github.alexthe666.rats.server.storage;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** Never mutate a stack obtained from a third-party handler. */
public final class StorageInventory {
	private StorageInventory() { }
	public static boolean same(ItemStack a, ItemStack b) { return !a.isEmpty() && !b.isEmpty() && ItemStack.isSameItemSameTags(a, b); }
	public static int capacity(IItemHandler handler, ItemStack stack, int maximum) {
		int left = maximum;
		for (int slot = 0; slot < handler.getSlots() && left > 0; slot++) {
			// Simulation may describe several parcels; actual rat stacks stay normal.
			int trial = left;
			left -= trial - handler.insertItem(slot, stack.copyWithCount(trial), true).getCount();
		}
		return maximum - left;
	}
	public static ItemStack insert(IItemHandler handler, ItemStack input, boolean simulate) {
		ItemStack remaining = input.copy();
		for (int pass = 0; pass < 2; pass++) for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
			ItemStack held = handler.getStackInSlot(slot);
			if (pass == 0 && !same(held, input) || pass == 1 && !held.isEmpty()) continue;
			remaining = handler.insertItem(slot, remaining, simulate);
		}
		return remaining;
	}
	public static long count(IItemHandler handler, ItemStack sample) {
		long count = 0;
		for (int i = 0; i < handler.getSlots(); i++) if (same(handler.getStackInSlot(i), sample)) count += handler.getStackInSlot(i).getCount();
		return count;
	}
	public static long extractableCount(IItemHandler handler, ItemStack sample) {
		long count = 0; for (int i = 0; i < handler.getSlots(); i++) if (same(handler.getStackInSlot(i), sample) && same(handler.extractItem(i, 1, true), sample)) count += handler.getStackInSlot(i).getCount(); return count;
	}
	public static ItemStack extract(IItemHandler handler, ItemStack sample, int maximum) {
		ItemStack result = ItemStack.EMPTY;
		for (int i = 0; i < handler.getSlots() && result.getCount() < maximum; i++) {
			if (!same(handler.getStackInSlot(i), sample)) continue;
			while (result.getCount() < maximum) {
			ItemStack trial = handler.extractItem(i, maximum - result.getCount(), true);
			if (!same(trial, sample)) break;
			ItemStack actual = handler.extractItem(i, Math.min(trial.getCount(), maximum - result.getCount()), false);
			if (actual.isEmpty()) break;
			if (result.isEmpty()) result = actual.copy(); else result.grow(actual.getCount());
			}
		}
		return result;
	}
}
