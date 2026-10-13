package com.github.alexthe666.rats.server.test;

import com.github.alexthe666.rats.registry.*;
import com.github.alexthe666.rats.server.block.entity.RatStorageBlockEntity;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.storage.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import net.minecraftforge.items.ItemStackHandler;

@GameTestHolder("rats") @PrefixGameTestTemplate(false)
public class StorageCapacityReviewGameTests {
    private static RatStorageBlockEntity controller(GameTestHelper h){
        h.setBlock(new BlockPos(10,4,10),RatsBlockRegistry.RAT_STORAGE_CONTROLLER.get());
        return (RatStorageBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(10,4,10)));
    }
    private static BlockPos chest(GameTestHelper h,RatStorageBlockEntity c){
        for(int x=11;x<=15;x++)h.setBlock(new BlockPos(x,4,10),RatsBlockRegistry.RAT_TUBE_COLOR.get());
        h.setBlock(new BlockPos(16,4,10),Blocks.CHEST);var p=h.absolutePos(new BlockPos(16,4,10));h.assertTrue(c.connect(p),"Chest connected");return p;
    }
    private static RatStorageBlockEntity input(GameTestHelper h,RatStorageBlockEntity c){
        h.setBlock(new BlockPos(10,4,9),RatsBlockRegistry.RAT_STORAGE_INPUT.get());var p=h.absolutePos(new BlockPos(10,4,9));h.assertTrue(c.connect(p),"Input connected");return (RatStorageBlockEntity)h.getLevel().getBlockEntity(p);
    }
    private static TamedRat worker(GameTestHelper h,RatStorageBlockEntity c){
        var rat=h.spawn(RatsEntityRegistry.TAMED_RAT.get(),new BlockPos(10,4,10));rat.setTame(true);rat.setItemSlot(EquipmentSlot.CHEST,new ItemStack(RatsItemRegistry.RAT_UPGRADE_STORAGE_KEEPER.get()));h.assertTrue(c.assign(rat),"Worker assigned");return rat;
    }
    @GameTest(template="quarry_test_empty") public static void cumulativeCapacityHonorsFiltersAndOversizedSlots(GameTestHelper h){
        var inventory=new ItemStackHandler(2){@Override public int getSlotLimit(int slot){return 256;}@Override protected int getStackLimit(int slot,ItemStack stack){return 256;}@Override public boolean isItemValid(int slot,ItemStack item){return slot==0&&item.is(Items.COBBLESTONE);}};
        var plan=new StorageCapacityPlan(inventory);h.assertTrue(plan.reserve(new ItemStack(Items.COBBLESTONE),192)==192,"Oversized capacity retained");h.assertTrue(plan.reserve(new ItemStack(Items.STONE),64)==0,"Filters retained");h.assertTrue(plan.reserve(new ItemStack(Items.COBBLESTONE),128)==64,"Cumulative reservations consume remaining space");h.assertTrue(inventory.getStackInSlot(0).isEmpty(),"Simulation never mutates real handler");
        var single=new ItemStackHandler(1);var virtual=new StorageCapacityPlan(single);h.assertTrue(virtual.reserve(new ItemStack(Items.COBBLESTONE),1)==1&&virtual.reserve(new ItemStack(Items.STONE),1)==0,"Two item keys cannot reserve the same last slot");h.succeed();
    }
    @GameTest(template="quarry_test_empty") public static void capacityIncludesOtherWorkersPendingAndCollectedParcels(GameTestHelper h){
        var c=controller(h);var destination=c.getBlockPos().east(3);var inventory=new ItemStackHandler(2);
        var pending=new StorageTask(c.getBlockPos(),Direction.UP,destination,Direction.UP,new ItemStack(Items.COBBLESTONE),1,null);
        var collected=new StorageTask(c.getBlockPos(),Direction.UP,destination,Direction.UP,new ItemStack(Items.STONE),1,null);collected.collected=true;collected.parcels=java.util.List.of(new ItemStack(Items.STONE));
        c.tasks.put(java.util.UUID.randomUUID(),pending);c.tasks.put(java.util.UUID.randomUUID(),collected);
        h.assertTrue(StorageCapacityPlan.reserved(c,destination,inventory,null).reserve(new ItemStack(Items.DIRT),1)==0,"Pending and collected different keys reserve both empty slots");
        h.assertTrue(StorageCapacityPlan.reserved(c,destination,inventory,collected).reserve(new ItemStack(Items.DIRT),1)==1,"Excluded current task does not double-reserve its parcel");h.succeed();
    }
    @GameTest(template="quarry_test_empty") public static void mixedChestConsolidationPrecedesEmptyChest(GameTestHelper h){
        var c=controller(h);var first=chest(h,c);var in=input(h,c);StorageInventory.insert(StorageRoutes.handler(h.getLevel(),first,Direction.WEST),new ItemStack(Items.COBBLESTONE),false);c.categories.put(first,"mixed");
        for(int z=11;z<=12;z++)h.setBlock(new BlockPos(15,4,z),RatsBlockRegistry.RAT_TUBE_COLOR.get());h.setBlock(new BlockPos(16,4,12),Blocks.CHEST);h.assertTrue(c.connect(h.absolutePos(new BlockPos(16,4,12))),"Alternative empty chest connected");
        in.setItem(0,new ItemStack(Items.COBBLESTONE,64));var rat=worker(h,c);rat.setNoAi(true);var task=StorageScheduler.claim(c,rat);h.assertTrue(task!=null&&task.destination.equals(first),"Existing mixed chest wins consolidation");h.succeed();
    }
    @GameTest(template="quarry_test_empty",timeoutTicks=1200) public static void silverTrayDoesNotOverbookItsOwnLastSlot(GameTestHelper h){
        var c=controller(h);var p=chest(h,c);var handler=StorageRoutes.handler(h.getLevel(),p,Direction.WEST);
        for(int i=1;i<handler.getSlots();i++)handler.insertItem(i,new ItemStack(Items.DIAMOND,64),false);
        var in=input(h,c);in.setItem(0,new ItemStack(Items.COBBLESTONE));in.setItem(1,new ItemStack(Items.STONE));var rat=worker(h,c);rat.setItemSlot(EquipmentSlot.LEGS,new ItemStack(RatsItemRegistry.RAT_UPGRADE_SILVER_TRAY.get()));
        h.succeedWhen(()->h.assertTrue(StorageInventory.count(handler,new ItemStack(Items.COBBLESTONE))==1&&StorageInventory.count(in.inventory(),new ItemStack(Items.STONE))==1&&rat.cargo.isEmpty()&&c.task(rat)==null,"Only deliverable key leaves input, no destination_full deadlock"));
    }
    @GameTest(template="quarry_test_empty",timeoutTicks=1400) public static void removedDestinationRecoversWithoutLoss(GameTestHelper h){recover(h,false);}
    @GameTest(template="quarry_test_empty",timeoutTicks=1600) public static void removedDestinationKeepsCargoWhenRecoveryFull(GameTestHelper h){recover(h,true);}
    private static void recover(GameTestHelper h,boolean full){
        var c=controller(h);var p=chest(h,c);var in=input(h,c);in.setItem(0,new ItemStack(Items.COBBLESTONE,64));var rat=worker(h,c);boolean[] removed={false},checked={!full};
        h.onEachTick(()->{if(!removed[0]&&!rat.cargo.isEmpty()){removed[0]=true;h.getLevel().setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());if(full){for(int i=0;i<54;i++)c.setItem(i,new ItemStack(Items.STONE,64));h.runAfterDelay(320,()->{h.assertTrue(StorageInventory.count(c.inventory(),new ItemStack(Items.COBBLESTONE))==0&&rat.cargo.get(0).getCount()==64&&c.task(rat)!=null,"Full recovery retains physical parcel and task");checked[0]=true;c.setItem(0,ItemStack.EMPTY);});}}});
        h.succeedWhen(()->h.assertTrue(removed[0]&&checked[0]&&in.isEmpty()&&rat.cargo.isEmpty()&&c.task(rat)==null&&StorageInventory.count(c.inventory(),new ItemStack(Items.COBBLESTONE))==64,"Removed destination returns exact parcel automatically"));
    }
}
