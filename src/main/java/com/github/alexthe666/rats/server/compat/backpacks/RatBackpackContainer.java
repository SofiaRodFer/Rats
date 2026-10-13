package com.github.alexthe666.rats.server.compat.backpacks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.items.SlotItemHandler;
import net.p3pp3rf1y.sophisticatedcore.common.gui.*;
public final class RatBackpackContainer extends UpgradeContainerBase<RatBackpackWrapper,RatBackpackContainer>{
    public static final UpgradeContainerType<RatBackpackWrapper,RatBackpackContainer> TYPE=new UpgradeContainerType<>(RatBackpackContainer::new);
    public RatBackpackContainer(Player player,int id,RatBackpackWrapper wrapper,UpgradeContainerType<RatBackpackWrapper,RatBackpackContainer> type){
        super(player,id,wrapper,type);
        var delegate=new net.minecraftforge.items.IItemHandlerModifiable(){
            public int getSlots(){return 9;}
            public net.minecraft.world.item.ItemStack getStackInSlot(int slot){return getUpgradeWrapper().incoming.getStackInSlot(slot);}
            public void setStackInSlot(int slot,net.minecraft.world.item.ItemStack stack){getUpgradeWrapper().incoming.setStackInSlot(slot,stack);}
            public net.minecraft.world.item.ItemStack insertItem(int slot,net.minecraft.world.item.ItemStack stack,boolean simulate){return getUpgradeWrapper().incoming.insertItem(slot,stack,simulate);}
            public net.minecraft.world.item.ItemStack extractItem(int slot,int amount,boolean simulate){return getUpgradeWrapper().incoming.extractItem(slot,amount,simulate);}
            public int getSlotLimit(int slot){return 64;}
            public boolean isItemValid(int slot,net.minecraft.world.item.ItemStack stack){return true;}
        };
        for(int i=0;i<9;i++)slots.add(new SlotItemHandler(delegate,i,-1000,0){@Override public void setChanged(){super.setChanged();getUpgradeWrapper().send(player);}});
    }
    @Override public void handleMessage(CompoundTag data){/* Slots use vanilla container synchronization; no client-authoritative transfer. */}
}
