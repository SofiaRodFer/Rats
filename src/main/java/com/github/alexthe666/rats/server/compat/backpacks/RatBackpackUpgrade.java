package com.github.alexthe666.rats.server.compat.backpacks;

import com.github.alexthe666.rats.server.block.RatStorageBlock;
import com.github.alexthe666.rats.server.block.entity.RatStorageBlockEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.p3pp3rf1y.sophisticatedbackpacks.Config;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeItemBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeType;
import java.util.List;

/** Loaded only when Sophisticated Backpacks is present. Binding is server-authoritative. */
public final class RatBackpackUpgrade extends UpgradeItemBase<RatBackpackWrapper> {
    private static final UpgradeType<RatBackpackWrapper> TYPE=new UpgradeType<>(RatBackpackWrapper::new);
    public RatBackpackUpgrade(){super(Config.SERVER.maxUpgradesPerStorage);}
    @Override public UpgradeType<RatBackpackWrapper> getType(){return TYPE;}
    @Override public List<UpgradeConflictDefinition> getUpgradeConflicts(){return List.of();}
    @Override public InteractionResult useOn(UseOnContext context){
        var player=context.getPlayer();var level=context.getLevel();
        if(player==null||!(level.getBlockEntity(context.getClickedPos()) instanceof RatStorageBlockEntity c)||c.kind()!=RatStorageBlock.Kind.CONTROLLER)return InteractionResult.PASS;
        if(!level.isClientSide){if(!player.getUUID().equals(c.owner))return InteractionResult.FAIL;var tag=context.getItemInHand().getOrCreateTag();tag.putLong("RatController",c.getBlockPos().asLong());tag.putString("RatDimension",level.dimension().location().toString());tag.putUUID("RatNetwork",c.network);tag.putUUID("RatOwner",player.getUUID());player.displayClientMessage(net.minecraft.network.chat.Component.translatable("storage.rats.backpack.bound"),true);}
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
