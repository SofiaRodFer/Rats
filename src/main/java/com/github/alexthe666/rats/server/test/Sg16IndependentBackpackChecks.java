package com.github.alexthe666.rats.server.test;

import com.github.alexthe666.rats.registry.*;
import com.github.alexthe666.rats.server.block.entity.RatStorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import java.util.UUID;

/** No GameTest annotation: optional API types are verified only after the mod guard. */
final class Sg16IndependentBackpackChecks {
    private Sg16IndependentBackpackChecks() { }
    static void run(GameTestHelper h) {
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"IndependentBackpack"));
        BlockPos relative=new BlockPos(8,4,8),position=h.absolutePos(relative);
        h.setBlock(relative,RatsBlockRegistry.RAT_STORAGE_CONTROLLER.get());h.setBlock(relative.east(),RatsBlockRegistry.RAT_STORAGE_INPUT.get());
        var c=(RatStorageBlockEntity)h.getLevel().getBlockEntity(position);c.owner=player.getUUID();h.assertTrue(c.connect(position.east()),"Native upgrade input linked");
        var input=(RatStorageBlockEntity)h.getLevel().getBlockEntity(position.east());
        var upgrade=new ItemStack(RatsItemRegistry.RAT_STORAGE_BACKPACK_UPGRADE.get());var tag=upgrade.getOrCreateTag();
        tag.putLong("RatController",position.asLong());tag.putString("RatDimension",h.getLevel().dimension().location().toString());tag.putUUID("RatNetwork",c.network);tag.putUUID("RatOwner",player.getUUID());
        var nativePack=new net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper(new ItemStack(net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems.BACKPACK.get()));
        var wrapper=new com.github.alexthe666.rats.server.compat.backpacks.RatBackpackWrapper(nativePack,upgrade,s->{});
        var parcel=new ItemStack(Items.DIAMOND,11);parcel.getOrCreateTag().putString("BackpackKey","retained");wrapper.incoming.setStackInSlot(0,parcel.copy());
        UUID original=c.network;c.network=UUID.randomUUID();wrapper.send(player);
        h.assertTrue(input.isEmpty()&&wrapper.incoming.getStackInSlot(0).getCount()==11,"Controller at same position with a different network cannot receive old linked cargo");
        c.network=original;tag.putString("RatDimension","invalid dimension");wrapper.send(player);
        h.assertTrue(input.isEmpty()&&wrapper.incoming.getStackInSlot(0).getCount()==11,"Malformed dimension refuses transfer without consuming cargo");
        tag.putString("RatDimension",h.getLevel().dimension().location().toString());h.getLevel().setBlockAndUpdate(position,Blocks.AIR.defaultBlockState());wrapper.send(player);
        var restored=new com.github.alexthe666.rats.server.compat.backpacks.RatBackpackWrapper(nativePack,upgrade,s->{});
        h.assertTrue(input.isEmpty()&&restored.incoming.getStackInSlot(0).getCount()==11&&ItemStack.isSameItemSameTags(restored.incoming.getStackInSlot(0),parcel),"Destroyed controller retains all tagged items through wrapper recreation");
        h.succeed();
    }
}
