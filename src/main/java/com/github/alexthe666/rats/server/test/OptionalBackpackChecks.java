package com.github.alexthe666.rats.server.test;
import com.github.alexthe666.rats.registry.*;
import com.github.alexthe666.rats.server.block.entity.*;
import com.github.alexthe666.rats.server.storage.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.*;
import java.util.UUID;
/** Never discovered or loaded by GameTest when the optional API is absent. */
final class OptionalBackpackChecks {
 private OptionalBackpackChecks() { }
 static void run(GameTestHelper h) {
		var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"Backpack16"));var pos=new BlockPos(8,4,8);h.setBlock(pos,RatsBlockRegistry.RAT_STORAGE_CONTROLLER.get());h.setBlock(pos.east(),RatsBlockRegistry.RAT_STORAGE_INPUT.get());var c=(RatStorageBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pos));c.owner=p.getUUID();h.assertTrue(c.connect(h.absolutePos(pos.east())),"Attach input");var input=(RatStorageBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pos.east()));
		var upgrade=new ItemStack(RatsItemRegistry.RAT_STORAGE_BACKPACK_UPGRADE.get());p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,upgrade);
		var hit=new net.minecraft.world.phys.BlockHitResult(c.getBlockPos().getCenter(),Direction.UP,c.getBlockPos(),false);
		h.assertTrue(c.getBlockState().use(h.getLevel(),p,net.minecraft.world.InteractionHand.MAIN_HAND,hit)==net.minecraft.world.InteractionResult.PASS,"Controller does not swallow upgrade binding with its GUI");
		h.assertTrue(upgrade.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(p,net.minecraft.world.InteractionHand.MAIN_HAND,hit)).consumesAction(),"Native upgrade binds through actual right-click handler");
		var tag=upgrade.getOrCreateTag();h.assertTrue(tag.getUUID("RatNetwork").equals(c.network)&&tag.getUUID("RatOwner").equals(p.getUUID()),"Binding writes authoritative network and owner");
		var backpack=new net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper(new ItemStack(net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems.BACKPACK.get()));var wrapper=new com.github.alexthe666.rats.server.compat.backpacks.RatBackpackWrapper(backpack,upgrade,s->{});h.assertTrue(wrapper.incoming.getSlots()==9,"Fresh native upgrade has nine real slots");
		for(int i=0;i<54;i++)input.setItem(i,new ItemStack(Items.STONE,64));input.setItem(0,new ItemStack(Items.DIAMOND,60));wrapper.incoming.setStackInSlot(0,new ItemStack(Items.DIAMOND,12));wrapper.send(p);h.assertTrue(wrapper.incoming.getStackInSlot(0).getCount()==8&&input.getItem(0).getCount()==64,"Exactly four deposited and eight retained when input full");
		var restored=new com.github.alexthe666.rats.server.compat.backpacks.RatBackpackWrapper(backpack,upgrade,s->{});h.assertTrue(restored.incoming.getStackInSlot(0).getCount()==8,"Remainder survives wrapper recreation");var container=new com.github.alexthe666.rats.server.compat.backpacks.RatBackpackContainer(p,819,wrapper,com.github.alexthe666.rats.server.compat.backpacks.RatBackpackContainer.TYPE);container.setUpgradeWrapper(restored);container.getSlots().get(0).set(new ItemStack(Items.DIAMOND,5));h.assertTrue(restored.incoming.getStackInSlot(0).getCount()==5,"Replaced wrapper is actual slot destination");
		tag.putUUID("RatOwner",UUID.randomUUID());input.setItem(1,ItemStack.EMPTY);restored.send(p);h.assertTrue(restored.incoming.getStackInSlot(0).getCount()==5&&input.getItem(1).isEmpty(),"Different owner cannot send to another network");h.succeed();
 }
}
