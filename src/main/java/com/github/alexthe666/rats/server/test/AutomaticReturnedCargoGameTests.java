package com.github.alexthe666.rats.server.test;

import com.github.alexthe666.rats.registry.*;
import com.github.alexthe666.rats.server.block.entity.RatStorageBlockEntity;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.inventory.RatStorageMenu;
import com.github.alexthe666.rats.server.storage.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("rats") @PrefixGameTestTemplate(false)
public class AutomaticReturnedCargoGameTests {
    private static RatStorageBlockEntity controller(GameTestHelper h) {
        var p=new BlockPos(10,4,10);h.setBlock(p,RatsBlockRegistry.RAT_STORAGE_CONTROLLER.get());
        return (RatStorageBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(p));
    }
    private static BlockPos chest(GameTestHelper h,RatStorageBlockEntity c) {
        for(int x=11;x<=15;x++)h.setBlock(new BlockPos(x,4,10),RatsBlockRegistry.RAT_TUBE_COLOR.get());
        h.setBlock(new BlockPos(16,4,10),Blocks.CHEST);var p=h.absolutePos(new BlockPos(16,4,10));h.assertTrue(c.connect(p),"Chest connected");return p;
    }
    private static TamedRat worker(GameTestHelper h,RatStorageBlockEntity c,boolean tray) {
        var r=h.spawn(RatsEntityRegistry.TAMED_RAT.get(),new BlockPos(10,4,10));r.setTame(true);
        r.setItemSlot(EquipmentSlot.CHEST,new ItemStack(RatsItemRegistry.RAT_UPGRADE_STORAGE_KEEPER.get()));
        if(tray)r.setItemSlot(EquipmentSlot.LEGS,new ItemStack(RatsItemRegistry.RAT_UPGRADE_SILVER_TRAY.get()));
        h.assertTrue(c.assign(r),"Assigned worker");return r;
    }
    private static ItemStack parcel(String name,int count) {
        var item=new ItemStack(Items.DIAMOND,count);item.getOrCreateTag().putString("ReturnedParcel",name);return item;
    }
    @GameTest(template="quarry_test_empty")
    public static void savedReturnedCargoIsCataloguedAndReservedWithoutMigration(GameTestHelper h) {
        var c=controller(h);var a=parcel("A",17);var b=parcel("B",9);c.setItem(0,a.copy());c.setItem(1,b.copy());
        c.load(c.saveWithoutMetadata());var entries=StorageStock.scan(c);
        h.assertTrue(entries.size()==2&&entries.stream().mapToLong(StorageStock.Entry::available).sum()==26,"Old saved cargo is immediately available with distinct NBT variants");
        var p=chest(h,c);var one=worker(h,c,false);var two=worker(h,c,false);one.setNoAi(true);two.setNoAi(true);
        var task=StorageScheduler.claim(c,one);h.assertTrue(task!=null&&task.source.equals(c.getBlockPos())&&task.destination.equals(p)&&task.maximum==17,"Saved cargo becomes a real rat task without an input module");
        h.assertTrue(StorageScheduler.claim(c,two)==null,"Different NBT key cannot overbook the same destination concurrently");
        h.assertTrue(c.getItem(0).getCount()==17&&c.getItem(1).getCount()==9,"Reserving never consumes physical items");h.succeed();
    }
    @GameTest(template="quarry_test_empty",timeoutTicks=1200)
    public static void returnedMixedTrayIsAutomaticallyFiledWithoutInput(GameTestHelper h) {
        var c=controller(h);var p=chest(h,c);c.setItem(0,new ItemStack(Items.COBBLESTONE,23));c.setItem(1,new ItemStack(Items.STONE,11));
        var r=worker(h,c,true);var handler=StorageRoutes.handler(h.getLevel(),p,Direction.WEST);
        h.succeedWhen(()->{h.assertTrue(c.isEmpty()&&r.cargo.isEmpty()&&c.task(r)==null&&StorageInventory.count(handler,new ItemStack(Items.COBBLESTONE))==23&&StorageInventory.count(handler,new ItemStack(Items.STONE))==11,"Real worker files both returned keys, preserving quantities");
            h.assertTrue(StorageStock.scan(c).stream().mapToLong(StorageStock.Entry::stored).sum()==34,"Catalogue does not duplicate cargo after it reaches the chest");r.setCommand(com.github.alexthe666.rats.server.entity.rat.RatCommand.SIT);});
    }
    @GameTest(template="quarry_test_empty",timeoutTicks=600)
    public static void fullChestRetainsCargoThenAutomaticallyResumes(GameTestHelper h) {
        var c=controller(h);var p=chest(h,c);var handler=StorageRoutes.handler(h.getLevel(),p,Direction.WEST);
        for(int i=0;i<handler.getSlots();i++)handler.insertItem(i,new ItemStack(Items.STONE,64),false);
        var item=parcel("full",12);c.setItem(0,item.copy());var r=worker(h,c,false);
        h.runAfterDelay(60,()->{h.assertTrue(StorageInventory.count(c.inventory(),item)==12&&r.cargo.isEmpty()&&c.task(r)==null,"Full chest cannot consume or loop a returned parcel");
            h.assertTrue(StorageStock.scan(c).stream().filter(e->StorageInventory.same(e.item(),item)).mapToLong(StorageStock.Entry::available).sum()==12,"Waiting cargo remains visible in the normal catalogue");handler.extractItem(0,64,false);});
        h.succeedWhen(()->h.assertTrue(c.isEmpty()&&r.cargo.isEmpty()&&c.task(r)==null&&StorageInventory.count(handler,item)==12,"Freeing real capacity resumes automatically without clicking organize"));
    }
    @GameTest(template="quarry_test_empty",timeoutTicks=400)
    public static void normalRequestCanWithdrawReturnedCargoWithoutAnyChest(GameTestHelper h) {
        var c=controller(h);var output=new BlockPos(10,4,11);h.setBlock(output,RatsBlockRegistry.RAT_STORAGE_OUTPUT.get());h.assertTrue(c.connect(h.absolutePos(output)),"Output connected");
        var item=parcel("output",13);c.setItem(0,item.copy());h.assertTrue(c.request(UUID.randomUUID(),item,13,h.absolutePos(output),-1),"Normal controller request can use returned stock immediately");
        var r=worker(h,c,false);var out=(RatStorageBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(output));
        h.succeedWhen(()->h.assertTrue(c.isEmpty()&&r.cargo.isEmpty()&&c.requests.isEmpty()&&StorageInventory.count(out.inventory(),item)==13,"Exact NBT parcel reaches output without a manual recovered-cargo UI"));
    }
    @GameTest(template="quarry_test_empty")
    public static void systemMenuHasNoInternalCargoSlotsAndCraftingIndicesStillWork(GameTestHelper h) {
        var c=controller(h);var item=parcel("hidden",7);c.setItem(0,item.copy());
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ReturnedUI"));player.setPos(c.getBlockPos().getCenter());
        var menu=new RatStorageMenu(918,player.getInventory(),c);h.assertTrue(menu.slots.size()==55&&menu.slots.stream().noneMatch(s->s.container==c),"Controller storage has no exposed slots, only craft/legacy receipt/player slots");
        player.getInventory().setItem(9,new ItemStack(Items.STONE,8));menu.page=2;h.assertTrue(menu.quickMoveStack(player,19).isEmpty()&&c.getItem(0).getCount()==7,"System-tab shift click cannot insert into hidden storage");
        menu.clicked(19,0,ClickType.PICKUP,player);h.assertTrue(menu.getCarried().is(Items.STONE)&&menu.getCarried().getCount()==8&&c.getItem(0).getCount()==7,"Slot 19 is the real player inventory, not the old recovery slot");menu.clicked(19,0,ClickType.PICKUP,player);
        menu.page=0;for(int index:new int[]{1,2,4,5})menu.slots.get(index).set(new ItemStack(Items.OAK_PLANKS));menu.slotsChanged(menu.slots.get(1).container);
        h.assertTrue(menu.slots.get(0).getItem().is(Items.CRAFTING_TABLE),"Crafting recipe still matches");menu.quickMoveStack(player,0);
        h.assertTrue(player.getInventory().countItem(Items.CRAFTING_TABLE)==1&&StorageInventory.count(c.inventory(),item)==7,"Crafting uses new player slot range without touching returned items");h.succeed();
    }
    @GameTest(template="quarry_test_empty")
    public static void concurrentWorkersReserveReturnedSourceOnlyOnce(GameTestHelper h) {
        var c=controller(h);chest(h,c);var item=parcel("reserve",64);c.setItem(0,item);c.setItem(1,item.copyWithCount(36));
        var a=worker(h,c,false);var b=worker(h,c,false);var d=worker(h,c,false);a.setNoAi(true);b.setNoAi(true);d.setNoAi(true);
        var first=StorageScheduler.claim(c,a);var second=StorageScheduler.claim(c,b);
        h.assertTrue(first!=null&&second!=null&&first.maximum==64&&second.maximum==36&&first.destination.equals(second.destination),"Source reservations consolidate exact 64+36 returned items");
        h.assertTrue(StorageScheduler.claim(c,d)==null&&StorageInventory.count(c.inventory(),item)==100,"No duplicate extraction reservation from the controller");h.succeed();
    }
}
