package com.github.alexthe666.rats.server.test;

import com.github.alexthe666.rats.registry.*;
import com.github.alexthe666.rats.server.block.entity.RatFarmingBlockEntity;
import com.github.alexthe666.rats.server.entity.rat.RatCommand;
import com.github.alexthe666.rats.server.inventory.RatCageMenu;
import com.github.alexthe666.rats.server.inventory.RatFarmingMenu;
import com.github.alexthe666.rats.server.misc.TubeTravel;
import com.github.alexthe666.rats.server.storage.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Independent user-contract regressions; deliberately separate from production tests. */
@GameTestHolder("rats")
@PrefixGameTestTemplate(false)
public class IndependentSg14GameTests {
    @GameTest(template="quarry_test_empty")
    public static void farmGhostTemplatesRejectForeignActorAndOutOfRange(GameTestHelper h) {
        h.setBlock(new BlockPos(12,4,20),RatsBlockRegistry.RAT_FARMING.get());
        var f=(RatFarmingBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(12,4,20)));
        f.configure(5,7);
        var owner=h.makeMockPlayer(); owner.setPos(f.getBlockPos().getCenter());
        var stranger=h.makeMockPlayer(); stranger.setUUID(UUID.randomUUID()); stranger.setPos(owner.position());
        var m=new RatFarmingMenu(101,owner.getInventory(),f);
        var seeds=new ItemStack(Items.WHEAT_SEEDS,37); seeds.getOrCreateTag().putString("IndependentMarker","original");
        m.setCarried(seeds);
        m.clicked(346,0,ClickType.PICKUP,stranger);
        h.assertTrue(f.template(0).isEmpty()&&!m.clickMenuButton(stranger,11),"Foreign actor cannot paint or change modes through another player's menu");
        m.clicked(346,0,ClickType.PICKUP,owner);
        h.assertTrue(f.template(0).getCount()==1&&f.template(0).getTag().getString("IndependentMarker").equals("original")&&m.getCarried().getCount()==37,"Template preserves NBT without consuming cursor");
        for(ClickType type:List.of(ClickType.CLONE,ClickType.SWAP,ClickType.THROW,ClickType.QUICK_CRAFT,ClickType.PICKUP_ALL,ClickType.QUICK_MOVE)) m.clicked(346,1,type,owner);
        h.assertTrue(f.template(0).is(Items.WHEAT_SEEDS)&&m.getCarried().getCount()==37,"Alternate clicks cannot extract, overwrite or consume ghost");
        owner.setPos(f.getBlockPos().above(20).getCenter()); m.clicked(346,1,ClickType.PICKUP,owner);
        h.assertTrue(f.template(0).is(Items.WHEAT_SEEDS)&&!m.clickMenuButton(owner,13),"Out-of-range template clear/mode edits rejected");
        h.succeed();
    }

    @GameTest(template="quarry_test_empty")
    public static void farmQuickMoveCannotUseAnotherPlayersMenu(GameTestHelper h) {
        h.setBlock(new BlockPos(12,4,20),RatsBlockRegistry.RAT_FARMING.get());
        var f=(RatFarmingBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(12,4,20)));
        var owner=h.makeMockPlayer();owner.setPos(f.getBlockPos().getCenter());
        var foreign=h.makeMockPlayer();foreign.setUUID(UUID.randomUUID());foreign.setPos(owner.position());
        owner.getInventory().setItem(9,new ItemStack(Items.DIAMOND,17));
        var m=new RatFarmingMenu(102,owner.getInventory(),f);
        h.assertTrue(m.quickMoveStack(foreign,54).isEmpty()&&f.isEmpty()&&owner.getInventory().getItem(9).getCount()==17,"Foreign quick-move cannot transfer the menu viewer's inventory");
        h.succeed();
    }

    @GameTest(template="quarry_test_empty")
    public static void collisionRestoredAfterTubeRemovedUnderSavedCargo(GameTestHelper h) {
        BlockPos p=new BlockPos(6,4,6);h.setBlock(p.below(),Blocks.STONE);h.setBlock(p,RatsBlockRegistry.RAT_TUBE_COLOR.get());
        var r=h.spawn(RatsEntityRegistry.TAMED_RAT.get(),p);r.setTame(true);r.setCommand(RatCommand.SIT);
        r.setItemSlot(EquipmentSlot.LEGS,new ItemStack(RatsItemRegistry.RAT_UPGRADE_SILVER_TRAY.get()));
        // Keep the visible slot occupied: normal cargo promotion legitimately
        // moves a lone last-slot stack to the hand during aiStep.
        r.cargo.set(0,new ItemStack(Items.STONE,11));
        var parcel=new ItemStack(Items.DIAMOND,23);parcel.getOrCreateTag().putString("IndependentParcel","unchanged");r.cargo.set(9,parcel);
        r.aiStep();h.assertTrue(r.noPhysics,"Collision disabled while inside tube");
        var saved=r.saveWithoutId(new CompoundTag());r.discard();
        var restored=RatsEntityRegistry.TAMED_RAT.get().create(h.getLevel());restored.load(saved);h.getLevel().addFreshEntity(restored);
        restored.aiStep();h.assertTrue(restored.noPhysics&&restored.cargo.get(9).getCount()==23&&restored.cargo.get(0).is(Items.STONE)&&restored.cargo.get(0).getCount()==11,"Tube position and both high-slot and visible cargo survive entity reload");
        h.setBlock(p,Blocks.AIR);restored.aiStep();
        h.assertTrue(!restored.noPhysics&&restored.cargo.get(9).getCount()==23&&restored.cargo.get(9).getTag().getString("IndependentParcel").equals("unchanged"),"Breaking occupied tube restores collisions without deleting or changing NBT cargo");
        h.setBlock(p,RatsBlockRegistry.RAT_TUBE_COLOR.get());restored.aiStep();h.assertTrue(restored.noPhysics,"Re-entry restores tube collision mode");
        h.succeed();
    }

    @GameTest(template="quarry_test_empty")
    public static void eachInventoryFaceHasItsOwnVerticalOrHorizontalDock(GameTestHelper h) {
        BlockPos chest=new BlockPos(12,8,12);h.setBlock(chest,Blocks.CHEST);var absolute=h.absolutePos(chest);
        for(Direction d:Direction.values()) {
            h.setBlock(chest.relative(d),RatsBlockRegistry.RAT_TUBE_COLOR.get());
            h.assertTrue(TubeTravel.inventoryFace(h.getLevel(),absolute,d)==d&&TubeTravel.inventoryDock(h.getLevel(),absolute,d).equals(absolute.relative(d)),"Explicit tube face selected: "+d);
            var handler=StorageRoutes.handler(h.getLevel(),absolute,d);
            var parcel=new ItemStack(Items.DIAMOND,7);parcel.getOrCreateTag().putString("DockFace",d.name());
            h.assertTrue(StorageInventory.insert(handler,parcel.copy(),false).isEmpty()&&StorageInventory.count(handler,parcel)==7,"All six connected faces accept exact NBT item: "+d);
            var out=StorageInventory.extract(handler,parcel,7);
            h.assertTrue(out.getCount()==7&&ItemStack.isSameItemSameTags(out,parcel)&&StorageInventory.count(handler,parcel)==0,"Face extraction preserves metadata: "+d);
            h.setBlock(chest.relative(d),Blocks.AIR);
        }
        h.succeed();
    }

    @GameTest(template="quarry_test_empty")
    public static void cageSelectionClearsOnOwnerChangeAndPlayerSlotsStayVisible(GameTestHelper h) {
        BlockPos p=new BlockPos(5,4,5);h.setBlock(p,RatsBlockRegistry.RAT_CAGE.get());
        var viewer=h.makeMockPlayer();viewer.setPos(h.absolutePos(p).getCenter());
        var r=h.spawn(RatsEntityRegistry.TAMED_RAT.get(),p);r.setTame(true);r.setOwnerUUID(viewer.getUUID());r.setNoAi(true);
        var m=new RatCageMenu(103,viewer.getInventory(),h.absolutePos(p));m.action(viewer,0,r.getId(),-1,"");
        h.assertTrue(m.cargoSlots()>0,"Own resident selectable");
        for(int i=13;i<49;i++)h.assertTrue(m.slots.get(i).isActive()&&m.slots.get(i).x>=0&&m.slots.get(i).y>=164,"All player slots visible without inventory tab");
        r.setOwnerUUID(UUID.randomUUID());
        h.assertTrue(m.cargoSlots()==0&&m.slots.subList(0,13).stream().noneMatch(s->s.mayPickup(viewer)||s.mayPlace(new ItemStack(Items.DIAMOND))),"Ownership mutation invalidates selected resident and all edits immediately");
        h.succeed();
    }

    @GameTest(template="quarry_test_empty")
    public static void tubeRouteDetoursButNeverWalksThroughFluidsOrBeyondRadius(GameTestHelper h) {
        for(int x=5;x<=12;x++)for(int z=5;z<=9;z++)h.setBlock(new BlockPos(x,3,z),Blocks.STONE);
        BlockPos from=new BlockPos(5,4,7),to=new BlockPos(12,4,7);
        h.setBlock(from,RatsBlockRegistry.RAT_TUBE_COLOR.get());h.setBlock(to,RatsBlockRegistry.RAT_TUBE_COLOR.get());
        for(int y=4;y<=7;y++)h.setBlock(new BlockPos(8,y,7),Blocks.STONE);
        var path=TubeTravel.route(h.getLevel(),h.absolutePos(from),h.absolutePos(from),h.absolutePos(to),20);
        h.assertTrue(!path.isEmpty()&&!path.contains(h.absolutePos(new BlockPos(8,4,7))),"Safe side detour exists around obstruction");
        for(BlockPos cell:path)h.assertTrue(TubeTravel.passable(h.getLevel(),cell),"Every waypoint is independently passable");
        h.setBlock(new BlockPos(7,4,6),Blocks.LAVA);h.setBlock(new BlockPos(7,4,8),Blocks.WATER);
        h.assertTrue(!TubeTravel.passable(h.getLevel(),h.absolutePos(new BlockPos(7,4,6)))&&!TubeTravel.passable(h.getLevel(),h.absolutePos(new BlockPos(7,4,8))),"Walkable gaps cannot route through lava or water");
        h.assertTrue(TubeTravel.route(h.getLevel(),h.absolutePos(from),h.absolutePos(from),h.absolutePos(to),3).isEmpty(),"Route respects origin radius rather than reaching arbitrary distant destination");
        h.succeed();
    }

    @GameTest(template="quarry_test_empty")
    public static void farmClearsBothHeadroomLayersButNeverDeletesInventory(GameTestHelper h) {
        h.setBlock(new BlockPos(12,4,20),RatsBlockRegistry.RAT_FARMING.get());
        var f=(RatFarmingBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(12,4,20)));f.configure(3,3);
        var r=h.spawn(RatsEntityRegistry.TAMED_RAT.get(),new BlockPos(12,5,20));r.setTame(true);r.setCommand(RatCommand.SIT);
        r.setItemSlot(EquipmentSlot.CHEST,new ItemStack(RatsItemRegistry.RAT_UPGRADE_FARMER.get()));
        int cell=java.util.stream.IntStream.range(0,256).filter(f.layout()::cropCell).findFirst().orElseThrow();var ground=f.ground(cell);
        h.getLevel().setBlockAndUpdate(ground,Blocks.DIRT.defaultBlockState());
        h.getLevel().setBlockAndUpdate(ground.above(),Blocks.OAK_PLANKS.defaultBlockState());
        h.getLevel().setBlockAndUpdate(ground.above(2),Blocks.GLASS.defaultBlockState());
        var one=f.taskAt(cell,r);
        h.assertTrue(one!=null&&one.action()==RatFarmingBlockEntity.Action.CLEAR&&one.target().equals(ground.above()),"First headroom obstacle cleared before second");
        h.getLevel().setBlockAndUpdate(ground.above(),Blocks.AIR.defaultBlockState());var two=f.taskAt(cell,r);
        h.assertTrue(two!=null&&two.action()==RatFarmingBlockEntity.Action.CLEAR&&two.target().equals(ground.above(2)),"Second headroom layer is cleared even for glass, not just terrain");
        h.getLevel().setBlockAndUpdate(ground.above(2),Blocks.CHEST.defaultBlockState());
        var inventory=StorageRoutes.handler(h.getLevel(),ground.above(2),Direction.DOWN);StorageInventory.insert(inventory,new ItemStack(Items.DIAMOND,19),false);
        h.assertTrue(f.taskAt(cell,r)==null&&StorageInventory.count(inventory,new ItemStack(Items.DIAMOND))==19,"Machine/inventory blocks farm preparation instead of deletion or till-through");
        h.succeed();
    }

    @GameTest(template="quarry_test_empty",timeoutTicks=1800)
    public static void actorReloadThenBrokenGapPreservesTaggedParcelToVerticalInput(GameTestHelper h) {
        for(int x=4;x<=15;x++)for(int z=6;z<=10;z++)h.setBlock(new BlockPos(x,3,z),Blocks.STONE);
        for(int x=5;x<=12;x++)h.setBlock(new BlockPos(x,4,8),RatsBlockRegistry.RAT_TUBE_COLOR.get());
        for(int y=5;y<=8;y++)h.setBlock(new BlockPos(12,y,8),RatsBlockRegistry.RAT_TUBE_COLOR.get());
        h.setBlock(new BlockPos(13,8,8),RatsBlockRegistry.RAT_STORAGE_INPUT.get());
        var target=h.absolutePos(new BlockPos(13,8,8));
        var parcel=new ItemStack(Items.DIAMOND,64);parcel.getOrCreateTag().putString("IndependentParcel","physical-reload-gap");
        var first=h.spawn(RatsEntityRegistry.TAMED_RAT.get(),new BlockPos(5,4,6));
        first.setTame(true);first.setItemSlot(EquipmentSlot.CHEST,new ItemStack(RatsItemRegistry.RAT_UPGRADE_PLATTER.get()));
        first.cargo.set(0,parcel.copy());first.setDepositPos(GlobalPos.of(h.getLevel().dimension(),target));first.setCommand(RatCommand.HARVEST);
        var actor=new com.github.alexthe666.rats.server.entity.rat.TamedRat[]{first};boolean[] phases={false,false,false};
        h.onEachTick(()->{
            var r=actor[0];
            if(!phases[0]&&r.isInTube()&&r.getX()>h.absolutePos(new BlockPos(6,4,8)).getX()) {
                var tag=r.saveWithoutId(new CompoundTag());r.discard();var copy=RatsEntityRegistry.TAMED_RAT.get().create(h.getLevel());copy.load(tag);
                h.assertTrue(h.getLevel().addFreshEntity(copy),"Actual NBT reload of a moving, loaded actor succeeds");actor[0]=copy;phases[0]=true;
                h.setBlock(new BlockPos(8,4,8),Blocks.AIR);h.setBlock(new BlockPos(9,4,8),Blocks.AIR);
            }
            r=actor[0];
            if(phases[0]&&!r.isInTube()&&r.getX()>=h.absolutePos(new BlockPos(8,4,8)).getX()&&!r.cargo.isEmpty())phases[1]=true;
            if(phases[1]&&r.isInTube()&&r.getX()>=h.absolutePos(new BlockPos(10,4,8)).getX())phases[2]=true;
        });
        h.succeedWhen(()->h.assertTrue(phases[0]&&phases[1]&&phases[2]&&actor[0].isAlive()&&actor[0].cargo.isEmpty()
                &&StorageInventory.count(StorageRoutes.handler(h.getLevel(),target,Direction.WEST),parcel)==64,
                "Reloaded real worker leaves broken sequence, re-enters, climbs and delivers exact tagged 64 without loss/duplication: "+actor[0].position()+" "+actor[0].getWorkDebugState()));
    }
}
