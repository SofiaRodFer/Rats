package com.github.alexthe666.rats.server.test;

import com.github.alexthe666.rats.registry.*;
import com.github.alexthe666.rats.server.block.entity.RatCageBreedingLanternBlockEntity;
import com.github.alexthe666.rats.server.entity.ai.goal.RatCageHabitatGoal;
import com.github.alexthe666.rats.server.entity.ai.goal.RatReturnToCageGoal;
import com.github.alexthe666.rats.server.entity.rat.RatCommand;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.inventory.RatCageMenu;
import com.github.alexthe666.rats.server.misc.CageHabitat;
import com.github.alexthe666.rats.server.misc.RatTreeUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.UUID;

@GameTestHolder("rats")
@PrefixGameTestTemplate(false)
public class CageGameTests {
	@GameTest(template = "quarry_test_empty")
	public static void evenHeadroomRejectsHorizontalRoutes(GameTestHelper helper) {
		for (int x=0;x<3;x++) for(int y=0;y<3;y++) for(int z=0;z<3;z++) helper.setBlock(CAGE.offset(x,y,z), RatsBlockRegistry.RAT_CAGE.get());
		com.github.alexthe666.rats.server.misc.CageLayout.synchronize(helper.getLevel(), habitat(helper));
		var a=helper.absolutePos(CAGE.offset(1,1,1)); var b=a.east();
		helper.assertTrue(!CageHabitat.canTraverse(helper.getLevel(),a,b), "Unsupported even headroom is not a horizontal walking floor");
		var resident=rat(helper,CAGE.offset(1,1,1),true);
		for(int i=0;i<30;i++) CageHabitat.settleUnsupported(resident);
		helper.assertTrue(resident.blockPosition().getY()==helper.absolutePos(CAGE).getY() && resident.isAlive(), "Old unsupported positions settle safely even with movement AI disabled"); helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void cageCargoAndUpgradeEditingIsAuthoritative(GameTestHelper helper) {
		cages(helper,2);var player=helper.makeMockPlayer();player.setPos(helper.absolutePos(CAGE).getCenter());
		var resident=rat(helper,CAGE,true);resident.setOwnerUUID(player.getUUID());
		var menu=new RatCageMenu(1,player.getInventory(),helper.absolutePos(CAGE));menu.action(player,0,resident.getId(),-1,"");
		helper.assertTrue(menu.slots.size()==49 && menu.slots.get(0).isActive() && !menu.slots.get(1).isActive() && menu.slots.get(0).getMaxStackSize()==1,"Ten cargo + three upgrade slots; disabled cargo cannot be used without capacity");
		player.getInventory().setItem(9,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE,64));menu.quickMoveStack(player,13);
		helper.assertTrue(resident.cargo.get(0).getCount()==1 && player.getInventory().getItem(9).getCount()==63,"Shift-click obeys unupgraded one-item capacity");
		var tray=new net.minecraft.world.item.ItemStack(RatsItemRegistry.RAT_UPGRADE_SILVER_TRAY.get());
		helper.assertTrue(menu.slots.get(10).mayPlace(tray),"Valid upgrade accepted");menu.slots.get(10).set(tray);
		helper.assertTrue(menu.slots.get(9).isActive() && menu.slots.get(9).getMaxStackSize()==64,"Capacity updates immediately after upgrade");
		menu.slots.get(9).set(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,12));
		helper.assertTrue(resident.cargo.get(9).getCount()==12 && !menu.slots.get(10).mayPickup(player),"Live cargo is editable; removing tray cannot discard cargo");
		helper.assertTrue(!menu.slots.get(11).mayPlace(tray),"Duplicate/conflicting upgrades rejected");
		var stranger=helper.makeMockPlayer();stranger.setUUID(UUID.randomUUID());
		var other=new RatCageMenu(2,stranger.getInventory(),helper.absolutePos(CAGE));stranger.setPos(player.position());other.action(stranger,0,resident.getId(),-1,"");
		helper.assertTrue(!other.slots.get(0).mayPickup(stranger) && !other.slots.get(10).mayPlace(tray),"Non-owner inspection cannot mutate inventory");
		resident.moveTo(helper.absolutePos(CAGE.east(5)).getCenter());
		helper.assertTrue(!menu.slots.get(0).mayPickup(player) && !menu.slots.get(10).mayPlace(tray),"Released or escaped resident immediately invalidates edits");helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void autoBreedingRespectsRatioCapacityAndOwnership(GameTestHelper helper) {
		cages(helper,12);helper.setBlock(CAGE,RatsBlockRegistry.RAT_CAGE_BREEDING_LANTERN.get().withPropertiesOf(helper.getLevel().getBlockState(helper.absolutePos(CAGE))));
		var lantern=(RatCageBreedingLanternBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(CAGE));
		var player=helper.makeMockPlayer();var adults=new ArrayList<TamedRat>();
		for(int i=0;i<9;i++){var resident=rat(helper,CAGE.east(i/2),i%2==0);resident.setOwnerUUID(player.getUUID());adults.add(resident);}
		helper.assertTrue(!lantern.isAutoBreeding() && lantern.toggleAutoBreed(player,habitat(helper)),"Automatic breeding defaults off and is opt-in");
		helper.assertTrue(!lantern.autoBreed(habitat(helper)),"Nine adults allow no baby");
		var tenth=rat(helper,CAGE.east(5),true);tenth.setOwnerUUID(player.getUUID());adults.add(tenth);
		helper.assertTrue(lantern.autoBreed(habitat(helper)),"Ten adults allow exactly one automatic baby");
		helper.assertTrue(habitat(helper).rats(helper.getLevel()).stream().filter(TamedRat::isBaby).count()==1,"Automatic breeding creates one, not a configured litter");
		var saved=lantern.saveWithoutMetadata();saved.putInt("BreedingCooldown",0);lantern.load(saved);adults.forEach(rat->rat.breedCooldown=0);
		helper.assertTrue(!lantern.autoBreed(habitat(helper)),"A living baby prevents repeated births beyond ratio even after cooldown");
		var stranger=helper.makeMockPlayer();stranger.setUUID(UUID.randomUUID());
		helper.assertTrue(!lantern.toggleAutoBreed(stranger,habitat(helper)),"Another player cannot change owned automation");
		helper.assertTrue(lantern.isAutoBreeding(),"Opt-in owner survives NBT reload");
		lantern.load(new CompoundTag());helper.assertTrue(!lantern.isAutoBreeding(),"Legacy NBT defaults off");helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void autoBreedingCannotOverflowOrUseForeignParents(GameTestHelper helper) {
		cages(helper,5);helper.setBlock(CAGE,RatsBlockRegistry.RAT_CAGE_BREEDING_LANTERN.get().withPropertiesOf(helper.getLevel().getBlockState(helper.absolutePos(CAGE))));
		var lantern=(RatCageBreedingLanternBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(CAGE));var player=helper.makeMockPlayer();
		for(int i=0;i<10;i++){var adult=rat(helper,CAGE.east(i/2),i%2==0);adult.setOwnerUUID(player.getUUID());}
		helper.assertTrue(lantern.toggleAutoBreed(player,habitat(helper)) && !lantern.autoBreed(habitat(helper)),"Ten adults meet ratio but a full cage cannot breed");
		helper.setBlock(CAGE.east(5),RatsBlockRegistry.RAT_CAGE.get());
		habitat(helper).rats(helper.getLevel()).stream().filter(TamedRat::isMale).forEach(rat->rat.setOwnerUUID(UUID.randomUUID()));
		helper.assertTrue(!lantern.autoBreed(habitat(helper)),"Available space does not authorize breeding another player's male");
		var resident=habitat(helper).rats(helper.getLevel()).stream().filter(TamedRat::isMale).findFirst().orElseThrow();resident.setOwnerUUID(player.getUUID());
		helper.assertTrue(lantern.autoBreed(habitat(helper)) && habitat(helper).rats(helper.getLevel()).size()==11,"Own pair fills exactly one available place");helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void cageRosterWireRoundTrip(GameTestHelper helper) {
		var packet=new com.github.alexthe666.rats.server.message.CageRosterPacket(3,12,18,true,0,10,64,true,true,java.util.List.of(new com.github.alexthe666.rats.server.message.CageRosterPacket.Resident(12,Component.literal("Luna"),false,false,true,0,false,"brown")));
		var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
		try { com.github.alexthe666.rats.server.message.CageRosterPacket.encode(packet,buffer);helper.assertTrue(packet.equals(com.github.alexthe666.rats.server.message.CageRosterPacket.decode(buffer)) && !buffer.isReadable(),"Cargo capacity, ownership and automation state round-trip exactly"); }
		finally { buffer.release(); } helper.succeed();
	}
	private static final BlockPos CAGE = new BlockPos(5, 4, 5);
	private static TamedRat rat(GameTestHelper helper, BlockPos pos, boolean male) {
		TamedRat rat = helper.spawn(RatsEntityRegistry.TAMED_RAT.get(), pos);
		rat.setPos(helper.absolutePos(pos).getX() + 0.5, helper.absolutePos(pos).getY() + 0.2, helper.absolutePos(pos).getZ() + 0.5);
		rat.setTame(true); rat.setNoAi(true); rat.setMale(male); rat.setCommand(RatCommand.WANDER);
		return rat;
	}
	private static CageHabitat habitat(GameTestHelper helper) { return CageHabitat.discover(helper.getLevel(), helper.absolutePos(CAGE)); }
	private static void cages(GameTestHelper helper, int count) {
		for (int x = 0; x < count; x++) helper.setBlock(CAGE.offset(x, 0, 0), RatsBlockRegistry.RAT_CAGE.get().defaultBlockState().setValue(com.github.alexthe666.rats.server.block.RatCageBlock.WEST, x > 0 ? 1 : 0).setValue(com.github.alexthe666.rats.server.block.RatCageBlock.EAST, x < count - 1 ? 1 : 0));
	}

	@GameTest(template = "quarry_test_empty", timeoutTicks = 240)
	public static void unsupportedUpperCageSettlesToFloor(GameTestHelper helper) {
		for (int y = 0; y < 2; y++) helper.setBlock(CAGE.above(y), RatsBlockRegistry.RAT_CAGE.get().defaultBlockState().setValue(com.github.alexthe666.rats.server.block.RatCageBlock.DOWN, y == 0 ? 0 : 1).setValue(com.github.alexthe666.rats.server.block.RatCageBlock.UP, y == 1 ? 0 : 1));
		var rat = rat(helper, CAGE.above(), true); rat.setNoAi(false);
		helper.runAfterDelay(180, () -> { helper.assertTrue(rat.isAlive() && rat.blockPosition().equals(helper.absolutePos(CAGE)) && Math.abs(rat.getY() - helper.absolutePos(CAGE).getY() - 0.125) < 0.025, "Rat rests on real bottom floor, not unsupported upper cells: " + rat.position()); helper.succeed(); });
	}
	@GameTest(template = "quarry_test_empty")
	public static void tierRoutesUseAccessInsteadOfSolidShelf(GameTestHelper helper) {
		for(int x=0;x<2;x++)for(int y=0;y<3;y++)for(int z=0;z<2;z++)helper.setBlock(CAGE.offset(x,y,z),RatsBlockRegistry.RAT_CAGE.get());
		com.github.alexthe666.rats.server.misc.CageLayout.synchronize(helper.getLevel(),habitat(helper));
		var rat=rat(helper,CAGE.offset(1,0,1),true);rat.getRandom().setSeed(42);var goal=new RatCageHabitatGoal(rat);goal.start();boolean upper=false,lower=false;
		for(int tick=0;tick<800;tick++){rat.tickCount++;goal.tick();if(rat.blockPosition().getY()==helper.absolutePos(CAGE.above(2)).getY())upper=true;if(upper&&rat.blockPosition().getY()==helper.absolutePos(CAGE).getY())lower=true;}
		helper.assertTrue(upper&&lower&&rat.isAlive(),"Routes must use ladder opening rather than collide with another floor tile: "+rat.position());goal.stop();helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void offsetTierGetsConnectedLadderPath(GameTestHelper helper) {
		for(var pos:java.util.List.of(CAGE,CAGE.above(),CAGE.east().above(),CAGE.east().above(2)))helper.setBlock(pos,RatsBlockRegistry.RAT_CAGE.get());
		var habitat=habitat(helper);com.github.alexthe666.rats.server.misc.CageLayout.synchronize(helper.getLevel(),habitat);
		helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(CAGE.east().above(2))).getValue(com.github.alexthe666.rats.server.block.RatCageBlock.LADDER),"Offset upper floors receive access, even without a three-block straight column");
		var rat=rat(helper,CAGE,true);rat.getRandom().setSeed(42);var goal=new RatCageHabitatGoal(rat);goal.start();boolean upper=false;
		for(int tick=0;tick<600;tick++){rat.tickCount++;goal.tick();if(rat.blockPosition().equals(helper.absolutePos(CAGE.east().above(2))))upper=true;}
		helper.assertTrue(upper&&rat.isAlive(),"Rat traverses offset cage ladder path: "+rat.position());goal.stop();helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void tierFloorKeepsVerticalTubePortOpen(GameTestHelper helper) {
		helper.setBlock(CAGE, RatsBlockRegistry.RAT_CAGE.get()); helper.setBlock(CAGE.above(), RatsBlockRegistry.RAT_TUBE_COLOR.get()); helper.setBlock(CAGE.above(2), RatsBlockRegistry.RAT_CAGE.get());
		var habitat = habitat(helper); com.github.alexthe666.rats.server.misc.CageLayout.synchronize(helper.getLevel(), habitat);
		helper.assertTrue(habitat.capacity() == 4, "Tube transit does not count as capacity");
		var state = helper.getLevel().getBlockState(helper.absolutePos(CAGE.above(2)));
		helper.assertTrue(state.getValue(com.github.alexthe666.rats.server.block.RatCageBlock.DOWN) == 2 && state.getValue(com.github.alexthe666.rats.server.block.RatCageBlock.SHELF), "Upper odd floor retains the tube connection");
		var shape = state.getCollisionShape(helper.getLevel(), helper.absolutePos(CAGE.above(2)));
		helper.assertTrue(!shape.toAabbs().stream().anyMatch(box -> box.contains(0.5,0.0625,0.5)), "Tube port is not sealed by the generated shelf");
		var rat = rat(helper, CAGE, true); rat.getRandom().setSeed(42); var goal = new RatCageHabitatGoal(rat); goal.start(); boolean upper=false, lower=false;
		for (int tick=0; tick<600; tick++) { rat.tickCount++; goal.tick(); if(rat.blockPosition().equals(helper.absolutePos(CAGE.above(2)))) upper=true; if(upper && rat.blockPosition().equals(helper.absolutePos(CAGE))) lower=true; }
		helper.assertTrue(upper && lower && rat.isAlive(), "Rat traverses the vertical tube in both directions: "+rat.position());goal.stop();helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void legacyCagePaletteAndDecorationSurviveLayout(GameTestHelper helper) {
		var palette = net.minecraft.nbt.NbtUtils.writeBlockState(RatsBlockRegistry.RAT_CAGE.get().defaultBlockState());
		palette.getCompound("Properties").remove("shelf"); palette.getCompound("Properties").remove("ladder"); palette.getCompound("Properties").putString("future_test_property", "true");
		var restored = net.minecraft.nbt.NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), palette);
		helper.assertTrue(restored.is(RatsBlockRegistry.RAT_CAGE.get()) && !restored.getValue(com.github.alexthe666.rats.server.block.RatCageBlock.SHELF), "Old palettes and unknown optional properties preserve the existing block ID");
		for (int y = 0; y < 3; y++) for (int x = 0; x < 2; x++) helper.setBlock(CAGE.offset(x, y, 0), restored);
		helper.setBlock(CAGE.offset(1, 2, 0), RatsBlockRegistry.RAT_CAGE_DECORATED.get().withPropertiesOf(helper.getLevel().getBlockState(helper.absolutePos(CAGE.offset(1, 2, 0)))));
		var deco = (com.github.alexthe666.rats.server.block.entity.DecoratedRatCageBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(CAGE.offset(1, 2, 0)));
		var igloo = new net.minecraft.world.item.ItemStack(RatsItemRegistry.RAT_IGLOOS[0].get()); igloo.setHoverName(Component.literal("Old house")); deco.setContainedItem(igloo);
		com.github.alexthe666.rats.server.misc.CageLayout.synchronize(helper.getLevel(), habitat(helper));
		helper.assertTrue(helper.getLevel().getBlockEntity(deco.getBlockPos()) == deco && net.minecraft.world.item.ItemStack.matches(igloo, deco.getContainedItem()), "Layout refresh must retain decorated block entity and contained item NBT"); helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void oddTierCapacityAndShelves(GameTestHelper helper) {
		for (int height = 1; height <= 7; height++) {
			for (int x = 0; x < 2; x++) helper.setBlock(CAGE.offset(x, height - 1, 0), RatsBlockRegistry.RAT_CAGE.get());
			var habitat = habitat(helper); com.github.alexthe666.rats.server.misc.CageLayout.synchronize(helper.getLevel(), habitat);
			helper.assertTrue(habitat.capacity() == 4 * ((height + 1) / 2), "Only levels 1,3,5... add capacity");
			for (int y = 1; y < height; y++) for (int x = 0; x < 2; x++) {
				var state = helper.getLevel().getBlockState(helper.absolutePos(CAGE.offset(x, y, 0)));
				helper.assertTrue(state.getValue(com.github.alexthe666.rats.server.block.RatCageBlock.SHELF) == (y % 2 == 0), "Upper odd tiers get floors; even tiers add headroom");
			}
		}
		helper.setBlock(CAGE.above(6), Blocks.AIR); helper.setBlock(CAGE.offset(1, 6, 0), Blocks.AIR);
		helper.assertTrue(habitat(helper).capacity() == 12, "Removing a counted level updates capacity without a stale cache"); helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void ladderAccessConnectsOddFloors(GameTestHelper helper) {
		for (int y = 0; y < 3; y++) helper.setBlock(CAGE.above(y), RatsBlockRegistry.RAT_CAGE.get());
		var habitat = habitat(helper); com.github.alexthe666.rats.server.misc.CageLayout.synchronize(helper.getLevel(), habitat);
		var rat = rat(helper, CAGE, true); var goal = new RatCageHabitatGoal(rat); goal.start(); boolean upper = false, lower = false;
		for (int tick = 0; tick < 400; tick++) { rat.tickCount++; goal.tick(); if (rat.blockPosition().equals(helper.absolutePos(CAGE.above(2))) && Math.abs(rat.getY() - helper.absolutePos(CAGE.above(2)).getY() - 0.125) < 0.02) upper = true; if (upper && rat.blockPosition().equals(helper.absolutePos(CAGE))) lower = true; }
		helper.assertTrue(upper && lower && rat.isAlive(), "Rat climbs through access opening and returns without false falling: " + rat.position());
		helper.assertTrue(habitat.capacity() == 4, "Three single-block levels supply four spaces"); goal.stop(); helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void capacityShrinkPreservesExistingResidents(GameTestHelper helper) {
		for (int y = 0; y < 2; y++) helper.setBlock(CAGE.above(y), RatsBlockRegistry.RAT_CAGE.get());
		var first = rat(helper, CAGE, true); var second = rat(helper, CAGE, false); var oldUpper = rat(helper, CAGE.above(), true); var outside = rat(helper, CAGE.east(3), true);
		var habitat = habitat(helper); com.github.alexthe666.rats.server.misc.CageLayout.synchronize(helper.getLevel(), habitat);
		helper.assertTrue(habitat.capacity() == 2 && habitat.rats(helper.getLevel()).size() == 3 && first.isAlive() && second.isAlive() && oldUpper.isAlive(), "Old over-capacity residents remain intact");
		helper.assertTrue(!habitat.admit(outside, helper.absolutePos(CAGE)), "New admission waits until capacity is available"); helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void shoulderReleaseMovesRatOffPlayer(GameTestHelper helper) {
		var player = helper.makeMockPlayer(); player.setPos(helper.absolutePos(CAGE).getCenter());
		var rat = rat(helper, CAGE, true); rat.setOwnerUUID(player.getUUID()); rat.startRiding(player, true); rat.setPos(player.getX(), player.getY() + 1.4, player.getZ());
		helper.assertTrue(com.github.alexthe666.rats.server.message.DismountRatPacket.dismount(player, rat), "Owner can detach passenger");
		helper.assertTrue(!rat.isPassenger() && rat.getY() == player.getY() && rat.distanceToSqr(player) >= 0.9 && rat.fallDistance == 0, "Release moves to feet, not head");
		helper.assertTrue(!com.github.alexthe666.rats.server.message.DismountRatPacket.dismount(player, rat), "Stale requests must not move released rats"); helper.succeed();
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 240)
	public static void multiLevelCageDoesNotAccumulateFall(GameTestHelper helper) {
		for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) for (int z = 0; z < 3; z++) helper.setBlock(CAGE.offset(x, y, z), RatsBlockRegistry.RAT_CAGE.get());
		TamedRat rat = rat(helper, CAGE.above(2), true);
		rat.setNoAi(false); rat.fallDistance = 80;
		float health = rat.getHealth();
		helper.assertTrue(!rat.causeFallDamage(80, 1, rat.damageSources().fall()), "Habitat must reject stale falling damage");
		helper.runAfterDelay(180, () -> {
			helper.assertTrue(rat.isAlive() && rat.getHealth() == health, "Ordinary rat must survive multi-level habitat traversal");
			helper.assertTrue(rat.isInCage() && rat.fallDistance == 0 && rat.isNoGravity(), "Habitat traversal must not accumulate falls");
			helper.succeed();
		});
	}
	@GameTest(template = "quarry_test_empty")
	public static void connectedTubesAndTopology(GameTestHelper helper) {
		cages(helper, 2);
		helper.setBlock(CAGE.offset(2, 0, 0), RatsBlockRegistry.RAT_TUBE_COLOR.get());
		helper.setBlock(CAGE.offset(3, 0, 0), RatsBlockRegistry.RAT_CAGE.get());
		CageHabitat habitat = habitat(helper);
		helper.assertTrue(habitat.complete() && habitat.nodes().size() == 4 && habitat.capacity() == 6, "Tubes must connect but not add capacity");
		helper.setBlock(CAGE.offset(2, 0, 0), Blocks.AIR);
		helper.assertTrue(habitat(helper).capacity() == 4, "Topology must refresh after a split");
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void capacityCountsBabies(GameTestHelper helper) {
		cages(helper, 1);
		rat(helper, CAGE, true); TamedRat baby = rat(helper, CAGE, false); baby.setAge(-24000);
		TamedRat outside = rat(helper, CAGE.offset(3, 0, 0), true);
		helper.assertTrue(!habitat(helper).hasRoom(helper.getLevel()) && !habitat(helper).admit(outside, helper.absolutePos(CAGE)), "Babies count and overflow admission must fail");
		helper.assertTrue(habitat(helper).rats(helper.getLevel()).size() == 2, "Overflow must not delete existing rats: " + habitat(helper).rats(helper.getLevel()).stream().map(rat -> rat.position().toString()).toList() + "; baby=" + baby.position());
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void breedingValidationAndCapacity(GameTestHelper helper) {
		cages(helper, 3);
		helper.setBlock(CAGE, RatsBlockRegistry.RAT_CAGE_BREEDING_LANTERN.get());
		RatCageBreedingLanternBlockEntity lantern = (RatCageBreedingLanternBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(CAGE));
		TamedRat female = rat(helper, CAGE, false), male = rat(helper, CAGE.offset(1, 0, 0), true);
		helper.assertTrue(!lantern.breed(habitat(helper), female, female), "Self/same-sex breeding must fail");
		male.setAge(-24000);
		helper.assertTrue(!lantern.breed(habitat(helper), female, male), "Babies must not breed"); male.setAge(0);
		helper.assertTrue(lantern.breed(habitat(helper), female, male), "Valid selected pair must breed");
		int residents = habitat(helper).rats(helper.getLevel()).size();
		helper.assertTrue(residents > 2 && residents <= 6, "Litter must remain inside capacity");
		helper.assertTrue(!lantern.breed(habitat(helper), female, male), "Cooldown must prevent duplicate litter");
		CompoundTag saved = lantern.saveWithoutMetadata();
		RatCageBreedingLanternBlockEntity restored = new RatCageBreedingLanternBlockEntity(lantern.getBlockPos(), lantern.getBlockState()); restored.load(saved);
		helper.assertTrue(restored.getBreedingCooldown() > 0 && male.breedCooldown > 0 && female.breedCooldown > 0, "Cooldowns must persist");
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void fullHabitatCannotBreed(GameTestHelper helper) {
		helper.setBlock(CAGE, RatsBlockRegistry.RAT_CAGE_BREEDING_LANTERN.get());
		TamedRat female = rat(helper, CAGE, false), male = rat(helper, CAGE, true);
		RatCageBreedingLanternBlockEntity lantern = (RatCageBreedingLanternBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(CAGE));
		helper.assertTrue(!lantern.breed(habitat(helper), female, male), "Full cage must not create babies");
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void safeRelease(GameTestHelper helper) {
		cages(helper, 1); TamedRat rat = rat(helper, CAGE, true);
		helper.assertTrue(!habitat(helper).release(rat, helper.absolutePos(CAGE)), "No floor means no safe release");
		for (int x = 1; x <= 9; x++) for (int z = 1; z <= 9; z++) helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
		helper.assertTrue(habitat(helper).release(rat, helper.absolutePos(CAGE)) && !rat.isInCage(), "Release must choose a safe exterior position");
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void menuOwnershipAndNames(GameTestHelper helper) {
		cages(helper, 1); TamedRat rat = rat(helper, CAGE, true);
		Player owner = helper.makeMockPlayer(); owner.setPos(rat.getX(), rat.getY(), rat.getZ()); rat.setOwnerUUID(owner.getUUID());
		RatCageMenu menu = new RatCageMenu(7, owner.getInventory(), helper.absolutePos(CAGE));
		menu.action(owner, 5, rat.getId(), -1, "Luna");
		helper.assertTrue(rat.getName().getString().equals("Luna"), "Owner should rename resident");
		menu.action(owner, 5, rat.getId(), -1, "bad\nname");
		helper.assertTrue(rat.getName().getString().equals("Luna"), "Control characters must be rejected");
		menu.action(owner, 2, rat.getId(), -1, "");
		helper.assertTrue(rat.getCageHome().isPresent(), "Owner should assign home");
		rat.setOwnerUUID(UUID.randomUUID()); menu.action(owner, 5, rat.getId(), -1, "Stolen");
		helper.assertTrue(rat.getName().getString().equals("Luna"), "Non-owner mutations must fail");
		helper.assertTrue(menu.slots.subList(0,13).stream().noneMatch(slot -> slot.mayPickup(owner)), "Non-owner inspection must not allow extraction from rat cargo/upgrades (player slots remain usable)");
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void homePersistenceAndCorruptDirections(GameTestHelper helper) {
		TamedRat rat = rat(helper, CAGE, true);
		GlobalPos home = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(CAGE)); rat.setCageHome(home);
		CompoundTag saved = new CompoundTag(); rat.addAdditionalSaveData(saved);
		saved.putInt("DepositFacing", Integer.MAX_VALUE); saved.putInt("PickupFacing", -99); saved.putInt("Command", 99);
		rat.setCageHome(null); rat.readAdditionalSaveData(saved);
		helper.assertTrue(rat.getCageHome().filter(home::equals).isPresent(), "Home must survive NBT roundtrip");
		helper.assertTrue(rat.getCommandInteger() < RatCommand.values().length, "Corrupt command must be clamped");
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void navigatorDoesNotChurn(GameTestHelper helper) {
		TamedRat rat = rat(helper, CAGE, true); rat.switchNavigator(0);
		var navigation = rat.getNavigation(); rat.switchNavigator(0);
		helper.assertTrue(rat.getNavigation() == navigation, "Repeated mode selection must preserve active navigator");
		rat.switchNavigator(5); helper.assertTrue(rat.getNavigation() != navigation, "Actual mode change should switch navigator");
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void wanderReturnsHome(GameTestHelper helper) {
		cages(helper, 1); TamedRat rat = rat(helper, CAGE.offset(1, 0, 0), true);
		rat.setCageHome(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(CAGE)));
		RatReturnToCageGoal goal = new RatReturnToCageGoal(rat);
		helper.assertTrue(goal.canUse(), "Wander should return to loaded home with room"); goal.start(); goal.tick();
		helper.assertTrue(rat.isInCage(), "Rat near entrance should enter assigned home");
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 200)
	public static void shoulderAdmissionClearsMotionAndStaysInside(GameTestHelper helper) {
		cages(helper, 2);
		Player player = helper.makeMockPlayer(); player.setPos(helper.absolutePos(CAGE.west()).getCenter());
		TamedRat rat = rat(helper, CAGE.west(), true); rat.setOwnerUUID(player.getUUID());
		helper.assertTrue(rat.startRiding(player, true), "Fixture rat must actually ride a player");
		var packets = new ArrayList<net.minecraft.network.protocol.Packet<?>>();
		var tracker = new net.minecraft.server.level.ServerEntity(helper.getLevel(), rat, 1, true, packets::add);
		tracker.sendChanges(); packets.clear();
		helper.assertTrue(com.github.alexthe666.rats.server.message.DismountRatPacket.canDismount(player, rat), "Owner may dismount shoulder rat");
		rat.setDeltaMovement(0, 2, 0);
		helper.assertTrue(habitat(helper).admit(rat, helper.absolutePos(CAGE)), "Shoulder admission must succeed");
		helper.assertTrue(!rat.isPassenger() && rat.getDeltaMovement().lengthSqr() == 0, "Admission must clear inherited shoulder velocity");
		helper.assertTrue(!com.github.alexthe666.rats.server.message.DismountRatPacket.canDismount(player, rat), "Late shoulder packet must not move an already admitted rat");
		tracker.sendChanges();
		helper.assertTrue(packets.stream().anyMatch(packet -> packet instanceof net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket), "Vanilla tracker must send absolute cage position after dismount");
		rat.setNoAi(false);
		helper.runAtTickTime(120, () -> {
			helper.assertTrue(rat.isAlive() && rat.isInCage() && habitat(helper).rats(helper.getLevel()).contains(rat), "Shoulder rat must stay visible in habitat after live ticks: " + rat.position());
			helper.succeed();
		});
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 200)
	public static void liveCageMovement(GameTestHelper helper) {
		cages(helper, 3); TamedRat rat = rat(helper, CAGE, true); rat.setCommand(RatCommand.SIT); rat.setNoAi(false);
		RatCageHabitatGoal goal = new RatCageHabitatGoal(rat);
		helper.assertTrue(goal.canUse(), "SIT rats should move freely inside cage");
		var initial = rat.position();
		double[] maximumDistance = {0};
		for (int tick = 10; tick <= 110; tick += 10) helper.runAtTickTime(tick, () -> maximumDistance[0] = Math.max(maximumDistance[0], rat.position().distanceToSqr(initial)));
		helper.runAtTickTime(120, () -> { helper.assertTrue(rat.isInCage() && maximumDistance[0] > 0.5, "Live rat should cross cage cells without escaping: maximum distance squared=" + maximumDistance[0] + ", now=" + rat.position() + ", goals=" + rat.getWorkDebugState()); helper.succeed(); });
	}
	@GameTest(template = "quarry_test_empty")
	public static void treeSearchHandlesCycles(GameTestHelper helper) {
		for (int x = 3; x <= 10; x++) for (int y = 3; y <= 9; y++) for (int z = 3; z <= 10; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG);
		var logs = RatTreeUtils.getLogsToBreak(helper.getLevel(), helper.absolutePos(new BlockPos(3, 3, 3)), new ArrayList<>(), Blocks.OAK_LOG.defaultBlockState());
		helper.assertTrue(logs.size() == 448 && logs.stream().distinct().count() == 448, "Iterative tree traversal must terminate and deduplicate cycles");
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void oversizedHabitatFailsClosed(GameTestHelper helper) {
		for (int x = 2; x < 14; x++) for (int y = 1; y < 9; y++) for (int z = 2; z < 14; z++) helper.setBlock(new BlockPos(x, y, z), RatsBlockRegistry.RAT_CAGE.get());
		CageHabitat habitat = CageHabitat.discover(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)));
		helper.assertTrue(!habitat.complete() && habitat.nodes().size() == CageHabitat.MAX_BLOCKS && !habitat.hasRoom(helper.getLevel()), "Oversized cyclic habitats must terminate and refuse admission");
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void deferredDropsDoNotStarveHarvesting(GameTestHelper helper) {
		TamedRat rat = rat(helper, CAGE, true); rat.setCommand(RatCommand.HARVEST);
		var item = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), rat.getX() + 1, rat.getY(), rat.getZ(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
		item.setOnGround(true); helper.getLevel().addFreshEntity(item);
		rat.deferredItemTargets.put(item.getUUID(), rat.tickCount + 600);
		var harvest = new com.github.alexthe666.rats.server.entity.ai.goal.harvest.RatHarvestCropsGoal(rat);
		helper.assertTrue(harvest.checkTheBasics(true, false), "Temporarily deferred drops must not indefinitely prevent harvesting");
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void houseRestAndShoulderAdmissionSpread(GameTestHelper helper) {
		cages(helper, 2);
		rat(helper, CAGE, true); rat(helper, CAGE, false);
		TamedRat outside = rat(helper, CAGE.offset(3, 0, 0), true);
		helper.assertTrue(habitat(helper).admit(outside, helper.absolutePos(CAGE)) && outside.blockPosition().equals(helper.absolutePos(CAGE.east())), "Admission should spread rats instead of cramming clicked cell");
		helper.setBlock(CAGE.east(), RatsBlockRegistry.RAT_CAGE_DECORATED.get());
		var deco = (com.github.alexthe666.rats.server.block.entity.DecoratedRatCageBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(CAGE.east()));
		deco.setContainedItem(new net.minecraft.world.item.ItemStack(RatsItemRegistry.RAT_IGLOOS[0].get()));
		// A single-cell habitat makes the decoration visit deterministic.
		helper.setBlock(CAGE, Blocks.AIR);
		RatCageHabitatGoal goal = new RatCageHabitatGoal(outside); goal.start(); goal.tick();
		helper.assertTrue(outside.isCageResting(), "House visit should start resting"); goal.stop();
		helper.assertTrue(!outside.isCageResting(), "Goal stop must clear rest state");
		helper.succeed();
	}
}
