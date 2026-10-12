package com.github.alexthe666.rats.server.test;

import com.github.alexthe666.rats.registry.*;
import com.github.alexthe666.rats.server.block.entity.RatQuarryBlockEntity;
import com.github.alexthe666.rats.server.entity.rat.RatCommand;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.inventory.RatQuarryMenu;
import com.github.alexthe666.rats.server.misc.QuarryGeometry;
import com.github.alexthe666.rats.server.misc.QuarrySafety;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;

import java.util.HashSet;
import java.util.List;

@GameTestHolder("rats")
@PrefixGameTestTemplate(false)
public class QuarryGameTests {
	@GameTest(template = "quarry_test_empty")
	public static void buriedSavedOreDoesNotPathToItself(GameTestHelper helper) {
		var quarry = quarry(helper, true); quarry.configure(1); var worker = rat(helper, true);
		worker.setItemSlot(EquipmentSlot.LEGS, new ItemStack(RatsItemRegistry.RAT_UPGRADE_QUARRY.get())); worker.setItemSlot(EquipmentSlot.FEET, new ItemStack(RatsItemRegistry.RAT_UPGRADE_ORE_HUNTER.get()));
		worker.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos())); worker.setCommand(RatCommand.HARVEST); worker.moveTo(quarry.getBlockPos().above().getCenter());
		var ore = quarry.getBlockPos().offset(8, 0, 0);
		for (var direction : Direction.values()) helper.getLevel().setBlockAndUpdate(ore.relative(direction), Blocks.STONE.defaultBlockState());
		helper.getLevel().setBlockAndUpdate(ore, Blocks.IRON_ORE.defaultBlockState());
		var saved = new CompoundTag(); saved.putLong("Quarry", quarry.getBlockPos().asLong()); saved.putLongArray("Targets", new long[]{ore.asLong()}); worker.oreVein.load(saved);
		var goal = new com.github.alexthe666.rats.server.entity.ai.goal.harvest.RatQuarryGoal(worker); helper.assertTrue(goal.canUse(), "Saved ore selected"); goal.start(); goal.tick();
		helper.assertTrue(worker.oreVein.targets(quarry.getBlockPos()).isEmpty() && !worker.quarryReturning, "Buried ore must not hold a self-path reservation or request an empty return"); goal.stop(); worker.setCommand(RatCommand.SIT); helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void quarryKeepsMiningSessionBetweenBlocks(GameTestHelper helper) {
		var quarry = quarry(helper, true); quarry.configure(1);
		var worker = rat(helper, true);
		worker.setItemSlot(EquipmentSlot.LEGS, new ItemStack(RatsItemRegistry.RAT_UPGRADE_QUARRY.get()));
		worker.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos())); worker.setCommand(RatCommand.HARVEST);
		worker.moveTo(quarry.getBlockPos().above().getCenter());
		for (int col = 0; col < quarry.getWidth(); col++) {
			helper.getLevel().setBlockAndUpdate(quarry.cell(0, col, -1), Blocks.STONE.defaultBlockState());
			helper.getLevel().setBlockAndUpdate(quarry.cell(0, col, 0), Blocks.COBBLESTONE.defaultBlockState());
		}
		var goal = new com.github.alexthe666.rats.server.entity.ai.goal.harvest.RatQuarryGoal(worker);
		helper.assertTrue(goal.canUse(), "First target available"); goal.start();
		for (int i = 0; i < 30; i++) goal.tick();
		helper.assertTrue(!worker.cargo.isEmpty() && goal.canContinueToUse(), "Breaking one block must retain the mining session and lease for the next block");
		for (int i = 0; i < 30; i++) goal.tick();
		helper.assertTrue(worker.cargo.get(0).getCount() >= 2 && !worker.quarryReturning, "Tray accumulates cargo without a return after each block"); goal.stop(); worker.setCommand(RatCommand.SIT); helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void blockedFrontDoesNotRecallPartialCargo(GameTestHelper helper) {
		var quarry = quarry(helper, false); quarry.configure(1);
		var worker = rat(helper, true); worker.setCommand(RatCommand.HARVEST);
		worker.setItemSlot(EquipmentSlot.LEGS, new ItemStack(RatsItemRegistry.RAT_UPGRADE_QUARRY.get()));
		worker.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos())); worker.cargo.insert(new ItemStack(Items.COBBLESTONE), false);
		for (int depth = 0; depth < 8; depth++) for (int col = 0; col < quarry.getWidth(); col++) {
			helper.getLevel().setBlockAndUpdate(quarry.cell(depth, col, -1), Blocks.STONE.defaultBlockState());
			helper.getLevel().setBlockAndUpdate(quarry.cell(depth, col, 0), Blocks.CHEST.defaultBlockState());
		}
		var goal = new com.github.alexthe666.rats.server.entity.ai.goal.harvest.RatQuarryGoal(worker);
		helper.assertTrue(!goal.canUse() && !worker.quarryReturning, "Blocked/shared work must not convert a partial inventory into an unsolicited deposit"); worker.setCommand(RatCommand.SIT); helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void loadedRetiredWorkersDoNotOccupyCrewSlots(GameTestHelper helper) {
		var quarry = quarry(helper, false);
		for (int i = 0; i < 2; i++) {
			var worker = rat(helper, true); worker.setItemSlot(EquipmentSlot.LEGS, new ItemStack(RatsItemRegistry.RAT_UPGRADE_QUARRY.get()));
			worker.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos())); helper.assertTrue(quarry.assignWorker(worker), "Assign worker");
			worker.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
		}
		var replacement = rat(helper, true); replacement.setItemSlot(EquipmentSlot.LEGS, new ItemStack(RatsItemRegistry.RAT_UPGRADE_QUARRY.get()));
		replacement.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos()));
		helper.assertTrue(quarry.assignWorker(replacement) && quarry.workerIds().size() == 1, "Loaded rats without quarry upgrades must not permanently exhaust legacy crew slots"); helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void oreReturnCheckpointIsConsumed(GameTestHelper helper) {
		var quarry = quarry(helper, false); quarry.configure(1);
		var worker = rat(helper, true); worker.quarryReturning = true;
		worker.oreVein.rememberReturnPoint(worker.position());
		com.github.alexthe666.rats.server.misc.QuarryTravel.moveTo(worker, quarry, Vec3.atBottomCenterOf(quarry.getBlockPos().above()));
		helper.assertTrue(worker.oreVein.returnPoint() == null, "Reached return point must be consumed before heading to quarry, preventing anchor/quarry oscillation");
		worker.oreVein.rememberReturnPoint(new Vec3(worker.getX(), worker.getY() + 15, worker.getZ()));
		com.github.alexthe666.rats.server.misc.QuarryTravel.moveTo(worker, quarry, Vec3.atBottomCenterOf(quarry.getBlockPos().above()));
		helper.assertTrue(worker.oreVein.returnPoint() == null, "Inaccessible saved checkpoint must not trap the rat"); helper.succeed();
	}
	private static TamedRat rat(GameTestHelper helper, boolean tray) {
		TamedRat rat = helper.spawn(RatsEntityRegistry.TAMED_RAT.get(), new BlockPos(4, 4, 4));
		rat.setTame(true);
		rat.setNoAi(true);
		if (tray) rat.setItemSlot(EquipmentSlot.CHEST, new ItemStack(RatsItemRegistry.RAT_UPGRADE_SILVER_TRAY.get()));
		return rat;
	}
	private static RatQuarryBlockEntity quarry(GameTestHelper helper, boolean advanced) {
		BlockPos position = new BlockPos(12, 4, 20);
		helper.setBlock(position, advanced ? RatsBlockRegistry.ADVANCED_RAT_QUARRY.get() : RatsBlockRegistry.RAT_QUARRY.get());
		return (RatQuarryBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(position));
	}
	@GameTest(template = "quarry_test_empty")
	public static void auditLeaseAndLegacyExtent(GameTestHelper helper) {
		var quarry = quarry(helper, false); CompoundTag legacy = quarry.saveWithoutMetadata();
		legacy.putIntArray("QuarryProgress", new int[]{0, 8, 0, 0, 0}); legacy.putInt("QuarryDirection", 4); legacy.remove("QuarryBuiltExtent"); legacy.remove("QuarryWidth"); quarry.load(legacy);
		helper.assertTrue(quarry.getProgress() == 0 && quarry.historicalExtent(Direction.NORTH) == 8, "Old narrow progress is retained for audits even after widening resets work");
		for (int depth = -1; depth < 8; depth++) for (int col = 0; col < 5; col++) helper.getLevel().setBlockAndUpdate(com.github.alexthe666.rats.server.misc.QuarryGeometry.cell(quarry.getBlockPos(), Direction.NORTH, 5, depth, col, -1), Blocks.STONE.defaultBlockState());
		BlockPos hole = com.github.alexthe666.rats.server.misc.QuarryGeometry.cell(quarry.getBlockPos(), Direction.NORTH, 5, 2, 0, -1); helper.getLevel().setBlockAndUpdate(hole, Blocks.AIR.defaultBlockState());
		quarry.scanMaintenance(); var first = rat(helper, true); var second = rat(helper, true);
		for (var worker : java.util.List.of(first, second)) worker.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos()));
		var task = quarry.claimMaintenance(first); helper.assertTrue(task != null && task.direction() == Direction.NORTH && task.target().equals(hole), "Audit covers inactive old direction");
		helper.assertTrue(quarry.claimMaintenance(second) == null, "Only one rat owns a repair"); quarry.finishMaintenance(first, false);
		var saved = quarry.saveWithoutMetadata(); var restored = new RatQuarryBlockEntity(quarry.getBlockPos(), quarry.getBlockState()); restored.load(saved);
		helper.assertTrue(restored.historicalExtent(Direction.NORTH) == 8 && restored.getDirectionIndex() == 4, "Optional history/cursor survives reload without changing configuration"); helper.succeed();
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 2000)
	public static void ratReturnsToRepairOlderTunnel(GameTestHelper helper) {
		var quarry = quarry(helper, false); quarry.setItem(0, new ItemStack(Items.DIAMOND, 13)); var legacy = quarry.saveWithoutMetadata();
		legacy.putIntArray("QuarryProgress", new int[]{0, 8, 0, 0, 16}); legacy.putInt("QuarryDirection", 4); legacy.putInt("QuarryLength", 16); legacy.remove("QuarryBuiltExtent"); quarry.load(legacy);
		for (int depth = -1; depth < 8; depth++) for (int col = 0; col < 5; col++) helper.getLevel().setBlockAndUpdate(com.github.alexthe666.rats.server.misc.QuarryGeometry.cell(quarry.getBlockPos(), Direction.NORTH, 5, depth, col, -1), Blocks.STONE.defaultBlockState());
		int damaged = 4;
		BlockPos hole = com.github.alexthe666.rats.server.misc.QuarryGeometry.cell(quarry.getBlockPos(), Direction.NORTH, 5, damaged, 0, -1); helper.getLevel().setBlockAndUpdate(hole, Blocks.AIR.defaultBlockState());
		for (int col = 0; col < 5; col++) helper.getLevel().setBlockAndUpdate(com.github.alexthe666.rats.server.misc.QuarryGeometry.cell(quarry.getBlockPos(), Direction.NORTH, 5, damaged, col, 5), Blocks.STONE.defaultBlockState());
		for (int col : new int[]{-1, 5}) helper.getLevel().setBlockAndUpdate(com.github.alexthe666.rats.server.misc.QuarryGeometry.cell(quarry.getBlockPos(), Direction.NORTH, 5, damaged, col, 1), Blocks.STONE.defaultBlockState());
		quarry.scanMaintenance(); quarry.scanMaintenance();
		var worker = rat(helper, true); worker.setItemSlot(EquipmentSlot.LEGS, new ItemStack(RatsItemRegistry.RAT_UPGRADE_QUARRY.get())); worker.setCommand(RatCommand.HARVEST); worker.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos())); worker.moveTo(quarry.getBlockPos().above().getCenter()); worker.setNoAi(false);
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getLevel().getBlockState(hole).is(RatsBlockRegistry.RAT_QUARRY_PLATFORM.get()), "Rat physically returns to fix a floor hole: " + worker.getWorkDebugState());
			for (int col : new int[]{0, 4}) helper.assertTrue(helper.getLevel().getBlockState(com.github.alexthe666.rats.server.misc.QuarryGeometry.cell(quarry.getBlockPos(), Direction.NORTH, 5, damaged, col, 1)).is(RatsBlockRegistry.RAT_QUARRY_TORCH.get()), "Missing older lights restored too");
			helper.assertTrue(quarry.getDirectionIndex() == 4 && quarry.getItem(0).is(Items.DIAMOND) && quarry.getItem(0).getCount() == 13, "Maintenance preserves active direction and old inventory"); worker.setCommand(RatCommand.SIT);
		});
	}
	@GameTest(template = "quarry_test_empty")
	public static void maintenancePreservesPlayerBlocksAndProtection(GameTestHelper helper) {
		var quarry = quarry(helper, false); var worker = rat(helper, true);
		BlockPos floor = com.github.alexthe666.rats.server.misc.QuarryGeometry.cell(quarry.getBlockPos(), Direction.NORTH, 5, 0, 0, -1);
		helper.getLevel().setBlockAndUpdate(floor, Blocks.CHEST.defaultBlockState());
		helper.assertTrue(QuarrySafety.ensureHorizontalFloor(quarry, worker, Direction.NORTH, 0), "Existing player support is valid"); helper.assertTrue(helper.getLevel().getBlockState(floor).is(Blocks.CHEST), "Do not remove player chest support");
		BlockPos protectedFloor = com.github.alexthe666.rats.server.misc.QuarryGeometry.cell(quarry.getBlockPos(), Direction.NORTH, 5, 1, 0, -1);
		var rules = helper.getLevel().getGameRules(); boolean before = rules.getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
		rules.getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(false, helper.getLevel().getServer());
		try { helper.assertTrue(!QuarrySafety.ensureHorizontalFloor(quarry, worker, Direction.NORTH, 1) && helper.getLevel().getBlockState(protectedFloor).isAir(), "Protected holes remain unchanged"); }
		finally { rules.getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(before, helper.getLevel().getServer()); }
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void downShaftGetsFourCheeseLights(GameTestHelper helper) {
		var quarry = quarry(helper, false); var worker = rat(helper, false); int depth = 4, width = quarry.getWidth();
		for (int col = -1; col <= width; col++) for (int row = -1; row <= width; row++) if (col == -1 || col == width || row == -1 || row == width) helper.getLevel().setBlockAndUpdate(quarry.cell(depth, col, row), Blocks.STONE.defaultBlockState());
		for (int col = 0; col < width; col++) for (int row = 0; row < width; row++) helper.getLevel().setBlockAndUpdate(quarry.cell(depth, col, row), Blocks.AIR.defaultBlockState());
		QuarrySafety.placeQuarryLights(quarry, worker, depth);
		int lights = 0; for (int col = 0; col < width; col++) for (int row = 0; row < width; row++) if (helper.getLevel().getBlockState(quarry.cell(depth, col, row)).is(RatsBlockRegistry.RAT_QUARRY_TORCH.get())) lights++;
		helper.assertTrue(lights == 4, "Closed downward shaft gets a light on each wall every fifth layer"); helper.succeed();
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 6000)
	public static void twoOreHuntersCrossBandVeinAndReturn(GameTestHelper helper) {
		var quarry = quarry(helper, false); quarry.configure(1); for (int i = 0; i < 15; i++) quarry.configure(5);
		for (int col = 0; col < 5; col++) helper.getLevel().setBlockAndUpdate(quarry.cell(0, col, 0), Blocks.IRON_ORE.defaultBlockState());
		helper.getLevel().setBlockAndUpdate(quarry.cell(0, 0, -1), Blocks.IRON_ORE.defaultBlockState());
		for (int col = 5; col < 11; col++) { helper.getLevel().setBlockAndUpdate(quarry.cell(0, col, 0), Blocks.IRON_ORE.defaultBlockState()); helper.getLevel().setBlockAndUpdate(quarry.cell(0, col, -1), Blocks.STONE.defaultBlockState()); }
		helper.getLevel().setBlockAndUpdate(quarry.cell(1, 4, 0), Blocks.COBBLESTONE.defaultBlockState());
		var crew = new java.util.ArrayList<TamedRat>();
		for (int i = 0; i < 2; i++) {
			var worker = rat(helper, true); worker.setItemSlot(EquipmentSlot.LEGS, new ItemStack(RatsItemRegistry.RAT_UPGRADE_QUARRY.get())); worker.setItemSlot(EquipmentSlot.FEET, new ItemStack(RatsItemRegistry.RAT_UPGRADE_ORE_HUNTER.get()));
			worker.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos())); worker.setCommand(RatCommand.HARVEST); worker.moveTo(quarry.getBlockPos().above().offset(i, 0, 0).getCenter()); worker.setNoAi(false); crew.add(worker);
		}
		helper.succeedWhen(() -> {
			int total = 0; for (int i = 0; i < 64; i++) total += quarry.getItem(i).getCount();
			helper.assertTrue(total == 13 && quarry.getProgress() == 16 && crew.stream().allMatch(worker -> worker.isAlive() && worker.cargo.isEmpty()), "Cross-band vein, floor ore and cave cargo all returned: total=" + total + "; progress=" + quarry.getProgress() + "; crew=" + crew.stream().map(worker -> worker.position() + "/" + worker.getWorkDebugState()).toList());
			helper.assertTrue(helper.getLevel().getBlockState(quarry.cell(0, 0, -1)).is(RatsBlockRegistry.RAT_QUARRY_PLATFORM.get()), "Mined ore floor is repaired at tunnel height"); crew.forEach(worker -> worker.setCommand(RatCommand.SIT));
		});
	}
	@GameTest(template = "quarry_test_empty")
	public static void sharedVeinHasSingleOwnerAcrossBands(GameTestHelper helper) {
		var quarry = quarry(helper, false); quarry.configure(1); var first = rat(helper, true); var second = rat(helper, true);
		for (var worker : java.util.List.of(first, second)) { worker.setCommand(RatCommand.HARVEST); worker.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos())); }
		BlockPos seed = quarry.cell(0, 0, 0);
		for (int col = 1; col < quarry.getWidth(); col++) helper.getLevel().setBlockAndUpdate(quarry.cell(0, col, 0), Blocks.IRON_ORE.defaultBlockState());
		first.oreVein.discover(helper.getLevel(), quarry.getBlockPos(), seed); quarry.claimVein(first);
		second.oreVein.discover(helper.getLevel(), quarry.getBlockPos(), seed); quarry.claimVein(second);
		helper.assertTrue(first.oreVein.targets(quarry.getBlockPos()).size() == 4 && second.oreVein.targets(quarry.getBlockPos()).isEmpty(), "Cross-band vein is not duplicated in two worker queues");
		BlockPos ore = quarry.cell(0, 4, 0);
		helper.assertTrue(!quarry.reserveOre(ore, second) && quarry.reserveOre(ore, first), "Only vein owner may reserve its ore");
		BlockPos next = quarry.cell(1, 4, 0); helper.getLevel().setBlockAndUpdate(next, Blocks.COBBLESTONE.defaultBlockState());
		helper.assertTrue(next.equals(quarry.reserveTarget(second, pos -> true)), "Non-owner continues other work instead of contesting vein"); helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void platformsAndCheeseLightSpacing(GameTestHelper helper) {
		var quarry = quarry(helper, false); quarry.configure(1); var worker = rat(helper, false);
		helper.assertTrue(QuarrySafety.ensureHorizontalFloor(quarry, worker, 0), "Void must receive tunnel-level footing");
		for (int column = 0; column < quarry.getWidth(); column++) helper.assertTrue(helper.getLevel().getBlockState(quarry.cell(0, column, -1)).is(RatsBlockRegistry.RAT_QUARRY_PLATFORM.get()), "Platform under each unsupported tunnel column");
		for (int column = 0; column < quarry.getWidth(); column++) helper.getLevel().setBlockAndUpdate(quarry.cell(1, column, -1), Blocks.STONE.defaultBlockState());
		helper.assertTrue(QuarrySafety.ensureHorizontalFloor(quarry, worker, 1), "Natural floor works");
		for (int column = 0; column < quarry.getWidth(); column++) helper.assertTrue(helper.getLevel().getBlockState(quarry.cell(1, column, -1)).is(Blocks.STONE), "Never overwrite solid floors");
		for (int depth = 0; depth < 10; depth++) {
			for (int column = 0; column < quarry.getWidth(); column++) helper.getLevel().setBlockAndUpdate(quarry.cell(depth, column, quarry.getWidth()), Blocks.STONE.defaultBlockState());
			helper.getLevel().setBlockAndUpdate(quarry.cell(depth, -1, 1), Blocks.STONE.defaultBlockState()); helper.getLevel().setBlockAndUpdate(quarry.cell(depth, quarry.getWidth(), 1), Blocks.STONE.defaultBlockState());
			QuarrySafety.placeQuarryLights(quarry, worker, depth);
			for (int column : new int[]{0, quarry.getWidth() - 1}) helper.assertTrue(helper.getLevel().getBlockState(quarry.cell(depth, column, 1)).is(RatsBlockRegistry.RAT_QUARRY_TORCH.get()) == ((depth + 1) % 5 == 0), "Lights only every five blocks on both walls");
		}
		BlockPos torch = quarry.cell(4, 0, 1); var state = helper.getLevel().getBlockState(torch);
		helper.assertTrue(state.getLightEmission(helper.getLevel(), torch) == 14 && Block.getDrops(state, helper.getLevel(), torch, null).isEmpty(), "Generated cheese torches illuminate without drops");
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void freeWorkerDoesNotWaitForLeasedFront(GameTestHelper helper) {
		var quarry = quarry(helper, false); quarry.configure(1); var first = rat(helper, true); var second = rat(helper, true);
		for (var worker : java.util.List.of(first, second)) { worker.setCommand(RatCommand.HARVEST); worker.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos())); }
		BlockPos front = quarry.cell(0, 0, 0), next = quarry.cell(1, 4, 0);
		helper.getLevel().setBlockAndUpdate(front, Blocks.STONE.defaultBlockState()); helper.getLevel().setBlockAndUpdate(next, Blocks.STONE.defaultBlockState());
		helper.assertTrue(front.equals(quarry.reserveTarget(first, pos -> true)), "First worker holds current front");
		helper.assertTrue(next.equals(quarry.reserveTarget(second, pos -> true)), "Second worker advances while first still owns front");
		helper.assertTrue(quarry.getProgress() == 0 && quarry.ownsTarget(front, first.getUUID()) && quarry.ownsTarget(next, second.getUUID()), "Progress never skips incomplete slices; leases remain exclusive");
		quarry.releaseTarget(first.getUUID()); quarry.releaseTarget(second.getUUID()); helper.getLevel().setBlockAndUpdate(next, Blocks.AIR.defaultBlockState());
		helper.assertTrue(front.equals(quarry.reserveTarget(second, pos -> true)), "Idle worker borrows available work from another band");
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 6000)
	public static void ratBridgesCaveAndKeepsMining(GameTestHelper helper) {
		var quarry = quarry(helper, false); quarry.configure(1); for (int i = 0; i < 15; i++) quarry.configure(5);
		for (int column = 0; column < quarry.getWidth(); column++) helper.getLevel().setBlockAndUpdate(quarry.cell(8, column, 0), Blocks.COBBLESTONE.defaultBlockState());
		var worker = rat(helper, true); worker.setItemSlot(EquipmentSlot.LEGS, new ItemStack(RatsItemRegistry.RAT_UPGRADE_QUARRY.get()));
		worker.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos())); worker.setCommand(RatCommand.HARVEST); worker.moveTo(quarry.getBlockPos().above().getCenter()); worker.setNoAi(false);
		helper.succeedWhen(() -> {
			helper.assertTrue(quarry.getProgress() == 16 && worker.cargo.isEmpty() && worker.isAlive(), "Worker crosses unsupported cave and returns cargo: " + worker.getWorkDebugState());
			int deposited = 0; for (int i = 0; i < 64; i++) deposited += quarry.getItem(i).getCount(); helper.assertTrue(deposited == 5, "All five cave blocks deposited"); worker.setCommand(RatCommand.SIT);
		});
	}
	@GameTest(template = "quarry_test_empty")
	public static void boundedOreSearch(GameTestHelper helper) {
		var quarry = quarry(helper, false); BlockPos seed = helper.absolutePos(new BlockPos(2, 2, 2));
		for (int x = 0; x < 10; x++) for (int y = 0; y < 8; y++) for (int z = 0; z < 10; z++) helper.getLevel().setBlockAndUpdate(seed.offset(x, y, z), Blocks.IRON_ORE.defaultBlockState());
		var work = new com.github.alexthe666.rats.server.misc.OreVeinWork(); work.discover(helper.getLevel(), quarry.getBlockPos(), seed);
		helper.assertTrue(work.targets(quarry.getBlockPos()).size() == 512, "Large connected deposits must be bounded at 512 blocks");
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void workerLimitsAreasRecallAndMigration(GameTestHelper helper) {
		var owner = helper.makeMockPlayer();
		for (boolean advanced : new boolean[]{false, true}) {
			var quarry = quarry(helper, advanced); quarry.configure(1);
			var workers = new java.util.ArrayList<TamedRat>();
			for (int i = 0; i < quarry.getWorkerLimit(); i++) {
				var worker = rat(helper, true); worker.setOwnerUUID(i == 0 ? owner.getUUID() : java.util.UUID.randomUUID());
				worker.setCommand(RatCommand.HARVEST); worker.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos())); workers.add(worker);
			}
			var extra = rat(helper, false); extra.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos()));
			helper.assertTrue(extra.getDepositPos().isEmpty(), "Overflow must not become assigned");
			for (int col = 0; col < quarry.getWidth(); col++) {
				final int column = col;
				helper.assertTrue(workers.stream().filter(worker -> quarry.ownsColumn(worker, column)).count() == 1, "Each column has exactly one owner");
				helper.getLevel().setBlockAndUpdate(quarry.cell(0, col, 0), Blocks.STONE.defaultBlockState());
			}
			var targets = new HashSet<BlockPos>();
			for (var worker : workers) { BlockPos target = quarry.reserveTarget(worker, pos -> true); helper.assertTrue(target != null && targets.add(target), "Workers never share a target"); worker.cargo.insert(new ItemStack(Items.COBBLESTONE, 3), false); }
			quarry.recallWorkers(owner);
			helper.assertTrue(workers.get(0).quarryReturning && workers.stream().skip(1).noneMatch(worker -> worker.quarryReturning), "Recall affects only viewer-owned workers");
			CompoundTag saved = quarry.saveWithoutMetadata();
			var restored = new RatQuarryBlockEntity(quarry.getBlockPos(), quarry.getBlockState()); restored.load(saved);
			helper.assertTrue(restored.workerIds().size() == quarry.getWorkerLimit(), "Worker assignments survive save/load");
			saved.putIntArray("QuarryProgress", new int[]{20, 30, 40, 50, 60}); saved.remove("QuarryWidth"); restored.load(saved);
			helper.assertTrue(restored.getProgress() == 0 && restored.getDirectionIndex() == 1, "Old narrow progress is reset, direction retained");
			workers.forEach(worker -> { worker.setDepositPos(null); worker.discard(); }); extra.discard();
		}
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void oreVeinBoundariesPersistenceAndFluidFace(GameTestHelper helper) {
		var quarry = quarry(helper, false); quarry.configure(1); var worker = rat(helper, true);
		worker.setCommand(RatCommand.HARVEST); worker.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos()));
		BlockPos seed = quarry.cell(0, quarry.getWidth() - 1, 0), outside = quarry.cell(0, quarry.getWidth(), 0);
		helper.getLevel().setBlockAndUpdate(outside, Blocks.IRON_ORE.defaultBlockState());
		helper.getLevel().setBlockAndUpdate(outside.above().east(), Blocks.DEEPSLATE_IRON_ORE.defaultBlockState());
		helper.getLevel().setBlockAndUpdate(outside.above(4), Blocks.DIAMOND_ORE.defaultBlockState());
		worker.oreVein.discover(helper.getLevel(), quarry.getBlockPos(), seed);
		helper.assertTrue(worker.oreVein.targets(quarry.getBlockPos()).size() == 2, "Connected diagonal ore outside quarry included; disconnected ore excluded");
		var loaded = new com.github.alexthe666.rats.server.misc.OreVeinWork(); loaded.load(worker.oreVein.save());
		helper.assertTrue(loaded.targets(quarry.getBlockPos()).equals(worker.oreVein.targets(quarry.getBlockPos())), "Vein survives reload/deposit");
		helper.getLevel().setBlockAndUpdate(outside.below(), Blocks.LAVA.defaultBlockState());
		helper.assertTrue(QuarrySafety.prepareOreBlock(worker, outside) && helper.getLevel().getBlockState(outside.below()).is(RatsBlockRegistry.RAT_QUARRY_LINING.get()), "Ore face seals adjacent lava before mining");
		helper.assertTrue(loaded.targets(quarry.getBlockPos().east()).isEmpty(), "Changing quarry clears old vein");
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 4000)
	public static void oreHunterMinesWallVeinAndResumes(GameTestHelper helper) {
		var quarry = quarry(helper, false); quarry.configure(1);
		for (int i = 0; i < 15; i++) quarry.configure(5);
		for (int depth = -1; depth < 16; depth++) for (int col = -1; col <= 7; col++) helper.getLevel().setBlockAndUpdate(quarry.cell(depth, col, -1), Blocks.STONE.defaultBlockState());
		for (int col = 0; col < quarry.getWidth(); col++) helper.getLevel().setBlockAndUpdate(quarry.cell(0, col, 0), Blocks.COBBLESTONE.defaultBlockState());
		BlockPos ore = quarry.cell(0, quarry.getWidth(), 0);
		helper.getLevel().setBlockAndUpdate(ore, Blocks.IRON_ORE.defaultBlockState()); helper.getLevel().setBlockAndUpdate(ore.east(), Blocks.IRON_ORE.defaultBlockState());
		var worker = rat(helper, true);
		worker.setItemSlot(EquipmentSlot.LEGS, new ItemStack(RatsItemRegistry.RAT_UPGRADE_QUARRY.get()));
		worker.setItemSlot(EquipmentSlot.FEET, new ItemStack(RatsItemRegistry.RAT_UPGRADE_ORE_HUNTER.get()));
		worker.setCommand(RatCommand.HARVEST); worker.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos()));
		worker.moveTo(quarry.getBlockPos().above().getCenter()); worker.setNoAi(false);
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getLevel().getBlockState(ore).isAir() && helper.getLevel().getBlockState(ore.east()).isAir(), "Whole exposed wall vein mined: " + worker.getWorkDebugState() + "; pending=" + worker.oreVein.targets(quarry.getBlockPos()) + "; position=" + worker.position());
			helper.assertTrue(quarry.getProgress() == 16 && worker.cargo.isEmpty(), "Normal quarry work resumes and cargo deposited");
			int total = 0; for (int i = 0; i < 64; i++) total += quarry.getItem(i).getCount();
			helper.assertTrue(total == 7, "Five quarry blocks plus two ore drops, without loss/duplication: total=" + total + "; inventory=" + java.util.stream.IntStream.range(0, 64).mapToObj(quarry::getItem).filter(stack -> !stack.isEmpty()).toList());
		});
	}
	@GameTest(template = "quarry_test_empty")
	public static void continuousQuarryInventoryPreservesSlots(GameTestHelper helper) {
		var player = helper.makeMockPlayer();
		for (boolean advanced : new boolean[]{false, true}) {
			RatQuarryBlockEntity quarry = quarry(helper, advanced);
			for (int i = 0; i < 64; i++) quarry.setItem(i, new ItemStack(Items.COBBLESTONE, i + 1));
			RatQuarryMenu menu = (RatQuarryMenu) quarry.createMenu(1, player.getInventory(), player);
			helper.assertTrue(menu.slots.size() == 100, "64 quarry slots and 36 player slots");
			for (int i = 0; i < 64; i++) {
				var slot = menu.slots.get(i);
				helper.assertTrue(slot.x == 8 + i % 8 * 18 && slot.y == 42 + i / 8 * 18, "Continuous eight by eight grid");
				helper.assertTrue(slot.getItem().getCount() == i + 1, "Existing item indices preserved");
			}
			helper.assertTrue(menu.slots.get(64).y == 198 && menu.slots.get(91).y == 256, "Player inventory below quarry storage");
			for (int i = 0; i < 64; i++) menu.quickMoveStack(player, i);
			int total = 0;
			for (int i = 0; i < 64; i++) total += quarry.getItem(i).getCount();
			for (int i = 0; i < 36; i++) total += player.getInventory().getItem(i).getCount();
			helper.assertTrue(total == 2080, "Shift-click conserves items even when player storage fills");
			menu.removed(player);
			player.getInventory().clearContent();
		}
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void dryHorizontalSliceDoesNotBuildCheese(GameTestHelper helper) {
		RatQuarryBlockEntity quarry = quarry(helper, false); quarry.configure(1);
		TamedRat rat = rat(helper, false);
		helper.assertTrue(QuarrySafety.prepareSlice(quarry, rat), "Dry slice must be safe");
		for (int row = -1; row <= quarry.getWidth(); row++) for (int col = -1; col <= quarry.getWidth(); col++) {
			if (row == -1 && col >= 0 && col < quarry.getWidth()) continue; // New intentional cave floor, not fluid lining.
			helper.assertTrue(helper.getLevel().getBlockState(quarry.cell(0, col, row)).isAir(), "Dry air must not become lining");
		}
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void exactCrossSections(GameTestHelper helper) {
		BlockPos origin = new BlockPos(7, 150, -11);
		for (int width : new int[]{5, 7}) for (Direction direction : RatQuarryBlockEntity.DIRECTIONS) {
			for (int depth : new int[]{0, 15, width == 5 ? 255 : 511}) {
				var positions = new HashSet<BlockPos>();
				for (int row = 0; row < width; row++) for (int column = 0; column < width; column++) positions.add(QuarryGeometry.cell(origin, direction, width, depth, column, row));
				helper.assertTrue(positions.size() == width * width, "Cross section must contain exactly width squared cells");
				for (BlockPos pos : positions) {
					if (direction == Direction.DOWN) helper.assertTrue(pos.getY() == origin.getY() - depth - 1, "Downward depth");
					else helper.assertTrue((pos.getX() - origin.getX()) * direction.getStepX() + (pos.getZ() - origin.getZ()) * direction.getStepZ() == depth + 1, "Horizontal depth");
				}
			}
		}
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void cargoTenStacksAndPersistence(GameTestHelper helper) {
		TamedRat rat = rat(helper, true);
		for (int i = 0; i < 10; i++) {
			ItemStack stack = new ItemStack(Items.COBBLESTONE, 64);
			stack.setHoverName(Component.literal("Stack " + i));
			helper.assertTrue(rat.cargo.insert(stack, false).isEmpty(), "Ten separate NBT stacks must fit");
		}
		helper.assertTrue(rat.cargo.isFull(), "Ten full slots");
		helper.assertTrue(!rat.cargo.canAccept(new ItemStack(Items.DIRT)), "Eleventh stack rejected");
		CompoundTag saved = rat.saveWithoutId(new CompoundTag());
		TamedRat restored = RatsEntityRegistry.TAMED_RAT.get().create(helper.getLevel());
		restored.load(saved);
		for (int i = 0; i < 10; i++) helper.assertTrue(ItemStack.matches(rat.cargo.get(i), restored.cargo.get(i)), "Cargo NBT survives save/load");
		ItemStackHandler storage = new ItemStackHandler(10);
		restored.cargo.deposit(storage);
		helper.assertTrue(restored.cargo.isEmpty(), "Deposit clears all ten slots");
		for (int i = 0; i < 10; i++) helper.assertTrue(storage.getStackInSlot(i).getCount() == 64, "No stack lost at deposit");
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void cargoLimitsAndFullDestination(GameTestHelper helper) {
		TamedRat rat = rat(helper, true);
		ItemStack pearls = new ItemStack(Items.ENDER_PEARL, 32);
		helper.assertTrue(rat.cargo.insert(pearls, true).isEmpty() && rat.cargo.isEmpty(), "Simulation does not mutate");
		rat.cargo.insert(pearls, false);
		helper.assertTrue(rat.cargo.get(0).getCount() == 16 && rat.cargo.get(1).getCount() == 16, "Sixteen-item limits");
		ItemStack first = new ItemStack(Items.DIAMOND_SWORD);
		first.getOrCreateTag().putInt("Damage", 7);
		rat.cargo.insert(first, false);
		helper.assertTrue(rat.cargo.get(2).getDamageValue() == 7, "Tool damage retained");
		ItemStackHandler full = new ItemStackHandler(1);
		full.setStackInSlot(0, new ItemStack(Items.DIRT, 64));
		helper.assertTrue(!rat.cargo.canDeposit(full), "Full destination does not accept cargo");
		rat.cargo.deposit(full);
		helper.assertTrue(rat.cargo.get(0).getCount() == 16 && rat.cargo.get(2).getDamageValue() == 7, "Full destination retains cargo");
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void reservationsAndDirectionalProgress(GameTestHelper helper) {
		RatQuarryBlockEntity quarry = quarry(helper, false);
		quarry.configure(1);
		TamedRat first = rat(helper, false), second = rat(helper, false);
		first.setCommand(RatCommand.HARVEST); second.setCommand(RatCommand.HARVEST);
		first.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos())); second.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos()));
		for (int i : new int[]{0, 4}) helper.getLevel().setBlockAndUpdate(quarry.cell(0, i, 0), Blocks.STONE.defaultBlockState());
		BlockPos one = quarry.reserveTarget(first, pos -> true), two = quarry.reserveTarget(second, pos -> true);
		helper.assertTrue(one != null && two != null && !one.equals(two), "Rats reserve different blocks");
		helper.getLevel().destroyBlock(one, false);
		helper.getLevel().destroyBlock(two, false);
		quarry.configure(3);
		helper.assertTrue(!quarry.ownsTarget(one, first.getUUID()), "Direction changes invalidate leases");
		quarry.reserveTarget(first, pos -> true);
		int savedProgress = quarry.getProgress();
		helper.assertTrue(savedProgress > 0 && savedProgress <= 8, "Empty slices advance in bounded batches until encountering the previous tunnel's lining");
		CompoundTag saved = quarry.saveWithoutMetadata();
		RatQuarryBlockEntity restored = new RatQuarryBlockEntity(quarry.getBlockPos(), quarry.getBlockState());
		restored.setLevel(helper.getLevel());
		restored.load(saved);
		helper.assertTrue(restored.getDirectionIndex() == 3 && restored.getProgress() == savedProgress, "Direction and progress survive reload");
		restored.configure(1);
		helper.assertTrue(restored.getProgress() == 0, "Each direction keeps its own progress");
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void fluidContainmentAndNoDrops(GameTestHelper helper) {
		RatQuarryBlockEntity quarry = quarry(helper, true);
		quarry.configure(1);
		TamedRat rat = rat(helper, true);
		BlockPos water = quarry.cell(0, 0, 0), lava = quarry.cell(0, 5, 0), front = quarry.cell(1, 5, 2);
		helper.getLevel().setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
		helper.getLevel().setBlockAndUpdate(lava, Blocks.LAVA.defaultBlockState());
		helper.getLevel().setBlockAndUpdate(front, Blocks.LAVA.defaultBlockState());
		helper.assertTrue(QuarrySafety.prepareSlice(quarry, rat), "Slice can be sealed");
		helper.assertTrue(helper.getLevel().getFluidState(water).isEmpty() && helper.getLevel().getFluidState(lava).isEmpty(), "Interior fluids drained");
		helper.assertTrue(helper.getLevel().getBlockState(front).is(RatsBlockRegistry.RAT_QUARRY_LINING.get()), "Next fluid face capped");
		helper.assertTrue(Block.getDrops(RatsBlockRegistry.RAT_QUARRY_LINING.get().defaultBlockState(), helper.getLevel(), front, null).isEmpty(), "Lining never drops items");
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 6000)
	public static void horizontalRatMinesAndDeposits(GameTestHelper helper) {
		mineAndDeposit(helper, false);
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 6000)
	public static void buriedHorizontalEntryWithSilverTray(GameTestHelper helper) {
		RatQuarryBlockEntity quarry = quarry(helper, true); quarry.configure(1);
		for (int i = 0; i < 31; i++) quarry.configure(5);
		for (int x = 7; x <= 17; x++) for (int z = 16; z <= 23; z++) for (int y = 1; y <= 7; y++) {
			BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
			if (!pos.equals(quarry.getBlockPos())) helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
		}
		for (int depth = 0; depth < 2; depth++) for (int row = 0; row < quarry.getWidth(); row++) for (int col = 0; col < quarry.getWidth(); col++) helper.getLevel().setBlockAndUpdate(quarry.cell(depth, col, row), Blocks.STONE.defaultBlockState());
		TamedRat rat = rat(helper, true);
		rat.setItemSlot(EquipmentSlot.LEGS, new ItemStack(RatsItemRegistry.RAT_UPGRADE_QUARRY.get()));
		rat.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos()));
		rat.setCommand(RatCommand.HARVEST);
		rat.moveTo(Vec3.atBottomCenterOf(quarry.getBlockPos().above(4).west())); rat.setNoAi(false);
		helper.succeedWhen(() -> {
			helper.assertTrue(quarry.getProgress() >= 2, "Buried horizontal entrance must advance from above: " + rat.position() + ", progress=" + quarry.getProgress() + ", work=" + rat.getWorkDebugState());
		});
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 6000)
	public static void advancedRatMinesAndDeposits(GameTestHelper helper) {
		mineAndDeposit(helper, true);
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 6000)
	public static void twoWorkersMineAndDeposit(GameTestHelper helper) { mineAndDeposit(helper, false, 2); }
	@GameTest(template = "quarry_test_empty", timeoutTicks = 6000)
	public static void fourWorkersMineAndDeposit(GameTestHelper helper) { mineAndDeposit(helper, true, 4); }
	@GameTest(template = "quarry_test_empty", timeoutTicks = 6000)
	public static void resumedEnclosedQuarryWithFourOreHunters(GameTestHelper helper) {
		var quarry = quarry(helper, true); quarry.configure(1); for (int i = 0; i < 31; i++) quarry.configure(5);
		var saved = quarry.saveWithoutMetadata(); saved.putIntArray("QuarryProgress", new int[]{0, 8, 0, 0, 0}); quarry.load(saved);
		int width = quarry.getWidth();
		for (int depth = -1; depth < 16; depth++) for (int row = -1; row <= width; row++) for (int col = -1; col <= width; col++) {
			if (quarry.cell(depth, col, row).equals(quarry.getBlockPos())) continue;
			boolean shell = row == -1 || row == width || col == -1 || col == width;
			Block state = shell || (depth >= 8 && depth < 14) ? Blocks.STONE : Blocks.AIR;
			helper.getLevel().setBlockAndUpdate(quarry.cell(depth, col, row), state.defaultBlockState());
		}
		for (int col = 0; col < width; col++) helper.getLevel().setBlockAndUpdate(quarry.cell(9, col, 0), Blocks.IRON_ORE.defaultBlockState());
		var crew = new java.util.ArrayList<TamedRat>();
		for (int i = 0; i < 4; i++) {
			var worker = rat(helper, true); worker.setItemSlot(EquipmentSlot.LEGS, new ItemStack(RatsItemRegistry.RAT_UPGRADE_QUARRY.get())); worker.setItemSlot(EquipmentSlot.FEET, new ItemStack(RatsItemRegistry.RAT_UPGRADE_ORE_HUNTER.get()));
			worker.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos())); worker.setCommand(RatCommand.HARVEST); worker.moveTo(quarry.getBlockPos().above().offset(i - 2, 0, 0).getCenter());
			quarry.assignWorker(worker); worker.oreVein.targets(quarry.getBlockPos()); worker.oreVein.rememberReturnPoint(worker.position()); worker.setNoAi(false); crew.add(worker);
		}
		int[] peak = {0}; boolean[] accumulated = new boolean[4];
		helper.succeedWhen(() -> {
			peak[0] = Math.max(peak[0], (int) crew.stream().filter(worker -> worker.crafting).count());
			for (int i = 0; i < 4; i++) if (crew.get(i).cargo.get(0).getCount() > 1) accumulated[i] = true;
			int deposited = java.util.stream.IntStream.range(0, 64).map(i -> quarry.getItem(i).getCount()).sum();
			helper.assertTrue(quarry.getProgress() == 16 && deposited >= width * width * 6 && crew.stream().allMatch(worker -> worker.cargo.isEmpty()), "Resumed enclosed crew finishes: progress=" + quarry.getProgress() + ", deposited=" + deposited + "; crew=" + crew.stream().map(worker -> worker.position() + "/" + worker.getWorkDebugState()).toList());
			helper.assertTrue(peak[0] == 4 && java.util.stream.IntStream.range(0, 4).allMatch(i -> accumulated[i]), "All four workers excavate concurrently and accumulate cargo"); crew.forEach(worker -> worker.setCommand(RatCommand.SIT));
		});
	}
	private static void mineAndDeposit(GameTestHelper helper, boolean advanced) {
		mineAndDeposit(helper, advanced, 1);
	}
	private static void mineAndDeposit(GameTestHelper helper, boolean advanced, int crewSize) {
		RatQuarryBlockEntity quarry = quarry(helper, advanced);
		quarry.configure(1);
		for (int i = 0; i < 31; i++) quarry.configure(5);
		int width = quarry.getWidth();
		// Natural ground, not free generated cheese floors, supports dry tunnels.
		for (int depth = -1; depth < 16; depth++) for (int col = -1; col <= width; col++) helper.getLevel().setBlockAndUpdate(quarry.cell(depth, col, -1), Blocks.STONE.defaultBlockState());
		for (int depth = 0; depth < 2; depth++) for (int row = 0; row < width; row++) for (int col = 0; col < width; col++) helper.getLevel().setBlockAndUpdate(quarry.cell(depth, col, row), Blocks.COBBLESTONE.defaultBlockState());
		var crew = new java.util.ArrayList<TamedRat>();
		for (int i = 0; i < crewSize; i++) {
			TamedRat worker = rat(helper, true);
			worker.setItemSlot(EquipmentSlot.LEGS, new ItemStack(RatsItemRegistry.RAT_UPGRADE_QUARRY.get()));
			worker.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos()));
			worker.setCommand(RatCommand.HARVEST); worker.moveTo(quarry.getBlockPos().above().offset(i - crewSize / 2, 0, 0).getCenter());
			worker.setNoAi(false); crew.add(worker);
		}
		TamedRat rat = crew.get(0);
		int[] peakMining = {0}; boolean[] carriedMultiple = new boolean[crewSize];
		helper.succeedWhen(() -> {
			peakMining[0] = Math.max(peakMining[0], (int) crew.stream().filter(worker -> worker.crafting).count());
			for (int i = 0; i < crewSize; i++) if (crew.get(i).cargo.get(0).getCount() > 1) carriedMultiple[i] = true;
			helper.assertTrue(quarry.getProgress() == 16, "Rat finishes configured tunnel: progress=" + quarry.getProgress() + ", status=" + quarry.getStatus() + ", quarry=" + quarry.getBlockPos() + ", rat=" + rat.position() + ", alive=" + rat.isAlive() + ", movable=" + rat.canMove() + ", ticks=" + rat.tickCount + ", cargo=" + rat.getMainHandItem() + ", returning=" + rat.quarryReturning + ", goals=" + rat.getWorkDebugState() + ", command=" + rat.getCommand());
			int deposited = 0;
			for (int i = 0; i < quarry.getContainerSize(); i++) deposited += quarry.getItem(i).getCount();
			helper.assertTrue(deposited == width * width * 2 && crew.stream().allMatch(worker -> worker.isAlive() && worker.cargo.isEmpty()), "All mined blocks deposited: deposited=" + deposited + "; expected=" + width * width * 2 + "; inventory=" + java.util.stream.IntStream.range(0, 64).mapToObj(quarry::getItem).filter(stack -> !stack.isEmpty()).toList() + "; crew=" + crew.stream().map(worker -> worker.getMainHandItem() + "/" + worker.getWorkDebugState()).toList());
			helper.assertTrue(peakMining[0] == crewSize && java.util.stream.IntStream.range(0, crewSize).allMatch(i -> carriedMultiple[i]), "Every worker must mine concurrently and accumulate multiple blocks: peak=" + peakMining[0]);
			crew.forEach(worker -> worker.setCommand(RatCommand.SIT));
		});
	}

	@GameTest(template = "quarry_test_empty", timeoutTicks = 6000)
	public static void downwardRatReturnsWithCargo(GameTestHelper helper) {
		RatQuarryBlockEntity quarry = quarry(helper, false);
		for (int depth = 0; depth < 2; depth++) for (int row = 0; row < quarry.getWidth(); row++) for (int col = 0; col < quarry.getWidth(); col++) helper.getLevel().setBlockAndUpdate(quarry.cell(depth, col, row), Blocks.COBBLESTONE.defaultBlockState());
		TamedRat rat = rat(helper, true);
		rat.setItemSlot(EquipmentSlot.LEGS, new ItemStack(RatsItemRegistry.RAT_UPGRADE_QUARRY.get()));
		rat.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos()));
		rat.setCommand(RatCommand.HARVEST);
		rat.moveTo(quarry.getBlockPos().above().getCenter());
		rat.setNoAi(false);
		helper.succeedWhen(() -> {
			int deposited = 0;
			for (int i = 0; i < quarry.getContainerSize(); i++) deposited += quarry.getItem(i).getCount();
			helper.assertTrue(deposited >= 50 && rat.cargo.isEmpty() && rat.isAlive(), "Downward shaft returns mined cargo: deposited=" + deposited + ", progress=" + quarry.getProgress() + ", status=" + quarry.getStatus() + ", rat=" + rat.position() + ", work=" + rat.getWorkDebugState() + "; travel=" + com.github.alexthe666.rats.server.misc.QuarryTravel.debugState(rat, quarry));
			rat.setCommand(RatCommand.SIT);
		});
	}

	@GameTest(template = "quarry_test_empty", timeoutTicks = 800)
	public static void unreachableCropRecovers(GameTestHelper helper) {
		for (int x = 3; x <= 5; x++) for (int y = 3; y <= 6; y++) for (int z = 3; z <= 5; z++) {
			if (x == 4 && z == 4 && (y == 4 || y == 5)) continue;
			helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
		}
		helper.setBlock(new BlockPos(8, 3, 4), Blocks.FARMLAND);
		helper.setBlock(new BlockPos(8, 4, 4), Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
		TamedRat rat = rat(helper, false);
		rat.setCommand(RatCommand.HARVEST);
		rat.setNoAi(false);
		helper.succeedWhen(() -> {
			helper.assertTrue(rat.workRetryAt > 0 && !rat.isCurrentlyWorking, "Unreachable crop releases work goal and schedules retry");
		});
	}
}
