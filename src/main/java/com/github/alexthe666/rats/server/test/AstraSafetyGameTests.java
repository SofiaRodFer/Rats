package com.github.alexthe666.rats.server.test;

import com.github.alexthe666.rats.registry.*;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.entity.ai.goal.RatStorageGoal;
import com.github.alexthe666.rats.server.events.ForgeEvents;
import com.github.alexthe666.rats.server.inventory.RatMenu;
import com.github.alexthe666.rats.server.message.*;
import com.github.alexthe666.rats.server.misc.WorkstationLedger;
import com.github.alexthe666.rats.server.storage.*;
import com.github.alexthe666.rats.server.block.entity.RatStorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;

@GameTestHolder("rats")
@PrefixGameTestTemplate(false)
public class AstraSafetyGameTests {
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h) {
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"AstraAudit"));
        h.getLevel().addNewPlayer(player);
        return player;
    }
    private static TamedRat rat(GameTestHelper h) {
        var r = h.spawn(RatsEntityRegistry.TAMED_RAT.get(), new BlockPos(8,4,8));
        r.setNoAi(true); r.setTame(true); return r;
    }
    @GameTest(template="quarry_test_empty")
    public static void menuRejectsStrangersDistanceAndStaleEntity(GameTestHelper h) {
        var r=rat(h); var owner=player(h); owner.setPos(r.position()); r.setOwnerUUID(owner.getUUID());
        var menu=new RatMenu(701,r.getInventory(),owner.getInventory()); owner.containerMenu=menu;
        h.assertTrue(RatPacketAuthority.menuRat(owner,r.getId())==r,"Own open nearby menu authorizes its rat");
        h.assertTrue(RatPacketAuthority.menuRat(owner,r.getId()+1)==null,"Different ID is not authorized");
        r.setOwnerUUID(java.util.UUID.randomUUID());
        h.assertTrue(!menu.stillValid(owner)&&RatPacketAuthority.menuRat(owner,r.getId())==null,"Ownership change invalidates menu");
        r.setOwnerUUID(owner.getUUID()); owner.setPos(r.position().add(20,0,0));
        h.assertTrue(!menu.stillValid(owner),"Distance invalidates entity menu");
        owner.setPos(r.position()); r.discard();
        h.assertTrue(!menu.stillValid(owner),"Removed entity cannot retain editable menu");
        h.succeed();
    }
    @GameTest(template="quarry_test_empty")
    public static void menuCargoLimitsAndUpgradeRemovalConserveItems(GameTestHelper h) {
        var r=rat(h); var owner=player(h); owner.setPos(r.position()); r.setOwnerUUID(owner.getUUID());
        var menu=new RatMenu(702,r.getInventory(),owner.getInventory());
        menu.setCarried(new ItemStack(Items.COBBLESTONE,64)); menu.clicked(0,0,ClickType.PICKUP,owner);
        h.assertTrue(r.cargo.get(0).getCount()==1&&menu.getCarried().getCount()==63,"Normal click obeys one-item base cargo capacity");
        h.assertTrue(!menu.slots.get(42).mayPlace(new ItemStack(Items.DIAMOND)),"Extra cargo disabled without tray");
        r.cargo.set(0,ItemStack.EMPTY);
        owner.getInventory().setItem(0,new ItemStack(Items.COBBLESTONE,64));
        menu.clicked(0,0,ClickType.SWAP,owner);
        h.assertTrue(r.cargo.get(0).getCount()<=1,"Hotbar swap obeys base cargo capacity");
        h.assertTrue(r.cargo.get(0).getCount()+owner.getInventory().items.stream().filter(s->s.is(Items.COBBLESTONE)).mapToInt(ItemStack::getCount).sum()==64,"Swap preserves all sixty-four input items");
        r.cargo.set(0,ItemStack.EMPTY);
        r.setItemSlot(EquipmentSlot.CHEST,new ItemStack(RatsItemRegistry.RAT_UPGRADE_SILVER_TRAY.get()));
        r.cargo.set(1,new ItemStack(Items.DIAMOND,64));
        h.assertTrue(!menu.slots.get(3).mayPickup(owner),"Cannot remove tray with extra cargo");
        menu.setCarried(ItemStack.EMPTY); menu.clicked(3,0,ClickType.PICKUP,owner);
        h.assertTrue(r.cargo.get(1).getCount()==64&&r.getInventory().getItem(3).is(RatsItemRegistry.RAT_UPGRADE_SILVER_TRAY.get()),"Denied click does not erase equipment/cargo");
        r.cargo.set(1,ItemStack.EMPTY);
        h.assertTrue(menu.slots.get(3).mayPickup(owner),"Empty cargo allows upgrade removal");
        h.succeed();
    }
    @GameTest(template="quarry_test_empty")
    public static void teleportWorksBelowZeroAndFailureRestoresPosition(GameTestHelper h) {
        var r=rat(h);
        var target=new BlockPos(r.blockPosition().getX()+2,-20,r.blockPosition().getZ());
        var floor=target.below(); var oldFloor=h.getLevel().getBlockState(floor);
        var oldTarget=h.getLevel().getBlockState(target);
        var oldAbove=h.getLevel().getBlockState(target.above());
        try {
            h.getLevel().setBlockAndUpdate(floor,Blocks.STONE.defaultBlockState());
            h.getLevel().setBlockAndUpdate(target,Blocks.AIR.defaultBlockState());
            h.getLevel().setBlockAndUpdate(target.above(),Blocks.AIR.defaultBlockState());
            r.attemptTeleport(target.getX()+.5,target.getY(),target.getZ()+.5);
            h.assertTrue(r.getY()==-20,"Actual Ender teleport finds floor below Y=0");
            var original=r.position();
            h.getLevel().setBlockAndUpdate(target,Blocks.LAVA.defaultBlockState());
            r.attemptTeleport(target.getX()+.5,target.getY(),target.getZ()+.5);
            h.assertTrue(r.position().equals(original),"Unsafe destination preserves original position");
        } finally {
            h.getLevel().setBlockAndUpdate(floor,oldFloor);
            h.getLevel().setBlockAndUpdate(target,oldTarget);
            h.getLevel().setBlockAndUpdate(target.above(),oldAbove);
        }
        h.succeed();
    }
    @GameTest(template="quarry_test_empty")
    public static void staffAuthorityPreservesRemoteOwnedSelection(GameTestHelper h) {
        var r=rat(h); var owner=player(h); owner.setPos(r.position().add(30,0,0)); r.setOwnerUUID(owner.getUUID());
        var selected=owner.getCapability(RatsCapabilityRegistry.SELECTED_RAT).orElseThrow(()->new IllegalStateException("Missing selection"));
        selected.setSelectedRat(r);
        owner.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(RatsItemRegistry.PATROL_STICK.get()));
        h.assertTrue(RatPacketAuthority.staffRat(owner,r.getId(),2)==r,"Own selected rat remains configurable remotely by correct staff");
        h.assertTrue(RatPacketAuthority.staffRat(owner,r.getId(),1)==null,"Wrong staff kind cannot configure radius");
        selected.clearSelectedRat();
        h.assertTrue(RatPacketAuthority.staffRat(owner,r.getId(),2)==null,"Unselected rat cannot be configured");
        h.succeed();
    }
    @GameTest(template="quarry_test_empty")
    public static void recipeAuthorityRequiresMatchingOpenLiveTable(GameTestHelper h) {
        var p=new BlockPos(10,4,10); h.setBlock(p,RatsBlockRegistry.RAT_CRAFTING_TABLE.get());
        var table=(com.github.alexthe666.rats.server.block.entity.RatCraftingTableBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(p));
        var owner=player(h); owner.setPos(table.getBlockPos().getCenter());
        h.assertTrue(RatPacketAuthority.table(owner,table.getBlockPos().asLong())==null,"No table menu grants no authority");
        var menu=new com.github.alexthe666.rats.server.inventory.RatCraftingTableMenu(703,owner.getInventory(),table,new net.minecraft.world.inventory.SimpleContainerData(2));
        owner.containerMenu=menu;
        h.assertTrue(RatPacketAuthority.table(owner,table.getBlockPos().asLong())==menu,"Matching open table authorized");
        h.assertTrue(RatPacketAuthority.table(owner,table.getBlockPos().east().asLong())==null,"Different block is rejected");
        h.setBlock(p,Blocks.AIR);
        h.assertTrue(RatPacketAuthority.table(owner,table.getBlockPos().asLong())==null,"Removed table invalidates old menu");
        h.succeed();
    }
    @GameTest(template="quarry_test_empty")
    public static void oversizedWireListsFailBeforeAllocatingElements(GameTestHelper h) {
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeInt(1); buf.writeVarInt(SyncRatTagPacket.MAX_NODES+1);
            boolean denied=false; try { SyncRatTagPacket.decode(buf); } catch(DecoderException expected) { denied=true; }
            h.assertTrue(denied,"Oversized patrol rejected before reading elements");
            buf.clear(); buf.writeBoolean(true); buf.writeBoolean(false); buf.writeVarInt(UpdateMobFilterPacket.MAX_MOBS+1);
            denied=false; try { UpdateMobFilterPacket.decode(buf); } catch(DecoderException expected) { denied=true; }
            h.assertTrue(denied,"Oversized mob filter rejected before reading strings");
            buf.clear(); buf.writeInt(1); buf.writeVarInt(0);
            h.assertTrue(SyncRatTagPacket.decode(buf).nodes().isEmpty(),"Empty route remains valid recovery command");
        } finally { buf.release(); }
        h.succeed();
    }
    @GameTest(template="quarry_test_empty")
    public static void workstationRetirementsPersistAndAreConsumedOnce(GameTestHelper h) {
        var ledger=new WorkstationLedger(); var id=java.util.UUID.randomUUID(); var station=h.absolutePos(new BlockPos(10,4,10));
        ledger.retire(station,id); ledger.retire(station,id);
        var saved=WorkstationLedger.load(ledger.save(new CompoundTag()));
        h.assertTrue(saved.take(station,id)&&!saved.take(station,id),"Deferred unlink survives NBT and deduplicates repeated removal");
        h.succeed();
    }
    @GameTest(template="quarry_test_empty")
    public static void removalWhileStationUnloadedPersistsUnlink(GameTestHelper h) {
        var r=rat(h);
        var station=new BlockPos(1000000,40,1000000);
        h.assertTrue(!h.getLevel().hasChunkAt(station),"Fixture station is truly unloaded");
        r.setDepositPos(net.minecraft.core.GlobalPos.of(h.getLevel().dimension(),station));
        var id=r.getUUID(); r.discard();
        var restored=WorkstationLedger.load(WorkstationLedger.get(h.getLevel()).save(new CompoundTag()));
        h.assertTrue(restored.take(station,id),"Actual discarded rat records unlink without loading station");
        h.assertTrue(!h.getLevel().hasChunkAt(station),"Lifecycle cannot force-load remote chunk");
        WorkstationLedger.get(h.getLevel()).take(station,id);
        h.succeed();
    }
    @GameTest(template="quarry_test_empty")
    public static void storageRetirementIsIdempotentAndTrueUnloadKeepsWorker(GameTestHelper h) {
        var p=new BlockPos(10,4,10); h.setBlock(p,RatsBlockRegistry.RAT_STORAGE_CONTROLLER.get());
        var c=(RatStorageBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(p)); var r=rat(h);
        r.setItemSlot(EquipmentSlot.CHEST,new ItemStack(RatsItemRegistry.RAT_UPGRADE_STORAGE_KEEPER.get()));
        h.assertTrue(c.assign(r),"Live storage worker assigned");
        StorageEvents.retire(r); StorageEvents.retire(r);
        h.assertTrue(c.workers.isEmpty()&&!StorageLedger.get(h.getLevel()).takeDead(c.network,r.getUUID()),"Repeated retirement cannot leave second tombstone");
        h.assertTrue(c.assign(r),"Living worker can be assigned anew after explicit retirement");
        r.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        h.assertTrue(c.workers.contains(r.getUUID()),"Chunk unload is not death or dismissal");
        h.succeed();
    }
    @GameTest(template="quarry_test_empty")
    public static void dockRejectsFluidsMissingFloorAndTallCollision(GameTestHelper h) {
        var r=rat(h); var p=new BlockPos(12,4,12); var at=h.absolutePos(p);
        h.setBlock(p.below(),Blocks.STONE); h.setBlock(p,Blocks.AIR);
        h.assertTrue(RatStorageGoal.safeDock(r,at),"Dry supported exit valid");
        h.setBlock(p,Blocks.LAVA);
        h.assertTrue(!RatStorageGoal.safeDock(r,at),"Lava cannot be selected as safe exit");
        h.setBlock(p,Blocks.WATER);
        h.assertTrue(!RatStorageGoal.safeDock(r,at),"Flooded exit invalid");
        h.setBlock(p,Blocks.AIR); h.setBlock(p.below(),Blocks.AIR);
        h.assertTrue(!RatStorageGoal.safeDock(r,at),"Unsupported air cannot be selected");
        h.setBlock(p.below(),Blocks.OAK_FENCE);
        h.assertTrue(!RatStorageGoal.safeDock(r,at),"Tall support cannot clip docking rat");
        h.succeed();
    }
    @GameTest(template="quarry_test_empty")
    public static void recallRechecksExitAfterFloorDisappears(GameTestHelper h) {
        var p=new BlockPos(10,4,10); h.setBlock(p,RatsBlockRegistry.RAT_STORAGE_CONTROLLER.get());
        var c=(RatStorageBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(p)); var r=rat(h);
        r.setItemSlot(EquipmentSlot.CHEST,new ItemStack(RatsItemRegistry.RAT_UPGRADE_STORAGE_KEEPER.get()));
        h.assertTrue(c.assign(r),"Worker assigned before recall");
        for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL) {
            h.setBlock(p.relative(d),Blocks.STONE);
            h.setBlock(p.relative(d).below(),Blocks.STONE);
        }
        h.setBlock(p.west(),Blocks.AIR);
        r.setPos(c.getBlockPos().getX()+.5,c.getBlockPos().getY()+.27,c.getBlockPos().getZ()+.5);
        c.recalling.add(r.getUUID());
        var goal=new RatStorageGoal(r); goal.tick();
        h.assertTrue(c.workers.contains(r.getUUID()),"Recall begins moving, not instant teleport/removal");
        h.setBlock(p.west().below(),Blocks.AIR); h.setBlock(p.east(),Blocks.AIR);
        for(int i=0;i<30;i++) goal.tick();
        h.assertTrue(!c.workers.contains(r.getUUID())&&!r.getPersistentData().hasUUID("RatStorageNetwork"),"Changed unsafe exit selects safe alternative and finishes");
        h.assertTrue(r.blockPosition().equals(h.absolutePos(p.east())),"Worker exits on newly safe supported side, not former missing floor");
        h.succeed();
    }
    @GameTest(template="quarry_test_empty")
    public static void scytheUsesServerEquipmentAndRateLimit(GameTestHelper h) {
        var player=h.makeMockPlayer(); player.setPos(h.absolutePos(new BlockPos(8,4,8)).getCenter());
        var forged=new ItemStack(RatsItemRegistry.PLAGUE_SCYTHE.get());
        ForgeEvents.handleArmSwing(forged,player);
        h.assertTrue(!player.getPersistentData().contains("RatsScytheShotAt"),"Client supplied scythe cannot authorize shot");
        player.setItemSlot(EquipmentSlot.MAINHAND,forged);
        ForgeEvents.handleArmSwing(ItemStack.EMPTY,player);
        h.assertTrue(player.getPersistentData().contains("RatsScytheShotAt"),"Real held scythe authorizes shot");
        long before=h.getLevel().getEntitiesOfClass(com.github.alexthe666.rats.server.entity.projectile.PlagueShot.class,player.getBoundingBox().inflate(5)).size();
        for(int i=0;i<10;i++) ForgeEvents.handleArmSwing(forged,player);
        long after=h.getLevel().getEntitiesOfClass(com.github.alexthe666.rats.server.entity.projectile.PlagueShot.class,player.getBoundingBox().inflate(5)).size();
        h.assertTrue(before==1&&after==before,"Repeated same-tick requests do not create extra projectiles");
        h.succeed();
    }
}
