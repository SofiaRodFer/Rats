package com.github.alexthe666.rats.server.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import com.github.alexthe666.rats.server.block.entity.RatStorageBlockEntity;

/** Virtual reservations preserve real handler filters and oversized stack limits. */
public final class StorageCapacityPlan {
    private final IItemHandler inventory;
    private final ItemStack[] keys;
    private final int[] added;
    public StorageCapacityPlan(IItemHandler inventory) {
        this.inventory=inventory;this.keys=new ItemStack[inventory.getSlots()];this.added=new int[inventory.getSlots()];
        for(int slot=0;slot<keys.length;slot++)keys[slot]=inventory.getStackInSlot(slot).copy();
    }
    public static StorageCapacityPlan reserved(RatStorageBlockEntity controller,BlockPos destination,IItemHandler inventory,StorageTask excluding) {
        var plan=new StorageCapacityPlan(inventory);
        for(var task:controller.tasks.values())if(task!=excluding&&task.destination.equals(destination)) {
            if(task.collected)for(var parcel:task.parcels)plan.reserve(parcel,parcel.getCount());
            else plan.reserve(task.item,task.maximum);
        }
        return plan;
    }
    /** Commit a simulated parcel. Never insert into or mutate the live handler. */
    public int reserve(ItemStack sample,int maximum) {
        int left=maximum;
        for(int pass=0;pass<2;pass++)for(int slot=0;slot<keys.length&&left>0;slot++) {
            if(pass==0&&!StorageInventory.same(keys[slot],sample)||pass==1&&!keys[slot].isEmpty())continue;
            int trial=left+added[slot];
            int accepted=trial-inventory.insertItem(slot,sample.copyWithCount(trial),true).getCount();
            int fits=Math.max(0,Math.min(left,accepted-added[slot]));
            if(fits>0){if(keys[slot].isEmpty())keys[slot]=sample.copyWithCount(1);added[slot]+=fits;left-=fits;}
        }
        return maximum-left;
    }
}
