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
		helper.assertTrue(menu.slots.stream().noneMatch(slot -> slot.mayPickup(owner)), "Cage inspection must not allow item extraction");
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
