package com.github.alexthe666.rats.server.test;

import com.github.alexthe666.rats.registry.*;
import com.github.alexthe666.rats.server.entity.ai.goal.*;
import com.github.alexthe666.rats.server.entity.rat.*;
import com.github.alexthe666.rats.server.misc.TubeTravel;
import com.github.alexthe666.rats.server.storage.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder("rats") @PrefixGameTestTemplate(false)
public class TubeModeRegressionGameTests {
    private static TamedRat fisherman(GameTestHelper h, BlockPos pos) {
        var r=h.spawn(RatsEntityRegistry.TAMED_RAT.get(),pos);r.setTame(true);
        r.setItemSlot(EquipmentSlot.CHEST,new ItemStack(RatsItemRegistry.RAT_UPGRADE_FISHERMAN.get()));
        r.setItemSlot(EquipmentSlot.LEGS,new ItemStack(RatsItemRegistry.RAT_UPGRADE_PLATTER.get()));
        r.setCommand(RatCommand.HARVEST);return r;
    }
    @GameTest(template="quarry_test_empty")
    public static void carryingItemsAndBindingsDoNotAuthorizeHarvestTubes(GameTestHelper h) {
        var p=new BlockPos(8,4,8);h.setBlock(p,Blocks.CHEST);h.setBlock(p.east(),RatsBlockRegistry.RAT_TUBE_COLOR.get());
        var r=fisherman(h,p.west(2));r.setNoAi(true);r.setDepositPos(GlobalPos.of(h.getLevel().dimension(),h.absolutePos(p)));
        r.setPickupPos(GlobalPos.of(h.getLevel().dimension(),h.absolutePos(p)));r.cargo.insert(new ItemStack(Items.COD,5),false);
        for(var command:RatCommand.values()) {
            r.setCommand(command);
            h.assertTrue(TubeTravel.usesTransportTubes(r)==(command==RatCommand.TRANSPORT),"Only TRANSPORT seeks tubes, even with cargo and two bindings: "+command);
            h.assertTrue(TubeTravel.authorized(r)==(command==RatCommand.TRANSPORT),"Outside unrelated rats cannot enter tubes: "+command);
        }
        r.setCommand(RatCommand.HARVEST);
        h.assertTrue(TubeTravel.inventoryFace(r,h.absolutePos(p),Direction.UP)==Direction.UP,"Harvester preserves configured deposit face instead of adopting tube face");
        r.getPersistentData().putUUID("RatStorageNetwork",java.util.UUID.randomUUID());
        h.assertTrue(TubeTravel.usesTransportTubes(r),"Explicit storage assignment remains an exception");h.succeed();
    }
    @GameTest(template="quarry_test_empty")
    public static void fisherDepositsOnFootEvenWhenChestHasTube(GameTestHelper h) {
        var p=new BlockPos(8,4,8);h.setBlock(p,Blocks.CHEST);h.setBlock(p.east(),RatsBlockRegistry.RAT_TUBE_COLOR.get());
        var r=fisherman(h,p.west());r.setNoAi(true);r.setDepositPos(GlobalPos.of(h.getLevel().dimension(),h.absolutePos(p)));r.cargo.insert(new ItemStack(Items.COD,5),false);
        var before=r.position();var goal=new RatDepositGoal(r,RatDepositGoal.DepositType.INVENTORY);
        h.assertTrue(goal.canUse(),"Fisher can deposit partial catch");goal.start();goal.tick();
        var inventory=StorageRoutes.handler(h.getLevel(),h.absolutePos(p),Direction.UP);
        h.assertTrue(r.cargo.isEmpty()&&StorageInventory.count(inventory,new ItemStack(Items.COD))==5,"Deposits directly rather than setting off toward tube");
        h.assertTrue(r.position().equals(before)&&!r.isInTube(),"No manual tube movement during deposit");goal.stop();h.succeed();
    }
    @GameTest(template="quarry_test_empty",timeoutTicks=3200)
    public static void oldTrappedFisherLeavesTubeAndActuallyFishes(GameTestHelper h) {
        for(int x=4;x<=12;x++)for(int z=4;z<=12;z++)h.setBlock(new BlockPos(x,3,z),Blocks.STONE);
        h.setBlock(new BlockPos(9,3,8),Blocks.WATER);var chest=new BlockPos(6,4,8);
        h.setBlock(chest,Blocks.CHEST);h.setBlock(chest.west(),RatsBlockRegistry.RAT_TUBE_COLOR.get());
        var start=chest.west();var r=fisherman(h,start);r.setPos(TubeTravel.point(h.getLevel(),h.absolutePos(start)));
        r.setDepositPos(GlobalPos.of(h.getLevel().dimension(),h.absolutePos(chest)));boolean[] left={false};
        h.onEachTick(()->{if(!r.isInTube())left[0]=true;else if(left[0])h.fail("Fisher reentered tube after recovery");});
        h.succeedWhen(()->{var inventory=StorageRoutes.handler(h.getLevel(),h.absolutePos(chest),Direction.UP);boolean caught=false;
            for(int i=0;i<inventory.getSlots();i++)if(!inventory.getStackInSlot(i).isEmpty())caught=true;
            h.assertTrue(left[0]&&caught,"Full AI escapes, finishes a cast and deposits fishing loot: "+r.getWorkDebugState());r.setCommand(RatCommand.SIT);});
    }
    @GameTest(template="quarry_test_empty")
    public static void blockedTubeRecoveryPreservesCargoAndRespectsCommandChange(GameTestHelper h) {
        var p=new BlockPos(8,4,8);h.setBlock(p,RatsBlockRegistry.RAT_TUBE_COLOR.get());
        for(var d:Direction.values())h.setBlock(p.relative(d),Blocks.STONE);
        var r=fisherman(h,p);r.setNoAi(true);r.setPos(TubeTravel.point(h.getLevel(),h.absolutePos(p)));
        var parcel=new ItemStack(Items.DIAMOND,7);parcel.getOrCreateTag().putString("Recovery","keep");r.cargo.insert(parcel,false);
        var goal=new RatLeaveWorkTubeGoal(r);var before=r.position();h.assertTrue(!goal.canUse(),"No teleport through sealed walls");
        h.assertTrue(r.position().equals(before)&&r.cargo.get(0).getCount()==7,"Blocked recovery retains position and items");
        h.setBlock(p.east(),Blocks.AIR);h.setBlock(p.east().below(),Blocks.STONE);r.tickCount+=40;
        h.setBlock(p.west(),RatsBlockRegistry.RAT_CAGE.get());
        h.assertTrue(!goal.canUse(),"Recovery must not forcibly release a resident through a cage-connected tube");
        h.setBlock(p.west(),Blocks.STONE);r.tickCount+=40;
        h.assertTrue(goal.canUse(),"Loaded safe opening allows recovery");goal.start();
        h.setBlock(new BlockPos(12,4,8),Blocks.CHEST);r.setDepositPos(GlobalPos.of(h.getLevel().dimension(),h.absolutePos(new BlockPos(12,4,8))));
        r.setCommand(RatCommand.TRANSPORT);h.assertTrue(!goal.canContinueToUse(),"Switching to valid transport stops recovery from stealing its route");goal.stop();
        r.setCommand(RatCommand.HARVEST);r.tickCount+=40;h.assertTrue(goal.canUse(),"Harvest recovery restarts");goal.start();
        for(int i=0;i<100&&goal.canContinueToUse();i++){r.tickCount++;goal.tick();}goal.stop();
        h.assertTrue(!r.isInTube()&&!r.noPhysics&&!r.isNoGravity()&&ItemStack.isSameItemSameTags(r.cargo.get(0),parcel)&&r.cargo.get(0).getCount()==7,"Walked out, restored normal physics, kept exact NBT and quantity");h.succeed();
    }
}
