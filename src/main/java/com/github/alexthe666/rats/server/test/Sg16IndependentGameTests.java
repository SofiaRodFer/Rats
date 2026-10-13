package com.github.alexthe666.rats.server.test;

import com.github.alexthe666.rats.registry.*;
import com.github.alexthe666.rats.server.block.entity.RatStorageBlockEntity;
import com.github.alexthe666.rats.server.entity.ai.goal.RatDepositGoal;
import com.github.alexthe666.rats.server.entity.rat.*;
import com.github.alexthe666.rats.server.misc.*;
import com.github.alexthe666.rats.server.storage.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Independent regressions exercise real goals, Forge saved tickets and optional wrappers. */
@GameTestHolder("rats") @PrefixGameTestTemplate(false)
public class Sg16IndependentGameTests {
    private static TamedRat rat(GameTestHelper h, BlockPos relative) {
        var rat=h.spawn(RatsEntityRegistry.TAMED_RAT.get(),relative);
        rat.setTame(true);rat.setNoAi(true);return rat;
    }
    private static void verticalLine(GameTestHelper h,BlockPos from,int distance) {
        // A tall column stays in the template's X/Z footprint and cannot
        // intersect another concurrently running test's neighboring structure.
        for(int y=0;y<=distance;y++)h.getLevel().setBlockAndUpdate(from.above(y),RatsBlockRegistry.RAT_TUBE_COLOR.get().defaultBlockState());
    }
    @GameTest(template="quarry_test_empty")
    public static void genericTubeRouteExceedsSixtyFourWithoutChangingStorageRadius(GameTestHelper h) {
        BlockPos from=h.absolutePos(new BlockPos(8,4,8)),to=from.above(80);
        verticalLine(h,from,80);
        try {
            var unlimited=TubeTravel.route(h.getLevel(),from,from,to,-1);
            h.assertTrue(unlimited.size()==80&&unlimited.get(79).equals(to),"Generic loaded tube reaches eighty blocks vertically");
            h.assertTrue(TubeTravel.route(h.getLevel(),from,from,to,20).isEmpty(),"Storage's explicit bounded radius still rejects a distant tube");
        } finally {
            for(int y=0;y<=80;y++)h.getLevel().setBlockAndUpdate(from.above(y),Blocks.AIR.defaultBlockState());
        }
        h.succeed();
    }
    @GameTest(template="quarry_test_empty")
    public static void longGenericDepositWaitsForBrokenTubeThenResumesExactTaggedCargo(GameTestHelper h) {
        BlockPos from=h.absolutePos(new BlockPos(8,4,8)),broken=from.above(40),chest=from.above(81);
        verticalLine(h,from,80);h.getLevel().setBlockAndUpdate(chest,Blocks.CHEST.defaultBlockState());
        var worker=rat(h,new BlockPos(8,4,8));worker.setPos(TubeTravel.point(h.getLevel(),from));
        worker.setItemSlot(EquipmentSlot.CHEST,new ItemStack(RatsItemRegistry.RAT_UPGRADE_PLATTER.get()));
        worker.setDepositPos(GlobalPos.of(h.getLevel().dimension(),chest));worker.depositFacing=Direction.DOWN;worker.setCommand(RatCommand.TRANSPORT);
        var parcel=new ItemStack(Items.DIAMOND,17);parcel.getOrCreateTag().putString("IndependentParcel","vertical80");worker.cargo.insert(parcel,false);
        var goal=new RatDepositGoal(worker,RatDepositGoal.DepositType.INVENTORY);
        try {
            h.assertTrue(goal.canUse(),"Configured generic transport begins with a partial tagged parcel");goal.start();
            for(int tick=0;tick<300&&worker.getY()<from.getY()+30;tick++){worker.tickCount++;goal.tick();}
            h.assertTrue(worker.getY()>=from.getY()+30,"Goal actually traverses vertical tubes before disruption");
            h.getLevel().setBlockAndUpdate(broken,Blocks.AIR.defaultBlockState());
            h.getLevel().setBlockAndUpdate(broken.above(),Blocks.AIR.defaultBlockState());
            for(int tick=0;tick<180;tick++){worker.tickCount++;goal.tick();}
            h.assertTrue(worker.getY()<broken.getY()&&worker.cargo.get(0).getCount()==17,"Broken unsupported shaft blocks progress and retains all cargo");
            h.getLevel().setBlockAndUpdate(broken,RatsBlockRegistry.RAT_TUBE_COLOR.get().defaultBlockState());
            h.getLevel().setBlockAndUpdate(broken.above(),RatsBlockRegistry.RAT_TUBE_COLOR.get().defaultBlockState());
            for(int tick=0;tick<1600&&goal.canContinueToUse();tick++){worker.tickCount++;goal.tick();}
            var destination=StorageRoutes.handler(h.getLevel(),chest,Direction.DOWN);
            h.assertTrue(worker.cargo.isEmpty()&&StorageInventory.count(destination,parcel)==17,"Same goal resumes beyond sixty-four blocks and delivers tagged cargo exactly once");
            h.assertTrue(destination.getStackInSlot(0).getTag().getString("IndependentParcel").equals("vertical80"),"Transport preserves item metadata");
        } finally {
            goal.stop();worker.setCommand(RatCommand.SIT);worker.discard();
            for(int y=0;y<=81;y++)h.getLevel().setBlockAndUpdate(from.above(y),Blocks.AIR.defaultBlockState());
        }
        h.succeed();
    }
    private static Set<Long> actualTickingTickets(GameTestHelper h,UUID worker) {
        var data=h.getLevel().getDataStorage().computeIfAbsent(ForcedChunksSavedData::load,ForcedChunksSavedData::new,"chunks");
        var saved=data.save(new CompoundTag());Set<Long> chunks=new HashSet<>();
        for(var entry:saved.getList("ForgeForced",Tag.TAG_COMPOUND)) {
            var mod=(CompoundTag)entry;if(!mod.getString("Mod").equals("rats"))continue;
            for(var value:mod.getList("ModForced",Tag.TAG_COMPOUND)) {
                var chunk=(CompoundTag)value;
                for(var owner:chunk.getList("TickingEntities",Tag.TAG_INT_ARRAY))if(NbtUtils.loadUUID(owner).equals(worker))chunks.add(chunk.getLong("Chunk"));
            }
        }
        return chunks;
    }
    @GameTest(template="quarry_test_empty")
    public static void forgeTicketsActuallySlideAndReleasePerWorker(GameTestHelper h) {
        var worker=rat(h,new BlockPos(8,4,8));worker.setCommand(RatCommand.HARVEST);
        worker.setItemSlot(EquipmentSlot.CHEST,new ItemStack(RatsItemRegistry.RAT_UPGRADE_FISHERMAN.get()));
        try {
            var first=RatWorkChunks.desired(worker);RatWorkChunks.update(worker);
            h.assertTrue(first.size()==9&&actualTickingTickets(h,worker.getUUID()).equals(first),"Forge records nine ticking entity tickets, not just a desired calculation");
            worker.setPos(worker.position().add(48,0,0));var next=RatWorkChunks.desired(worker);RatWorkChunks.update(worker);
            h.assertTrue(next.size()==9&&Collections.disjoint(first,next)&&actualTickingTickets(h,worker.getUUID()).equals(next),"Sliding window removes every obsolete Forge ticket without growing beyond nine");
            worker.setCommand(RatCommand.SIT);RatWorkChunks.update(worker);
            h.assertTrue(actualTickingTickets(h,worker.getUUID()).isEmpty(),"Stopping work removes persisted ticking tickets");
            worker.setCommand(RatCommand.HARVEST);RatWorkChunks.update(worker);RatWorkChunks.release(worker);RatWorkChunks.release(worker);
            h.assertTrue(actualTickingTickets(h,worker.getUUID()).isEmpty(),"Explicit release is idempotent after reactivation");
        } finally {RatWorkChunks.release(worker);worker.setCommand(RatCommand.SIT);worker.discard();}
        h.succeed();
    }
    @GameTest(template="quarry_test_empty")
    public static void pendingReservationsConsolidateButKeepDifferentNbtSeparate(GameTestHelper h) {
        h.setBlock(new BlockPos(10,4,10),RatsBlockRegistry.RAT_STORAGE_CONTROLLER.get());
        var c=(RatStorageBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(10,4,10)));
        h.setBlock(new BlockPos(10,4,9),RatsBlockRegistry.RAT_STORAGE_INPUT.get());
        var input=(RatStorageBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(10,4,9)));h.assertTrue(c.connect(input.getBlockPos()),"Input linked");
        for(int x=11;x<=15;x++)h.setBlock(new BlockPos(x,4,10),RatsBlockRegistry.RAT_TUBE_COLOR.get());
        h.setBlock(new BlockPos(16,4,10),Blocks.CHEST);var first=h.absolutePos(new BlockPos(16,4,10));h.assertTrue(c.connect(first),"First chest linked");
        for(int z=11;z<=12;z++)h.setBlock(new BlockPos(15,4,z),RatsBlockRegistry.RAT_TUBE_COLOR.get());
        h.setBlock(new BlockPos(16,4,12),Blocks.CHEST);var second=h.absolutePos(new BlockPos(16,4,12));h.assertTrue(c.connect(second),"Alternative chest linked");
        var parcel=new ItemStack(Items.DIAMOND,64);parcel.getOrCreateTag().putString("ParcelKey","A");
        input.setItem(0,parcel.copy());input.setItem(1,parcel.copyWithCount(36));
        var one=rat(h,new BlockPos(10,4,10));var two=rat(h,new BlockPos(10,4,10));var three=rat(h,new BlockPos(10,4,10));
        for(var worker:List.of(one,two,three))worker.setItemSlot(EquipmentSlot.CHEST,new ItemStack(RatsItemRegistry.RAT_UPGRADE_STORAGE_KEEPER.get()));
        var a=StorageScheduler.claim(c,one);var b=StorageScheduler.claim(c,two);
        h.assertTrue(a!=null&&b!=null&&a.maximum==64&&b.maximum==36&&a.destination.equals(b.destination),"Two uncollected parcels reserve the same first chest rather than scattering the last thirty-six");
        h.assertTrue(StorageScheduler.claim(c,three)==null&&input.getItem(0).getCount()==64&&input.getItem(1).getCount()==36,"Source reservation prevents a third worker from duplicating either still-physical parcel");
        var differentlyTagged=parcel.copyWithCount(7);differentlyTagged.getOrCreateTag().putString("ParcelKey","B");input.setItem(2,differentlyTagged);
        var distinct=StorageScheduler.claim(c,three);
        h.assertTrue(distinct!=null&&!distinct.destination.equals(a.destination)&&ItemStack.isSameItemSameTags(distinct.item,differentlyTagged),"Competing NBT key uses the other chest while first destination remains reserved");
        h.succeed();
    }
    @GameTest(template="quarry_test_empty")
    public static void nativeBackpackRejectsReplacedNetworkAndMissingController(GameTestHelper h) {
        if(!net.minecraftforge.fml.ModList.get().isLoaded("sophisticatedbackpacks")){
            h.assertTrue(!(RatsItemRegistry.RAT_STORAGE_BACKPACK_UPGRADE.get() instanceof com.github.alexthe666.rats.server.items.upgrades.BaseRatUpgradeItem),"Absent optional mod leaves a safe nonwearable item");h.succeed();return;
        }
        Sg16IndependentBackpackChecks.run(h);
    }
}
