package com.github.alexthe666.rats.server.test;

import com.github.alexthe666.rats.registry.*;
import com.github.alexthe666.rats.server.block.entity.RatStorageBlockEntity;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.storage.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.items.ItemStackHandler;
import java.util.*;

@GameTestHolder("rats") @PrefixGameTestTemplate(false)
public class StorageGameTests {
	@GameTest(template="quarry_test_empty",timeoutTicks=1200)
	public static void staffLinksChestBeforeItsGuiAndRatEntersFromOutside(GameTestHelper h){
		var c=controller(h);for(int x=8;x<=12;x++)for(int z=5;z<=12;z++)h.setBlock(new BlockPos(x,3,z),Blocks.STONE);
		var player=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"StorageStaff"));player.setPos(c.getBlockPos().getX(),c.getBlockPos().getY(),c.getBlockPos().getZ()-2);c.owner=player.getUUID();
		// Ownership resolves through the world's player registry, just as on a
		// real client. An unregistered FakePlayer cannot be returned by getOwner.
		h.getLevel().addNewPlayer(player);
		var staff=new ItemStack(RatsItemRegistry.CHEESE_STORAGE_STAFF.get());player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,staff);
		var click=new net.minecraft.world.phys.BlockHitResult(c.getBlockPos().getCenter(),Direction.NORTH,c.getBlockPos(),false);
		player.gameMode.useItemOn(player,h.getLevel(),staff,net.minecraft.world.InteractionHand.MAIN_HAND,click);h.assertTrue(staff.getOrCreateTag().hasUUID("Network"),"Real controller click selects staff network");
		var pos=chest(h,c);c.disconnect(pos);click=new net.minecraft.world.phys.BlockHitResult(pos.getCenter(),Direction.WEST,pos,false);player.setPos(pos.getX()-1,pos.getY(),pos.getZ());
		player.gameMode.useItemOn(player,h.getLevel(),staff,net.minecraft.world.InteractionHand.MAIN_HAND,click);h.assertTrue(c.endpoints.contains(pos)&&player.containerMenu==player.inventoryMenu,"Non-sneaking real chest click links without opening chest UI");
		var in=input(h,c);in.setItem(0,new ItemStack(Items.COBBLESTONE,64));var rat=h.spawn(RatsEntityRegistry.TAMED_RAT.get(),new BlockPos(10,4,6));rat.setTame(true);rat.setOwnerUUID(player.getUUID());rat.setItemSlot(EquipmentSlot.CHEST,new ItemStack(RatsItemRegistry.RAT_UPGRADE_STORAGE_KEEPER.get()));player.setPos(rat.getX(),rat.getY(),rat.getZ());staff.interactLivingEntity(player,rat,net.minecraft.world.InteractionHand.MAIN_HAND);
		h.assertTrue(c.workers.contains(rat.getUUID()),"Staff assigns outside rat: owned="+rat.isOwnedBy(player)+" baby="+rat.isBaby()+" cage="+rat.isInCage()+" passenger="+rat.isPassenger()+" upgrade="+com.github.alexthe666.rats.server.misc.RatUpgradeUtils.hasUpgrade(rat,RatsItemRegistry.RAT_UPGRADE_STORAGE_KEEPER.get())+" staff="+staff.getTag()+" rat="+rat.blockPosition()+" controller="+c.getBlockPos()+" network="+c.network);
		h.assertTrue(!com.github.alexthe666.rats.server.entity.ai.goal.RatStorageGoal.safeDock(rat,c.getBlockPos().east()),"A connected tube is not an exterior ground-navigation dock");
		h.assertTrue(com.github.alexthe666.rats.server.entity.ai.goal.RatStorageGoal.safeDock(rat,c.getBlockPos().west()),"A supported empty side remains a valid exterior entrance");
		h.succeedWhen(()->h.assertTrue(StorageInventory.count(StorageRoutes.handler(h.getLevel(),pos,Direction.WEST),new ItemStack(Items.COBBLESTONE))==64&&in.isEmpty(),"Outside rat docks despite input blocking front and delivers: pos="+rat.position()+" status="+rat.getPersistentData().getString("RatStorageStatus")));
	}
	@GameTest(template="quarry_test_empty",timeoutTicks=1000)
	public static void menuRecallWorksWithBlockedFront(GameTestHelper h){
		var c=controller(h);for(int x=9;x<=11;x++)for(int z=9;z<=11;z++)h.setBlock(new BlockPos(x,3,z),Blocks.STONE);input(h,c);var rat=worker(h,c);
		var player=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"StorageRecall"));player.setPos(c.getBlockPos().getX(),c.getBlockPos().getY(),c.getBlockPos().getZ());c.owner=player.getUUID();var menu=new com.github.alexthe666.rats.server.inventory.RatStorageMenu(73,player.getInventory(),c);menu.broadcastChanges();
		int id=menu.snapshot.getList("Workers",net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).getInt("Index");menu.action(player,1,id,0);h.assertTrue(c.recalling.contains(rat.getUUID()),"UI action sets recall on owned controller worker");
		h.succeedWhen(()->h.assertTrue(!c.workers.contains(rat.getUUID())&&!rat.getPersistentData().hasUUID("RatStorageNetwork")&&rat.isAlive(),"Recall finds an alternate exit instead of blocking on input"));
	}
	@GameTest(template="quarry_test_empty")
	public static void outputIsPlayerReadOnlyAndRecipeRefillRetired(GameTestHelper h){
		var c=controller(h);h.setBlock(new BlockPos(10,4,11),RatsBlockRegistry.RAT_STORAGE_OUTPUT.get());var out=(RatStorageBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(10,4,11)));
		var player=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"StorageOutput"));player.setPos(c.getBlockPos().getX(),c.getBlockPos().getY(),c.getBlockPos().getZ());player.getInventory().setItem(9,new ItemStack(Items.DIAMOND,64));var menu=new com.github.alexthe666.rats.server.inventory.RatStorageMenu(74,player.getInventory(),out);
		for(int i=0;i<54;i++)h.assertTrue(!menu.slots.get(i).mayPlace(new ItemStack(Items.DIAMOND)),"Normal click cannot place in output");h.assertTrue(menu.quickMoveStack(player,54).isEmpty()&&out.isEmpty()&&player.getInventory().getItem(9).getCount()==64,"Shift click cannot deposit into output");
		h.assertTrue(StorageInventory.insert(out.inventory(),new ItemStack(Items.DIAMOND,64),false).isEmpty(),"Rat delivery capability remains writable");h.assertTrue(!menu.quickMoveStack(player,0).isEmpty()&&out.isEmpty(),"Player can withdraw delivery");
		var publicOutput=out.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,Direction.UP).orElseThrow(()->new IllegalStateException("Missing output capability"));h.assertTrue(StorageInventory.insert(publicOutput,new ItemStack(Items.DIAMOND,64),false).getCount()==64&&out.isEmpty(),"External automation cannot insert into output");
		var controllerMenu=new com.github.alexthe666.rats.server.inventory.RatStorageMenu(75,player.getInventory(),c);controllerMenu.slots.get(1).set(new ItemStack(Items.OAK_PLANKS));controllerMenu.action(player,3,0,0);h.assertTrue(c.requests.isEmpty(),"Retired recipe refill action cannot create a request");h.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void deathMarkersSurviveUnloadedControllerAndReload(GameTestHelper h) {
		var c=controller(h); UUID missing=UUID.randomUUID(); c.workers.add(missing); var ledger=StorageLedger.get(h.getLevel()); ledger.markDead(c.network,missing); var restored=StorageLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag())); h.assertTrue(restored.takeDead(c.network,missing) && !restored.takeDead(c.network,missing),"Confirmed death persists across unloaded controller/restart and is consumed once");
		c.pruneDeadWorkers(); h.assertTrue(c.workers.isEmpty(),"Known dead UUID cannot permanently consume one of ten worker slots"); h.succeed();
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 120)
	public static void replacementInputCannotBeUsedWithoutLinkingAgain(GameTestHelper h) {
		var c=controller(h); var pos=chest(h,c); var old=input(h,c); old.setItem(0,new ItemStack(Items.COBBLESTONE,64)); var rat=worker(h,c); h.assertTrue(StorageScheduler.claim(c,rat)!=null,"Task reserved before replacement");
		BlockPos input=old.getBlockPos(); h.getLevel().setBlockAndUpdate(input,Blocks.AIR.defaultBlockState()); h.getLevel().setBlockAndUpdate(input,RatsBlockRegistry.RAT_STORAGE_INPUT.get().defaultBlockState()); var replacement=(RatStorageBlockEntity)h.getLevel().getBlockEntity(input); replacement.setItem(0,new ItemStack(Items.COBBLESTONE,64));
		h.runAfterDelay(60,()-> { h.assertTrue(replacement.getItem(0).getCount()==64 && rat.cargo.isEmpty() && c.task(rat)==null && StorageInventory.count(StorageRoutes.handler(h.getLevel(),pos,Direction.WEST),new ItemStack(Items.COBBLESTONE))==0,"Previously reserved task must not extract from a replacement module without its explicit controller link"); h.succeed(); });
	}
	@GameTest(template = "quarry_test_empty")
	public static void requestFairnessAndInvalidAmounts(GameTestHelper h) {
		var c=controller(h); var pos=chest(h,c); var handler=StorageRoutes.handler(h.getLevel(),pos,Direction.WEST); for(int i=0;i<10;i++) StorageInventory.insert(handler,new ItemStack(Items.COBBLESTONE,64),false);
		h.setBlock(new BlockPos(10,4,11),RatsBlockRegistry.RAT_STORAGE_OUTPUT.get()); BlockPos output=h.absolutePos(new BlockPos(10,4,11)); h.assertTrue(c.connect(output),"Output linked");
		h.assertTrue(!c.request(UUID.randomUUID(),new ItemStack(Items.COBBLESTONE),0,output,-1) && !c.request(UUID.randomUUID(),new ItemStack(Items.COBBLESTONE),100001,output,-1),"Bounded valid request amount");
		h.assertTrue(c.request(UUID.randomUUID(),new ItemStack(Items.COBBLESTONE),512,output,-1) && c.request(UUID.randomUUID(),new ItemStack(Items.COBBLESTONE),64,output,-1),"Two independent requests accepted");
		var one=StorageScheduler.claim(c,worker(h,c)); var two=StorageScheduler.claim(c,worker(h,c)); h.assertTrue(one!=null && two!=null && !one.request.equals(two.request),"Round-robin request scheduling prevents one large request monopolizing all workers"); h.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void craftingSessionsAreIsolatedBetweenPlayers(GameTestHelper h) {
		var c=controller(h); UUID one=UUID.randomUUID(),two=UUID.randomUUID(); c.session(one).setItem(0,new ItemStack(Items.DIAMOND,3)); c.session(two).setItem(0,new ItemStack(Items.OAK_PLANKS,6)); c.session(one).setItem(9,new ItemStack(Items.IRON_INGOT,16));
		var restored=new RatStorageBlockEntity(c.getBlockPos(),c.getBlockState()); restored.load(c.saveWithFullMetadata()); h.assertTrue(restored.session(one).getItem(0).is(Items.DIAMOND) && restored.session(two).getItem(0).is(Items.OAK_PLANKS) && restored.session(two).getItem(9).isEmpty(),"Crafting and ingredient deliveries remain private across save/reload"); h.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void workerDeathReleasesTaskWithoutDuplicatingCargo(GameTestHelper h) {
		var c=controller(h); chest(h,c); var in=input(h,c); in.setItem(0,new ItemStack(Items.COBBLESTONE,64)); var rat=worker(h,c); var task=StorageScheduler.claim(c,rat); rat.cargo.set(0,in.removeItem(0,64)); task.collected=true; task.loaded=64; task.capture(rat);
		rat.hurt(h.getLevel().damageSources().genericKill(),1000); h.assertTrue(!rat.isAlive() && c.workers.isEmpty() && c.tasks.isEmpty(),"Dead worker releases network capacity and task");
		long drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,rat.getBoundingBox().inflate(3)).stream().filter(e->e.getItem().is(Items.COBBLESTONE)).mapToLong(e->e.getItem().getCount()).sum(); h.assertTrue(drops==64 && in.isEmpty(),"Held physical parcel drops exactly once on death"); h.succeed();
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 1600)
	public static void partialDeliverySurvivesRatAndControllerReload(GameTestHelper h) {
		var c=controller(h); var pos=chest(h,c); StorageInventory.insert(StorageRoutes.handler(h.getLevel(),pos,Direction.WEST),new ItemStack(Items.COBBLESTONE,32),false); h.setBlock(new BlockPos(10,4,11),RatsBlockRegistry.RAT_STORAGE_OUTPUT.get()); var out=(RatStorageBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(10,4,11))); c.connect(out.getBlockPos());
		h.assertTrue(c.request(UUID.randomUUID(),new ItemStack(Items.COBBLESTONE),17,out.getBlockPos(),-1),"Request accepted before output fills"); TamedRat[] rat={worker(h,c)}; int[] phase={0};
		h.onEachTick(()-> {
			if(phase[0]==0 && rat[0].cargo.get(0).getCount()==17) { for(int i=0;i<54;i++) out.setItem(i,new ItemStack(i==0 ? Items.COBBLESTONE : Items.STONE,i==0 ? 63 : 64)); phase[0]=1; }
			if(phase[0]==1 && !c.requests.isEmpty() && c.requests.get(0).delivered()==1) {
				h.assertTrue(rat[0].cargo.get(0).getCount()==16,"Only actually accepted quantity is counted"); var saved=c.saveWithFullMetadata(); var ratTag=rat[0].saveWithoutId(new net.minecraft.nbt.CompoundTag()); rat[0].discard(); var restored=new RatStorageBlockEntity(c.getBlockPos(),c.getBlockState()); restored.load(saved); h.getLevel().setBlockEntity(restored); var copy=RatsEntityRegistry.TAMED_RAT.get().create(h.getLevel()); copy.load(ratTag); h.assertTrue(h.getLevel().addFreshEntity(copy),"Worker recreated from actual entity NBT"); rat[0]=copy; out.clearContent(); phase[0]=2;
			}
		});
		h.succeedWhen(()-> { var current=(RatStorageBlockEntity)h.getLevel().getBlockEntity(c.getBlockPos()); h.assertTrue(phase[0]==2 && current.requests.isEmpty() && rat[0].cargo.isEmpty() && StorageInventory.count(out.inventory(),new ItemStack(Items.COBBLESTONE))==16 && StorageInventory.count(StorageRoutes.handler(h.getLevel(),pos,Direction.WEST),new ItemStack(Items.COBBLESTONE))==15,"Exactly 17 delivered across partial acceptance, controller reload and rat reload"); });
	}
	@GameTest(template = "quarry_test_empty")
	public static void doubleChestsAreCountedOnlyOnce(GameTestHelper h) {
		var c=controller(h); var pos=chest(h,c); h.assertTrue(c.disconnect(pos),"Fixture disconnects");
		for(String namespace : List.of("minecraft","sophisticatedstorage")) {
			if(!namespace.equals("minecraft") && !net.minecraftforge.fml.ModList.get().isLoaded(namespace)) continue;
			var block=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(namespace,namespace.equals("minecraft") ? "chest" : "iron_chest"));
			var left=block.defaultBlockState().setValue(ChestBlock.FACING,Direction.NORTH).setValue(ChestBlock.TYPE,net.minecraft.world.level.block.state.properties.ChestType.LEFT); BlockPos other=pos.relative(ChestBlock.getConnectedDirection(left));
			h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),2); h.getLevel().setBlock(other,Blocks.AIR.defaultBlockState(),2); h.getLevel().setBlock(pos,left,2); h.getLevel().setBlock(other,block.defaultBlockState().setValue(ChestBlock.FACING,Direction.NORTH).setValue(ChestBlock.TYPE,net.minecraft.world.level.block.state.properties.ChestType.RIGHT),2);
			for(BlockPos p : List.of(pos.above(),other.above(),pos.above().west())) h.getLevel().setBlockAndUpdate(p,RatsBlockRegistry.RAT_TUBE_COLOR.get().defaultBlockState());
			h.assertTrue(c.connect(pos) && c.connect(other),"Both halves can have tube endpoints"); var handler=StorageRoutes.handler(h.getLevel(),pos,Direction.WEST); StorageInventory.insert(handler,new ItemStack(Items.DIAMOND,64),false);
			h.assertTrue(c.catalog().stream().filter(e->e.item().is(Items.DIAMOND)).mapToLong(StorageStock.Entry::stored).sum()==64,"Double chest inventory is never double-counted: " + namespace);
			h.assertTrue(c.disconnect(other) && c.catalog().stream().filter(e->e.item().is(Items.DIAMOND)).mapToLong(StorageStock.Entry::stored).sum()==64,"Removing an alias keeps remaining chest ownership"); h.assertTrue(c.disconnect(pos),"Full double chest unlink");
		}
		h.succeed();
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 1400)
	public static void tenRatsDeliverAllParcelsAndVerticalPortWorks(GameTestHelper h) {
		var c=controller(h); var pos=chest(h,c); var in=input(h,c); for(int i=0;i<10;i++) in.setItem(i,new ItemStack(Items.COBBLESTONE,64)); for(int i=0;i<10;i++) worker(h,c);
		h.runAfterDelay(8,()->h.assertTrue(c.tasks.size()==10,"Ten simultaneous tasks, no all-worker barrier")); h.succeedWhen(()->h.assertTrue(in.isEmpty() && StorageInventory.count(StorageRoutes.handler(h.getLevel(),pos,Direction.WEST),new ItemStack(Items.COBBLESTONE))==640,"All ten rats physically deliver full stacks"));
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 1200)
	public static void ratActuallyClimbsToChestBottomFace(GameTestHelper h) {
		var c=controller(h); for(int x=11;x<=14;x++) h.setBlock(new BlockPos(x,4,10),RatsBlockRegistry.RAT_TUBE_COLOR.get()); for(int y=5;y<=7;y++) h.setBlock(new BlockPos(14,y,10),RatsBlockRegistry.RAT_TUBE_COLOR.get()); h.setBlock(new BlockPos(14,8,10),Blocks.CHEST); BlockPos pos=h.absolutePos(new BlockPos(14,8,10)); h.assertTrue(c.connect(pos),"Chest above vertical tube links"); var in=input(h,c); in.setItem(0,new ItemStack(Items.DIAMOND,64)); var rat=worker(h,c);
		h.succeedWhen(()-> { h.assertTrue(StorageInventory.count(StorageRoutes.handler(h.getLevel(),pos,Direction.DOWN),new ItemStack(Items.DIAMOND))==64 && rat.cargo.isEmpty(),"Rat physically climbed and delivered through bottom face"); });
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 1200)
	public static void removingTubeUnderWorkerPreservesItsParcel(GameTestHelper h) {
		var c=controller(h); var pos=chest(h,c); var in=input(h,c); in.setItem(0,new ItemStack(Items.COBBLESTONE,64)); var rat=worker(h,c); boolean[] broke={false},checked={false};
		h.onEachTick(()-> { if(!broke[0] && rat.isInTube() && !rat.cargo.isEmpty()) { broke[0]=true; BlockPos tube=rat.blockPosition(); h.getLevel().setBlockAndUpdate(tube,Blocks.AIR.defaultBlockState()); h.runAfterDelay(80,()-> {
			long held=java.util.stream.IntStream.range(0,10).filter(i->rat.cargo.get(i).is(Items.COBBLESTONE)).mapToLong(i->rat.cargo.get(i).getCount()).sum();
			long delivered=StorageInventory.count(StorageRoutes.handler(h.getLevel(),pos,Direction.WEST),new ItemStack(Items.COBBLESTONE));
			long loose=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(c.getBlockPos()).inflate(12)).stream().filter(e->e.getItem().is(Items.COBBLESTONE)).mapToLong(e->e.getItem().getCount()).sum();
			h.assertTrue(rat.isAlive()&&held+delivered==64&&loose==0,"Broken occupied tube preserves physical parcel without fatal fall: health="+rat.getHealth()+" held="+held+" delivered="+delivered+" loose="+loose+" pos="+rat.position()+" status="+rat.getPersistentData().getString("RatStorageStatus"));
			if(!rat.isInTube()&&!com.github.alexthe666.rats.server.misc.TubeTravel.dock(h.getLevel(),rat.blockPosition()))h.assertTrue(!rat.noPhysics,"Outside rat cannot retain tube collision bypass");
			checked[0]=true;h.getLevel().setBlockAndUpdate(tube,RatsBlockRegistry.RAT_TUBE_COLOR.get().defaultBlockState());
		}); } });
		h.succeedWhen(()->h.assertTrue(broke[0]&&checked[0]&&rat.cargo.isEmpty()&&StorageInventory.count(StorageRoutes.handler(h.getLevel(),pos,Direction.WEST),new ItemStack(Items.COBBLESTONE))==64,"Worker safely exits/reenters or resumes after repair, then delivers exactly64; delayed80tick conservation check must execute"));
	}
	@GameTest(template = "quarry_test_empty")
	public static void realCraftingRemaindersAndSessionPersistence(GameTestHelper h) {
		var c=controller(h); var player=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"StorageCraft")); player.setPos(c.getBlockPos().getX(),c.getBlockPos().getY(),c.getBlockPos().getZ());
		var menu=new com.github.alexthe666.rats.server.inventory.RatStorageMenu(71,player.getInventory(),c);
		for(int cell : List.of(1,2,4,5)) menu.slots.get(cell).set(new ItemStack(Items.OAK_PLANKS));
		h.assertTrue(menu.slots.get(0).getItem().is(Items.CRAFTING_TABLE),"Functional real 3x3 recipe result"); menu.quickMoveStack(player,0); h.assertTrue(player.getInventory().countItem(Items.CRAFTING_TABLE)==1 && java.util.stream.IntStream.rangeClosed(1,9).allMatch(i->menu.slots.get(i).getItem().isEmpty()),"Result consumes exactly four real ingredients");
		for(int i=1;i<=3;i++) menu.slots.get(i).set(new ItemStack(Items.MILK_BUCKET)); menu.slots.get(4).set(new ItemStack(Items.SUGAR)); menu.slots.get(5).set(new ItemStack(Items.EGG)); menu.slots.get(6).set(new ItemStack(Items.SUGAR)); for(int i=7;i<=9;i++) menu.slots.get(i).set(new ItemStack(Items.WHEAT));
		h.assertTrue(menu.slots.get(0).getItem().is(Items.CAKE),"Vanilla cake recipe matches"); menu.quickMoveStack(player,0); h.assertTrue(player.getInventory().countItem(Items.CAKE)==1 && java.util.stream.IntStream.rangeClosed(1,3).allMatch(i->menu.slots.get(i).getItem().is(Items.BUCKET)),"Milk bucket remainders preserved");
		menu.removed(player); var restored=new RatStorageBlockEntity(c.getBlockPos(),c.getBlockState()); restored.load(c.saveWithFullMetadata()); h.assertTrue(java.util.stream.IntStream.range(0,3).allMatch(i->restored.session(player.getUUID()).getItem(i).is(Items.BUCKET)),"Closing and save/reload preserve actual crafting ingredients"); h.succeed();
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 300)
	public static void idleDockIsSafeAndRecallUsesPhysicalExit(GameTestHelper h) {
		var c=controller(h); var rat=worker(h,c); h.setBlock(new BlockPos(10,3,9),Blocks.STONE);
		h.runAfterDelay(180,()-> { h.assertTrue(rat.isAlive() && rat.getHealth()==rat.getMaxHealth(),"Waiting inside the controller must not suffocate or fall"); c.recalling.add(rat.getUUID()); });
		h.succeedWhen(()->h.assertTrue(!rat.getPersistentData().hasUUID("RatStorageNetwork") && !c.workers.contains(rat.getUUID()) && rat.blockPosition().equals(h.absolutePos(new BlockPos(10,4,9))),"Recall physically exits controller and unregisters rat"));
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 1000)
	public static void silverTrayCarriesDifferentItemsTogether(GameTestHelper h) {
		var c=controller(h); var pos=chest(h,c); var in=input(h,c); in.setItem(0,new ItemStack(Items.COBBLESTONE,64)); in.setItem(1,new ItemStack(Items.DIRT,64)); var rat=worker(h,c); rat.setItemSlot(EquipmentSlot.LEGS,new ItemStack(RatsItemRegistry.RAT_UPGRADE_SILVER_TRAY.get())); boolean[] mixed={false};
		h.onEachTick(()-> { if(rat.cargo.get(0).is(Items.COBBLESTONE) && java.util.stream.IntStream.range(1,10).anyMatch(i->rat.cargo.get(i).is(Items.DIRT))) mixed[0]=true; });
		h.succeedWhen(()-> { var handler=StorageRoutes.handler(h.getLevel(),pos,Direction.WEST); h.assertTrue(mixed[0] && StorageInventory.count(handler,new ItemStack(Items.COBBLESTONE))==64 && StorageInventory.count(handler,new ItemStack(Items.DIRT))==64,"One physical tray trip can carry different compatible items"); });
	}
	@GameTest(template = "quarry_test_empty")
	public static void realIronChestsAndSophisticatedStorage(GameTestHelper h) throws ReflectiveOperationException {
		if (!net.minecraftforge.fml.ModList.get().isLoaded("ironchest") || !net.minecraftforge.fml.ModList.get().isLoaded("sophisticatedstorage")) { h.succeed(); return; }
		var c = controller(h); BlockPos pos = chest(h,c); h.assertTrue(c.disconnect(pos), "Disconnect vanilla fixture chest");
		for (String namespace : List.of("ironchest", "sophisticatedstorage")) for (String tier : List.of("iron_chest", "diamond_chest")) {
			var block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(namespace, tier)); h.assertTrue(block != null && block != Blocks.AIR, "Actual compatible chest registered: " + namespace + ":" + tier); h.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
			h.assertTrue(c.connect(pos), "Real mod chest links through Forge face capability"); var handler = StorageRoutes.handler(h.getLevel(), pos, Direction.WEST); h.assertTrue(handler != null && handler.getSlots() > 27, "Larger chest exposes real slots");
			for (Direction face : Direction.values()) h.assertTrue(StorageRoutes.handler(h.getLevel(), pos, face) != null, "Inventory accessible by each face: " + face);
			var left = StorageInventory.insert(handler, new ItemStack(Items.DIAMOND,64), false); h.assertTrue(left.isEmpty() && StorageInventory.count(handler,new ItemStack(Items.DIAMOND))==64,"Real mod insertion and count");
			h.assertTrue(StorageInventory.extract(handler,new ItemStack(Items.DIAMOND),32).getCount()==32,"Real mod extraction"); h.assertTrue(c.disconnect(pos),"Real mod chest disconnects without clearing its items");
		}
		h.getLevel().setBlockAndUpdate(pos, net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation("sophisticatedstorage", "diamond_chest")).defaultBlockState());
		Object wrapper = h.getLevel().getBlockEntity(pos).getClass().getMethod("getStorageWrapper").invoke(h.getLevel().getBlockEntity(pos)); var api = Class.forName("net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper");
		var upgrades = (net.minecraftforge.items.IItemHandlerModifiable)api.getMethod("getUpgradeHandler").invoke(wrapper); var upgrade = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("sophisticatedstorage", "stack_upgrade_tier_1")); upgrades.setStackInSlot(0,new ItemStack(upgrade));
		var handler = StorageRoutes.handler(h.getLevel(),pos,Direction.WEST); h.assertTrue(handler.getSlotLimit(0)>64,"Actual Sophisticated stack upgrade raises capacity");
		for(int i=0;i<3;i++) h.assertTrue(StorageInventory.insert(handler,new ItemStack(Items.COBBLESTONE,64),false).isEmpty(),"Normal carried stack inserted into upgraded chest");
		h.assertTrue(java.util.stream.IntStream.range(0,handler.getSlots()).anyMatch(i -> handler.getStackInSlot(i).is(Items.COBBLESTONE) && handler.getStackInSlot(i).getCount()>64) && StorageInventory.count(handler,new ItemStack(Items.COBBLESTONE))==192,"Actual upgraded chest holds oversized stack, not simulated mock"); h.succeed();
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 1000)
	public static void brokenTubePreservesCargoAndResumes(GameTestHelper h) {
		var c=controller(h); var pos=chest(h,c); var in=input(h,c); in.setItem(0,new ItemStack(Items.COBBLESTONE,64)); var rat=worker(h,c); boolean[] broke={false};
		h.onEachTick(()-> { if(!broke[0] && !rat.cargo.isEmpty()) { broke[0]=true; h.setBlock(new BlockPos(14,4,10),Blocks.AIR); h.runAfterDelay(60,()-> { h.assertTrue(!rat.cargo.isEmpty(),"Broken route never discards extracted cargo"); h.setBlock(new BlockPos(14,4,10),RatsBlockRegistry.RAT_TUBE_COLOR.get()); }); } });
		h.succeedWhen(()->h.assertTrue(broke[0] && rat.cargo.isEmpty() && StorageInventory.count(StorageRoutes.handler(h.getLevel(),pos,Direction.WEST),new ItemStack(Items.COBBLESTONE))==64,"Delivery resumes after tube repair: controller=" + c.getBlockPos() + "; chest=" + pos + "; pos=" + rat.position() + "; cargo=" + rat.cargo.get(0) + "; input=" + StorageInventory.count(in.inventory(),new ItemStack(Items.COBBLESTONE)) + "; chestCount=" + StorageInventory.count(StorageRoutes.handler(h.getLevel(),pos,Direction.WEST),new ItemStack(Items.COBBLESTONE)) + "; recovery=" + StorageInventory.count(c.inventory(),new ItemStack(Items.COBBLESTONE)) + "; status=" + rat.getPersistentData().getString("RatStorageStatus") + "; task=" + c.task(rat)));
	}
	@GameTest(template = "quarry_test_empty")
	public static void fullFilteredSlotsAndCargoCapacity(GameTestHelper h) {
		var handler=new ItemStackHandler(2) { @Override public boolean isItemValid(int slot, ItemStack stack) { return slot==0 && stack.is(Items.DIAMOND); } }; handler.setStackInSlot(0,new ItemStack(Items.DIAMOND,60));
		h.assertTrue(StorageInventory.capacity(handler,new ItemStack(Items.DIAMOND),64)==4 && StorageInventory.capacity(handler,new ItemStack(Items.DIRT),64)==0,"Actual filters and partial space honored");
		var c=controller(h); var rat=worker(h,c); h.assertTrue(rat.cargo.limit(new ItemStack(Items.DIAMOND))==64 && rat.cargo.slots()==1,"Keeper carries a stack without separate platter"); rat.setItemSlot(EquipmentSlot.LEGS,new ItemStack(RatsItemRegistry.RAT_UPGRADE_SILVER_TRAY.get())); h.assertTrue(rat.cargo.slots()==10,"Keeper and Silver Tray are compatible"); rat.cargo.set(0,new ItemStack(Items.WHEAT,64)); h.assertTrue(!rat.isHoldingFood(),"Storage cargo cannot be eaten"); h.succeed();
	}
	private static RatStorageBlockEntity controller(GameTestHelper h) { h.setBlock(new BlockPos(10,4,10), RatsBlockRegistry.RAT_STORAGE_CONTROLLER.get()); return (RatStorageBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(10,4,10))); }
	private static TamedRat worker(GameTestHelper h, RatStorageBlockEntity c) { var rat = h.spawn(RatsEntityRegistry.TAMED_RAT.get(), new BlockPos(10,4,10)); rat.setTame(true); rat.setItemSlot(EquipmentSlot.CHEST, new ItemStack(RatsItemRegistry.RAT_UPGRADE_STORAGE_KEEPER.get())); rat.setPos(c.getBlockPos().getX() + 0.5, c.getBlockPos().getY() + 0.27, c.getBlockPos().getZ() + 0.5); h.assertTrue(c.assign(rat), "Keeper assigned"); return rat; }
	private static BlockPos chest(GameTestHelper h, RatStorageBlockEntity c) { for (int x = 11; x <= 17; x++) h.setBlock(new BlockPos(x,4,10), RatsBlockRegistry.RAT_TUBE_COLOR.get()); h.setBlock(new BlockPos(18,4,10), Blocks.CHEST); BlockPos pos = h.absolutePos(new BlockPos(18,4,10)); h.assertTrue(c.connect(pos), "Chest connected by tube"); return pos; }
	private static RatStorageBlockEntity input(GameTestHelper h, RatStorageBlockEntity c) { h.setBlock(new BlockPos(10,4,9), RatsBlockRegistry.RAT_STORAGE_INPUT.get()); var input = (RatStorageBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(10,4,9))); h.assertTrue(c.connect(input.getBlockPos()), "Adjacent input connected"); return input; }
	@GameTest(template = "quarry_test_empty")
	public static void simulationDoesNotMutateAndSupportsLargeSlots(GameTestHelper h) {
		var handler = new ItemStackHandler(1) { @Override public int getSlotLimit(int slot) { return 1024; } @Override protected int getStackLimit(int slot, ItemStack stack) { return 1024; } };
		handler.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 900)); h.assertTrue(StorageInventory.capacity(handler, new ItemStack(Items.COBBLESTONE), 64) == 64, "Oversized slot accepts normal carried stack"); h.assertTrue(handler.getStackInSlot(0).getCount() == 900, "Simulation never mutates storage"); StorageInventory.insert(handler, new ItemStack(Items.COBBLESTONE, 64), false); h.assertTrue(handler.getStackInSlot(0).getCount() == 964, "Actual oversized insertion respected"); h.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void tagsRemainSeparateAndRoutesUseSixFaces(GameTestHelper h) {
		var c = controller(h); BlockPos pos = chest(h, c); var handler = StorageRoutes.handler(h.getLevel(), pos, Direction.WEST); var named = new ItemStack(Items.DIAMOND, 3); named.setHoverName(net.minecraft.network.chat.Component.literal("Special")); handler.insertItem(0, named, false); handler.insertItem(1, new ItemStack(Items.DIAMOND, 5), false);
		h.assertTrue(c.catalog().size() == 2, "NBT variants must never merge"); var graph = StorageRoutes.graph(h.getLevel(), c.getBlockPos()); h.assertTrue(StorageRoutes.port(h.getLevel(), pos, graph).face() == Direction.WEST, "Use actual connected face");
		for (int y = 5; y <= 8; y++) h.setBlock(new BlockPos(14,y,10), RatsBlockRegistry.RAT_TUBE_COLOR.get()); var upper = h.absolutePos(new BlockPos(14,8,10)); h.assertTrue(!StorageRoutes.route(h.getLevel(), c.getBlockPos(), c.getBlockPos(), upper).isEmpty(), "Vertical route reachable");
		h.assertTrue(StorageRoutes.within(c.getBlockPos(), c.getBlockPos().above(20)) && !StorageRoutes.within(c.getBlockPos(), c.getBlockPos().offset(20,1,0)), "Euclidean 20-block range includes height"); h.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void tenWorkersAndNoDuplicateReservation(GameTestHelper h) {
		var c = controller(h); var pos = chest(h,c); var in = input(h,c); in.setItem(0,new ItemStack(Items.COBBLESTONE,64)); var rats = new ArrayList<TamedRat>(); for(int i=0;i<10;i++) rats.add(worker(h,c));
		var extra = h.spawn(RatsEntityRegistry.TAMED_RAT.get(),new BlockPos(10,4,10)); extra.setTame(true); extra.setItemSlot(EquipmentSlot.CHEST,new ItemStack(RatsItemRegistry.RAT_UPGRADE_STORAGE_KEEPER.get())); h.assertTrue(!c.assign(extra),"Eleventh rat rejected");
		var one = StorageScheduler.claim(c,rats.get(0)); var two = StorageScheduler.claim(c,rats.get(1)); h.assertTrue(one != null && two == null && one.maximum == 64,"Single stack reserved once and whole stack carried");
		var saved = c.saveWithFullMetadata(); var restored = new RatStorageBlockEntity(c.getBlockPos(),c.getBlockState()); restored.load(saved); h.assertTrue(restored.workers.size()==10 && restored.tasks.size()==1 && restored.network.equals(c.network),"Assignments and tasks survive reload"); h.succeed();
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 1200)
	public static void physicalInputDeliveryAndWithdrawal(GameTestHelper h) {
		var c=controller(h); var pos=chest(h,c); var in=input(h,c); h.setBlock(new BlockPos(10,4,11),RatsBlockRegistry.RAT_STORAGE_OUTPUT.get()); var out=(RatStorageBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(10,4,11))); h.assertTrue(c.connect(out.getBlockPos()),"Output linked"); in.setItem(0,new ItemStack(Items.COBBLESTONE,64)); var rat=worker(h,c); var holder=new boolean[]{false};
		h.onEachTick(()-> { var inv=StorageRoutes.handler(h.getLevel(),pos,Direction.WEST); if(!holder[0] && StorageInventory.count(inv,new ItemStack(Items.COBBLESTONE))==64) { holder[0]=true; h.assertTrue(in.isEmpty() && rat.cargo.isEmpty(),"Physical whole-stack deposit"); h.assertTrue(c.request(UUID.randomUUID(),new ItemStack(Items.COBBLESTONE),32,out.getBlockPos(),-1),"Withdrawal accepted"); h.assertTrue(!c.request(UUID.randomUUID(),new ItemStack(Items.COBBLESTONE),40,out.getBlockPos(),-1),"Concurrent request cannot overbook"); } });
		h.succeedWhen(()-> { h.assertTrue(holder[0] && StorageInventory.count(out.inventory(),new ItemStack(Items.COBBLESTONE))==32,"Rat physically delivered withdrawal"); h.assertTrue(StorageInventory.count(StorageRoutes.handler(h.getLevel(),pos,Direction.WEST),new ItemStack(Items.COBBLESTONE))==32,"Exact remaining stock; no duplication"); });
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 1000)
	public static void parallelRatsDeliverWithoutBarrier(GameTestHelper h) {
		var c=controller(h); var pos=chest(h,c); var in=input(h,c); in.setItem(0,new ItemStack(Items.COBBLESTONE,64)); in.setItem(1,new ItemStack(Items.COBBLESTONE,64)); worker(h,c); worker(h,c);
		h.runAfterDelay(8,()->h.assertTrue(c.tasks.size()==2,"Two rats concurrently own tasks"));
		h.succeedWhen(()->h.assertTrue(StorageInventory.count(StorageRoutes.handler(h.getLevel(),pos,Direction.WEST),new ItemStack(Items.COBBLESTONE))==128 && in.isEmpty(),"Parallel deliveries completed"));
	}
	@GameTest(template = "quarry_test_empty")
	public static void oldDefaultsClaimsAndMissingTube(GameTestHelper h) {
		var c=controller(h); var pos=chest(h,c); var id=c.network; h.assertTrue(!StorageLedger.get(h.getLevel()).claim(StorageStock.identity(h.getLevel(),pos),UUID.randomUUID()),"Chest cannot belong to another controller");
		h.setBlock(new BlockPos(14,4,10),Blocks.AIR); h.assertTrue(StorageRoutes.port(h.getLevel(),pos,StorageRoutes.graph(h.getLevel(),c.getBlockPos()))==null && c.catalog().isEmpty(),"Broken route makes stock unavailable");
		var restored=new RatStorageBlockEntity(c.getBlockPos(),c.getBlockState()); restored.load(new net.minecraft.nbt.CompoundTag()); h.assertTrue(restored.workers.isEmpty() && restored.requests.isEmpty() && restored.isEmpty(),"Missing new tags use safe defaults"); h.succeed();
	}
}
