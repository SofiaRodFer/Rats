package com.github.alexthe666.rats.server.test;

import com.github.alexthe666.rats.registry.*;
import com.github.alexthe666.rats.server.block.entity.RatFarmingBlockEntity;
import com.github.alexthe666.rats.server.entity.rat.*;
import com.github.alexthe666.rats.server.inventory.RatCageMenu;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.*;

/** Review regressions: exercise vanilla fluid/crop behavior, not only layout arithmetic. */
@GameTestHolder("rats")
@PrefixGameTestTemplate(false)
public class FarmReservoirReviewGameTests {
	@GameTest(template = "quarry_test_empty")
	public static void reservoirHydratesRealFarmlandOnlyInsideVanillaRange(GameTestHelper h) {
		var level = h.getLevel();
		var source = h.absolutePos(new BlockPos(8, 4, 8));
		var inside = source.offset(4, 0, 4);
		var outside = source.offset(5, 0, 0);
		level.setBlockAndUpdate(source, RatsBlockRegistry.RAT_FARM_WATER.get().defaultBlockState());
		for (var p : new BlockPos[]{inside, outside}) {
			level.setBlockAndUpdate(p.above(3), Blocks.STONE.defaultBlockState()); // No rain hydration.
			level.setBlockAndUpdate(p, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 5));
			level.getBlockState(p).randomTick(level, p, level.random);
		}
		h.assertTrue(level.getBlockState(inside).is(Blocks.FARMLAND) && level.getBlockState(inside).getValue(FarmBlock.MOISTURE) == 7,
				"Reservoir's actual source fluid hydrates diagonal distance 4, not merely metadata");
		h.assertTrue(level.getBlockState(outside).is(Blocks.FARMLAND) && level.getBlockState(outside).getValue(FarmBlock.MOISTURE) == 4,
				"Distance 5 remains outside hydration range without rain");
		level.destroyBlock(source, false);
		// destroyBlock preserves source fluid by vanilla design; remove that too.
		level.setBlockAndUpdate(source, Blocks.AIR.defaultBlockState());
		level.getBlockState(inside).randomTick(level, inside, level.random);
		h.assertTrue(level.getBlockState(inside).getValue(FarmBlock.MOISTURE) == 6, "Removed reservoir stops hydration");
		h.succeed();
	}

	@GameTest(template = "quarry_test_empty", timeoutTicks = 100)
	public static void sourceFluidTicksCannotEscapeReservoirAtOpenEdge(GameTestHelper h) {
		var source = h.absolutePos(new BlockPos(8, 5, 8));
		h.getLevel().setBlockAndUpdate(source, RatsBlockRegistry.RAT_FARM_WATER.get().defaultBlockState());
		for (var direction : Direction.values()) h.getLevel().setBlockAndUpdate(source.relative(direction), Blocks.AIR.defaultBlockState());
		for (int i = 0; i < 5; i++) Fluids.WATER.tick(h.getLevel(), source, h.getLevel().getFluidState(source));
		h.runAfterDelay(40, () -> {
			h.assertTrue(h.getLevel().getBlockState(source).is(RatsBlockRegistry.RAT_FARM_WATER.get()) && h.getLevel().getFluidState(source).isSource(), "Ticking retains real reservoir source");
			for (int x = -2; x <= 2; x++) for (int y = -2; y <= 1; y++) for (int z = -2; z <= 2; z++) {
				var p = source.offset(x, y, z);
				if (!p.equals(source)) h.assertTrue(h.getLevel().getFluidState(p).isEmpty(), "No fluid escaped into open side/bottom at " + p);
			}
			h.succeed();
		});
	}

	@GameTest(template = "quarry_test_empty", timeoutTicks = 1800)
	public static void actualWorkerConvertsExistingCropIntoWaterAndLight(GameTestHelper h) {
		var farm = farm(h, 3, 3);
		int cell = farm.layout().water.nextSetBit(0);
		var ground = farm.ground(cell);
		h.getLevel().setBlockAndUpdate(ground, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
		h.getLevel().setBlockAndUpdate(ground.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
		var rat = worker(h, farm);
		h.assertTrue(farm.taskAt(cell, rat) != null && farm.taskAt(cell, rat).action() == RatFarmingBlockEntity.Action.CLEAR,
				"Existing crop at new water location is cleared rather than eternally blocking WATER");
		rat.setCommand(RatCommand.HARVEST);
		h.succeedWhen(() -> {
			h.assertTrue(h.getLevel().getBlockState(ground).is(RatsBlockRegistry.RAT_FARM_LIGHT.get()) && h.getLevel().isEmptyBlock(ground.above()), "Real worker replaces crop with reservoir and lantern");
			int wheat = java.util.stream.IntStream.range(0, 54).filter(i -> farm.getItem(i).is(Items.WHEAT)).map(i -> farm.getItem(i).getCount()).sum()
					+ java.util.stream.IntStream.range(0, 10).filter(i -> rat.cargo.get(i).is(Items.WHEAT)).map(i -> rat.cargo.get(i).getCount()).sum();
			h.assertTrue(wheat == 1, "Mature wheat drop survives conversion exactly once");
			rat.setCommand(RatCommand.SIT);
		});
	}

	@GameTest(template = "quarry_test_empty", timeoutTicks = 1800)
	public static void twoHeightVegetationDoesNotBlockNewWaterLocation(GameTestHelper h) {
		var farm = farm(h, 3, 3);
		int cell = farm.layout().water.nextSetBit(0);
		var ground = farm.ground(cell);
		h.getLevel().setBlockAndUpdate(ground.above(), Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
		h.getLevel().setBlockAndUpdate(ground.above(2), Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
		var rat = worker(h, farm); rat.setCommand(RatCommand.HARVEST);
		h.succeedWhen(() -> {
			h.assertTrue(h.getLevel().getBlockState(ground).is(RatsBlockRegistry.RAT_FARM_LIGHT.get()) && h.getLevel().isEmptyBlock(ground.above()) && h.getLevel().isEmptyBlock(ground.above(2)), "Both plant halves removed; ground humidifier installed without upper lantern");
			rat.setCommand(RatCommand.SIT);
		});
	}

	@GameTest(template = "quarry_test_empty")
	public static void distinctTemplateHalvesQuadrantsAndNbtUseCorrectCells(GameTestHelper h) {
		var farm = farm(h, 5, 7);
		Item[] seeds = {Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.BEETROOT_SEEDS};
		for (int i = 0; i < 4; i++) h.assertTrue(farm.template(i, new ItemStack(seeds[i], 23)), "Valid distinct marker " + i);
		for (int[] size : new int[][]{{5, 5}, {6, 6}, {2, 2}, {7, 7}}) {
			farm.configure(size[0], size[1]);
			h.assertTrue(farm.mode(1), "Whole-field mode on all dimensions");
			checkPattern(h, farm, seeds, 1, 0);
			for (int axis = 0; axis < 2; axis++) {
				if ((axis == 0 ? size[0] : size[1]) >= 2) { h.assertTrue(farm.mode(2), "Valid split axis"); checkPattern(h, farm, seeds, 2, axis); }
				else h.assertTrue(!farm.mode(2), "Invalid one-cell split rejected");
				farm.splitAxis();
			}
			if (size[0] >= 2 && size[1] >= 2) {
				h.assertTrue(farm.mode(3), "Valid four quadrants"); checkPattern(h, farm, seeds, 3, 0);
				var restored = new RatFarmingBlockEntity(farm.getBlockPos(), farm.getBlockState()); restored.load(farm.saveWithFullMetadata());
				checkPattern(h, restored, seeds, 3, 0);
			} else h.assertTrue(!farm.mode(3), "One-dimensional plot cannot have four quadrants");
		}
		h.succeed();
	}
	private static void checkPattern(GameTestHelper h, RatFarmingBlockEntity farm, Item[] seeds, int mode, int axis) {
		for (int cell = 0; cell < 256; cell++) if (farm.layout().cropCell(cell)) {
			int x = cell % 16, z = cell / 16;
			int expected = 0;
			if (mode == 2) expected = (axis == 0 ? x < farm.layout().width / 2 : z < farm.layout().height / 2) ? 0 : 1;
			if (mode == 3) expected = (x < farm.layout().width / 2 ? 0 : 1) + (z < farm.layout().height / 2 ? 0 : 2);
			h.assertTrue(farm.seedAt(cell).is(seeds[expected]) && farm.seedAt(cell).getCount() == 1, "Marker mapping at cell " + cell + " for mode " + mode + " axis " + axis);
		}
	}

	@GameTest(template = "quarry_test_empty")
	public static void realAlexsMobsCrowPredicateRecognizesCheeseLantern(GameTestHelper h) throws ReflectiveOperationException {
		if (!net.minecraftforge.fml.ModList.get().isLoaded("alexsmobs")) { h.succeed(); return; }
		var type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(new net.minecraft.resources.ResourceLocation("alexsmobs", "crow"));
		h.assertTrue(type != null, "Actual Alex's Mobs crow entity type exists");
		var crow = type.create(h.getLevel());
		h.assertTrue(crow != null, "Actual crow instantiated, not a mock tag-only predicate");
		var goalType = Class.forName("com.github.alexthe666.alexsmobs.entity.EntityCrow$AIAvoidPumpkins");
		var constructor = goalType.getDeclaredConstructor(crow.getClass()); constructor.setAccessible(true);
		var goal = constructor.newInstance(crow);
		var predicate = goalType.getDeclaredMethod("isPumpkin", net.minecraft.world.level.Level.class, BlockPos.MutableBlockPos.class); predicate.setAccessible(true);
		var position = h.absolutePos(new BlockPos(8, 4, 8));
		h.getLevel().setBlockAndUpdate(position, RatsBlockRegistry.RAT_FARM_LIGHT.get().defaultBlockState());
		h.assertTrue(Boolean.TRUE.equals(predicate.invoke(goal, h.getLevel(), position.mutable())), "Real loaded crow avoidance predicate recognizes cheese lantern");
		h.getLevel().setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
		h.assertTrue(Boolean.FALSE.equals(predicate.invoke(goal, h.getLevel(), position.mutable())), "Control stone is not a scarecrow pumpkin");
		h.succeed();
	}

	@GameTest(template = "quarry_test_empty")
	public static void cageKeeperRetainsCapacityWhenUnrelatedUpgradeChanges(GameTestHelper h) {
		var local = new BlockPos(5, 4, 5); h.setBlock(local, RatsBlockRegistry.RAT_CAGE.get());
		var player = h.makeMockPlayer(); player.setPos(h.absolutePos(local).getCenter());
		var rat = h.spawn(RatsEntityRegistry.TAMED_RAT.get(), local); rat.setPos(player.getX(), h.absolutePos(local).getY() + .2, player.getZ());
		rat.setTame(true); rat.setOwnerUUID(player.getUUID()); rat.setNoAi(true);
		rat.setItemSlot(EquipmentSlot.CHEST, new ItemStack(RatsItemRegistry.RAT_UPGRADE_STORAGE_KEEPER.get()));
		rat.setItemSlot(EquipmentSlot.LEGS, new ItemStack(RatsItemRegistry.RAT_UPGRADE_SPEED.get()));
		rat.cargo.set(0, new ItemStack(Items.COBBLESTONE, 64));
		var menu = new RatCageMenu(87, player.getInventory(), h.absolutePos(local)); menu.action(player, 0, rat.getId(), -1, "");
		h.assertTrue(menu.slots.get(11).mayPickup(player), "Unrelated speed upgrade removable while Keeper retains stack capacity");
		h.assertTrue(!menu.slots.get(10).mayPickup(player), "Last stack capacity provider cannot be removed with 64 cargo");
		h.assertTrue(rat.cargo.get(0).getCount() == 64, "Permission probes never shrink/discard cargo");
		rat.cargo.set(0, new ItemStack(Items.COBBLESTONE));
		h.assertTrue(menu.slots.get(10).mayPickup(player), "Last provider becomes removable once cargo fits base capacity");
		h.succeed();
	}

	private static RatFarmingBlockEntity farm(GameTestHelper h, int width, int height) {
		var local = new BlockPos(12, 4, 20); h.setBlock(local, RatsBlockRegistry.RAT_FARMING.get());
		var f = (RatFarmingBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(local)); f.configure(width, height);
		for (int x = -width / 2 - 1; x <= width / 2 + 1; x++) for (int z = 0; z <= 2; z++) h.getLevel().setBlockAndUpdate(f.getBlockPos().offset(x, -1, z), Blocks.DIRT.defaultBlockState());
		for (int i = 0; i < 256; i++) if (f.layout().contains(i)) { h.getLevel().setBlockAndUpdate(f.ground(i), Blocks.DIRT.defaultBlockState()); h.getLevel().setBlockAndUpdate(f.ground(i).below(), Blocks.STONE.defaultBlockState()); }
		return f;
	}
	private static TamedRat worker(GameTestHelper h, RatFarmingBlockEntity f) {
		var rat = h.spawn(RatsEntityRegistry.TAMED_RAT.get(), new BlockPos(12, 5, 20)); rat.setTame(true); rat.setCommand(RatCommand.SIT);
		rat.setItemSlot(EquipmentSlot.CHEST, new ItemStack(RatsItemRegistry.RAT_UPGRADE_FARMER.get()));
		rat.setItemSlot(EquipmentSlot.LEGS, new ItemStack(RatsItemRegistry.RAT_UPGRADE_SILVER_TRAY.get()));
		rat.setItemSlot(EquipmentSlot.FEET, new ItemStack(RatsItemRegistry.RAT_UPGRADE_REPLANTER.get()));
		rat.setDepositPos(GlobalPos.of(h.getLevel().dimension(), f.getBlockPos())); return rat;
	}
}
