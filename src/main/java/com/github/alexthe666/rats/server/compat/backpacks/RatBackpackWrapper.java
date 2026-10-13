package com.github.alexthe666.rats.server.compat.backpacks;

import com.github.alexthe666.rats.server.block.RatStorageBlock;
import com.github.alexthe666.rats.server.block.entity.RatStorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ITickableUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

public final class RatBackpackWrapper extends UpgradeWrapperBase<RatBackpackWrapper,RatBackpackUpgrade> implements ITickableUpgrade {
    private boolean transferring;
    public final ItemStackHandler incoming=new ItemStackHandler(9){@Override protected void onContentsChanged(int slot){persist();}};
    public RatBackpackWrapper(IStorageWrapper storage,ItemStack upgrade,Consumer<ItemStack> save){super(storage,upgrade,save);if(upgrade.getOrCreateTag().contains("RatIncoming"))incoming.deserializeNBT(upgrade.getOrCreateTag().getCompound("RatIncoming"));}
    private void persist(){upgrade.getOrCreateTag().put("RatIncoming",incoming.serializeNBT());save();}
    public void send(Player player){
        if(transferring||!(player.level() instanceof ServerLevel current)||!isEnabled())return;
        var tag=upgrade.getOrCreateTag();if(!tag.hasUUID("RatNetwork")||!tag.hasUUID("RatOwner")||!tag.getUUID("RatOwner").equals(player.getUUID()))return;
        var dimension=ResourceLocation.tryParse(tag.getString("RatDimension"));if(dimension==null)return;
        ServerLevel level=current.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,dimension));BlockPos pos=BlockPos.of(tag.getLong("RatController"));
        if(level==null||!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof RatStorageBlockEntity c)||!c.network.equals(tag.getUUID("RatNetwork"))||!player.getUUID().equals(c.owner))return;
        transferring=true;try{
            for(BlockPos endpoint:c.endpoints)if(level.hasChunkAt(endpoint)&&level.getBlockEntity(endpoint) instanceof RatStorageBlockEntity input&&input.kind()==RatStorageBlock.Kind.INPUT&&c.network.equals(input.controllerNetwork)&&pos.equals(input.controller)&&pos.distManhattan(endpoint)==1){
                for(int i=0;i<incoming.getSlots();i++){ItemStack held=incoming.getStackInSlot(i);if(!held.isEmpty())incoming.setStackInSlot(i,ItemHandlerHelper.insertItemStacked(input.inventory(),held.copy(),false));}
            }
        }finally{transferring=false;}
    }
    @Override public void tick(@Nullable Entity entity,Level level,BlockPos pos){if(!level.isClientSide&&level.getGameTime()%20==0&&entity instanceof Player player)send(player);}
}
