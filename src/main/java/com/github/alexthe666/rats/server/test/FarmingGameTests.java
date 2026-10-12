package com.github.alexthe666.rats.server.test;

import com.github.alexthe666.rats.registry.*;
import com.github.alexthe666.rats.server.block.entity.RatFarmingBlockEntity;
import com.github.alexthe666.rats.server.entity.rat.*;
import com.github.alexthe666.rats.server.entity.ai.goal.*;
import com.github.alexthe666.rats.server.entity.ai.goal.harvest.*;
import com.github.alexthe666.rats.server.inventory.RatFarmingMenu;
import com.github.alexthe666.rats.server.misc.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("rats") @PrefixGameTestTemplate(false)
public class FarmingGameTests {
	@GameTest(template = "quarry_test_empty")
	public static void legacyPlanterKeepsStemSeedsAndFarmPreservesMachines(GameTestHelper helper) {
		var farm = farm(helper, 6, 6); var rat = helper.spawn(RatsEntityRegistry.TAMED_RAT.get(), new BlockPos(12,5,20)); rat.setTame(true); rat.setItemSlot(EquipmentSlot.CHEST, new ItemStack(RatsItemRegistry.RAT_UPGRADE_FARMER.get())); rat.setCommand(RatCommand.HARVEST); rat.cargo.set(0, new ItemStack(Items.PUMPKIN_SEEDS));
		int cell = java.util.stream.IntStream.range(0, 256).filter(farm.layout()::cropCell).findFirst().orElseThrow(); var ground = farm.ground(cell); helper.getLevel().setBlockAndUpdate(ground, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
		var goal = new RatFarmGoal(rat); helper.assertTrue(goal.canUse(), "Ordinary planter still accepts vanilla pumpkin seeds outside the station"); goal.stop(); rat.setCommand(RatCommand.SIT);
		helper.getLevel().setBlockAndUpdate(ground, Blocks.CHEST.defaultBlockState()); var managed = worker(helper, farm); helper.assertTrue(farm.taskAt(cell, managed) == null, "Terraforming must not bulldoze storage/machines");
		for (var direction : Direction.Plane.HORIZONTAL) { var positions = new HashSet<BlockPos>(); for (int index = 0; index < 256; index++) if (farm.layout().contains(index)) { var pos = farm.layout().ground(farm.getBlockPos(), direction, index); positions.add(pos); int along = (pos.getX() - farm.getBlockPos().getX()) * direction.getStepX() + (pos.getZ() - farm.getBlockPos().getZ()) * direction.getStepZ(); helper.assertTrue(pos.getY() == farm.getBlockPos().getY() - 1 && along >= 1 && along <= 6, "All facing orientations use the fixed ground plane and plot length"); } helper.assertTrue(positions.size() == 36, "Rotation cannot overlap field cells"); }
		helper.assertTrue(!farm.contains(farm.getBlockPos()) && !farm.contains(farm.getBlockPos().relative(farm.facing(), 7)) && farm.contains(farm.ground(cell)), "Automatic collection/work boundary is the exact chosen rectangle"); helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void farmersDelightFarmlandSeedsAndTomatoStages(GameTestHelper helper) {
		if (!net.minecraftforge.fml.ModList.get().isLoaded("farmersdelight")) { helper.succeed(); return; }
		var farm = farm(helper, 6, 6); int cell = java.util.stream.IntStream.range(0, 256).filter(farm.layout()::cropCell).findFirst().orElseThrow(); var pos = farm.ground(cell).above();
		helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
		for (String id : List.of("tomato_seeds", "cabbage_seeds", "onion")) {
			var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("farmersdelight", id)); var seed = new ItemStack(item);
			helper.assertTrue(FarmCrops.seed(seed) && farm.paint(cell, seed), "Farmer's Delight farmland seed supported: " + id);
		}
		var budding = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation("farmersdelight", "budding_tomatoes"));
		helper.getLevel().setBlockAndUpdate(pos, budding.defaultBlockState()); helper.assertTrue(!FarmCrops.mature(helper.getLevel(), pos), "Budding tomatoes must be allowed to grow, not harvested prematurely");
		var tomatoes = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation("farmersdelight", "tomatoes")); var crop = (CropBlock)tomatoes;
		helper.getLevel().setBlockAndUpdate(pos, crop.getStateForAge(crop.getMaxAge())); helper.assertTrue(FarmCrops.mature(helper.getLevel(), pos), "Final mature tomato stage harvestable"); helper.succeed();
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 3000)
	public static void ordinaryPlanterAccumulatesThenDeposits(GameTestHelper helper) {
		var chest = helper.absolutePos(new BlockPos(12, 4, 20)); helper.getLevel().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
		for (int x = 9; x <= 15; x++) for (int z = 17; z <= 21; z++) if (z >= 19) helper.getLevel().setBlockAndUpdate(new BlockPos(helper.absolutePos(new BlockPos(x, 3, z))), Blocks.DIRT.defaultBlockState());
		for (int x = 9; x <= 15; x++) { var ground = helper.absolutePos(new BlockPos(x, 3, 18)); helper.getLevel().setBlockAndUpdate(ground, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7)); helper.getLevel().setBlockAndUpdate(ground.north(), Blocks.GLOWSTONE.defaultBlockState()); }
		// Propagate block light under the fixture roof before creating crops;
		// neighbor updates legitimately pop crops planted in complete darkness.
		helper.runAfterDelay(20, () -> { for (int x = 9; x <= 15; x++) helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 4, 18)), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7)); });
		var rat = helper.spawn(RatsEntityRegistry.TAMED_RAT.get(), new BlockPos(12, 5, 20)); rat.setTame(true); rat.setItemSlot(EquipmentSlot.CHEST, new ItemStack(RatsItemRegistry.RAT_UPGRADE_FARMER.get())); rat.setItemSlot(EquipmentSlot.LEGS, new ItemStack(RatsItemRegistry.RAT_UPGRADE_SILVER_TRAY.get())); rat.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), chest)); rat.setCommand(RatCommand.HARVEST);
		boolean[] accumulated = {false};
		helper.succeedWhen(() -> {
			for (int slot = 0; slot < rat.cargo.slots(); slot++) if (rat.cargo.get(slot).is(Items.WHEAT) && rat.cargo.get(slot).getCount() > 1) accumulated[0] = true;
			var storage = (net.minecraft.world.Container)helper.getLevel().getBlockEntity(chest); int wheat = java.util.stream.IntStream.range(0, storage.getContainerSize()).filter(i -> storage.getItem(i).is(Items.WHEAT)).map(i -> storage.getItem(i).getCount()).sum();
			helper.assertTrue(wheat == 7 && rat.cargo.isEmpty() && accumulated[0], "Ordinary Farmer accumulates harvests and deposits when no work remains: wheat=" + wheat + "; pos=" + rat.position() + "; crops=" + java.util.stream.IntStream.rangeClosed(9,15).mapToObj(x -> helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x,4,18)))).toList() + "; " + rat.getWorkDebugState()); rat.setCommand(RatCommand.SIT);
		});
	}
	private static RatFarmingBlockEntity farm(GameTestHelper helper, int width, int height) {
		var pos = new BlockPos(12, 4, 20); helper.setBlock(pos, RatsBlockRegistry.RAT_FARMING.get());
		var farm = (RatFarmingBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(pos)); farm.configure(width, height);
		// Like a real block placed on the ground, its supply/entry side needs
		// ground too. It is not a floating station over the fixture's air floor.
		for (int x = -width / 2 - 1; x <= width / 2 + 1; x++) for (int z = 0; z <= 2; z++) helper.getLevel().setBlockAndUpdate(farm.getBlockPos().offset(x, -1, z), Blocks.DIRT.defaultBlockState());
		for (int cell = 0; cell < 256; cell++) if (farm.layout().contains(cell)) { helper.getLevel().setBlockAndUpdate(farm.ground(cell), Blocks.DIRT.defaultBlockState()); helper.getLevel().setBlockAndUpdate(farm.ground(cell).below(), Blocks.STONE.defaultBlockState()); }
		return farm;
	}
	private static TamedRat worker(GameTestHelper helper, RatFarmingBlockEntity farm) {
		var rat = helper.spawn(RatsEntityRegistry.TAMED_RAT.get(), new BlockPos(12, 5, 20)); rat.setTame(true); rat.setCommand(RatCommand.SIT);
		rat.setItemSlot(EquipmentSlot.CHEST, new ItemStack(RatsItemRegistry.RAT_UPGRADE_FARMER.get())); rat.setItemSlot(EquipmentSlot.LEGS, new ItemStack(RatsItemRegistry.RAT_UPGRADE_SILVER_TRAY.get())); rat.setItemSlot(EquipmentSlot.FEET, new ItemStack(RatsItemRegistry.RAT_UPGRADE_REPLANTER.get()));
		rat.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), farm.getBlockPos())); return rat;
	}
	@GameTest(template = "quarry_test_empty")
	public static void layoutsCoverAllSizesWithWaterAndGroundLight(GameTestHelper helper) {
		for (int w = 1; w <= 16; w++) for (int h = 1; h <= 16; h++) {
			var layout = new FarmLayout(w, h);
			helper.assertTrue(layout.water.cardinality() == (w + 8) / 9 * ((h + 8) / 9), "Minimum 9x9 water coverage");
			for (int cell = 0; cell < 256; cell++) if (layout.cropCell(cell)) {
				boolean water = false, light = false;
				for (int index = 0; index < 256; index++) { if (layout.water.get(index) && Math.abs(index % 16 - cell % 16) <= 4 && Math.abs(index / 16 - cell / 16) <= 4) water = true; if (layout.lights.get(index) && Math.abs(index % 16 - cell % 16) + Math.abs(index / 16 - cell / 16) <= 5) light = true; }
				helper.assertTrue(water && light, "Hydration/light covers every crop cell " + w + "x" + h);
			}
		} helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void ghostPlanCannotConsumeDuplicateOrExtractSeeds(GameTestHelper helper) {
		var farm = farm(helper, 6, 6); var player = helper.makeMockPlayer(); player.setPos(farm.getBlockPos().getCenter()); var menu = new RatFarmingMenu(1, player.getInventory(), farm);
		int cell = java.util.stream.IntStream.range(0, 256).filter(farm.layout()::cropCell).findFirst().orElseThrow(); var seeds = new ItemStack(Items.WHEAT_SEEDS, 37); menu.setCarried(seeds);
		menu.clicked(90 + cell, 0, ClickType.PICKUP, player);
		helper.assertTrue(menu.getCarried().getCount() == 37 && farm.seedAt(cell).getCount() == 1 && farm.isEmpty(), "Ghost is a marker, not stored/consumed seeds");
		menu.clicked(90 + cell, 0, ClickType.QUICK_MOVE, player); helper.assertTrue(menu.quickMoveStack(player, 90 + cell).isEmpty() && menu.getCarried().getCount() == 37, "Ghost cannot be extracted/shift moved");
		int water = farm.layout().water.nextSetBit(0); helper.assertTrue(!farm.paint(water, seeds), "Water cells cannot hold seeds");
		menu.setCarried(new ItemStack(Items.DIAMOND)); menu.clicked(90 + cell, 0, ClickType.PICKUP, player); helper.assertTrue(farm.seedAt(cell).is(Items.WHEAT_SEEDS), "Invalid seed refused");
		menu.clicked(90 + cell, 1, ClickType.PICKUP, player); helper.assertTrue(farm.seedAt(cell).isEmpty() && menu.getCarried().is(Items.DIAMOND), "Clear marker does not destroy cursor");
		player.setPos(farm.getBlockPos().above(20).getCenter()); menu.setCarried(seeds); menu.clicked(90 + cell, 0, ClickType.PICKUP, player); helper.assertTrue(farm.seedAt(cell).isEmpty(), "Out-of-range edits rejected"); helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void farmNbtAndExclusiveHalves(GameTestHelper helper) {
		var farm = farm(helper, 6, 6); var a = worker(helper, farm); var b = worker(helper, farm); var extra = worker(helper, farm);
		helper.assertTrue(extra.getDepositPos().isEmpty(), "Only two rats can be assigned"); a.setCommand(RatCommand.HARVEST); b.setCommand(RatCommand.HARVEST);
		for (int cell = 0; cell < 256; cell++) if (farm.layout().contains(cell)) helper.assertTrue(farm.ownsHalf(a, cell) != farm.ownsHalf(b, cell), "Each cell belongs to exactly one half");
		var one = farm.claim(a, Set.of()); var two = farm.claim(b, Set.of()); helper.assertTrue(one != null && two != null && one.cell() != two.cell(), "Exclusive task leases");
		int seed = java.util.stream.IntStream.range(0, 256).filter(farm.layout()::cropCell).findFirst().orElseThrow(); farm.paint(seed, new ItemStack(Items.CARROT)); farm.setItem(0, new ItemStack(Items.DIAMOND, 17));
		var restored = new RatFarmingBlockEntity(farm.getBlockPos(), farm.getBlockState()); restored.load(farm.saveWithoutMetadata());
		helper.assertTrue(restored.layout().width == 6 && restored.seedAt(seed).is(Items.CARROT) && restored.getItem(0).getCount() == 17, "Dimensions, ghost plan and storage survive reload"); a.setCommand(RatCommand.SIT); b.setCommand(RatCommand.SIT); helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void planterReturnIsLatchedUntilActualDeposit(GameTestHelper helper) {
		var farm = farm(helper, 6, 6); var rat = worker(helper, farm); rat.setCommand(RatCommand.HARVEST); rat.cargo.insert(new ItemStack(Items.WHEAT, 12), false); rat.farmReturning = true;
		var goal = new RatFarmGoal(rat); var pickup = new RatPickupGoal(rat, RatPickupGoal.PickupType.INVENTORY); var items = new RatTargetItemsGoal(rat);
		helper.assertTrue(!goal.canUse() && !pickup.canUse() && !items.canUse(), "No planting/collection after deposit decision");
		var saved = new net.minecraft.nbt.CompoundTag(); rat.addAdditionalSaveData(saved); var restored = worker(helper, farm); restored.readAdditionalSaveData(saved); helper.assertTrue(restored.farmReturning, "Return latch survives save");
		rat.moveTo(farm.getBlockPos().above().getCenter()); var deposit = new RatDepositGoal(rat, RatDepositGoal.DepositType.INVENTORY); helper.assertTrue(deposit.canUse(), "Deposit despite partial cargo"); deposit.start(); deposit.tick();
		helper.assertTrue(rat.cargo.isEmpty() && !rat.farmReturning && farm.getItem(0).getCount() == 12, "Reset only after actual deposit"); rat.setCommand(RatCommand.SIT); restored.setCommand(RatCommand.SIT); helper.succeed();
	}
	@GameTest(template = "quarry_test_empty", timeoutTicks = 6000)
	public static void twoFarmersLevelPlantHarvestDepositAndRepair(GameTestHelper helper) {
		var farm = farm(helper, 6, 6); farm.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 64)); farm.setItem(1, new ItemStack(Items.WHEAT_SEEDS, 64)); farm.setItem(2, new ItemStack(Items.DIRT, 32));
		var cells = java.util.stream.IntStream.range(0, 256).filter(farm.layout()::cropCell).boxed().toList(); for (int cell : cells) farm.paint(cell, new ItemStack(Items.WHEAT_SEEDS));
		helper.getLevel().setBlockAndUpdate(farm.ground(cells.get(0)), Blocks.AIR.defaultBlockState()); helper.getLevel().setBlockAndUpdate(farm.ground(cells.get(1)).above(), Blocks.DIRT.defaultBlockState());
		var a = worker(helper, farm); var b = worker(helper, farm); a.setCommand(RatCommand.HARVEST); b.setCommand(RatCommand.HARVEST); a.moveTo(farm.getBlockPos().above().west().getCenter()); b.moveTo(farm.getBlockPos().above().east().getCenter());
		boolean[] grown = {false}, damaged = {false}; boolean[] accumulated = {false, false};
		helper.succeedWhen(() -> {
			for (int i = 0; i < 2; i++) { var rat = i == 0 ? a : b; for (int slot = 0; slot < rat.cargo.slots(); slot++) if (rat.cargo.get(slot).is(Items.WHEAT) && rat.cargo.get(slot).getCount() > 1) accumulated[i] = true; }
			boolean planted = cells.stream().allMatch(cell -> helper.getLevel().getBlockState(farm.ground(cell).above()).is(Blocks.WHEAT));
			if (!grown[0] && planted) { grown[0] = true; for (int cell : cells) helper.getLevel().setBlockAndUpdate(farm.ground(cell).above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7)); }
			int wheat = java.util.stream.IntStream.range(0, 54).filter(slot -> farm.getItem(slot).is(Items.WHEAT)).map(slot -> farm.getItem(slot).getCount()).sum();
			if (grown[0] && !damaged[0] && wheat >= cells.size() && planted && a.cargo.isEmpty() && b.cargo.isEmpty()) {
				damaged[0] = true; int cell = cells.get(0); helper.getLevel().setBlockAndUpdate(farm.ground(cell).above(), Blocks.AIR.defaultBlockState()); helper.getLevel().setBlockAndUpdate(farm.ground(cell), Blocks.DIRT.defaultBlockState());
				helper.getLevel().setBlockAndUpdate(farm.ground(farm.layout().water.nextSetBit(0)), Blocks.DIRT.defaultBlockState()); helper.getLevel().setBlockAndUpdate(farm.ground(farm.layout().lights.nextSetBit(0)), Blocks.DIRT.defaultBlockState());
			}
			helper.assertTrue(damaged[0] && planted && wheat >= cells.size() && a.cargo.isEmpty() && b.cargo.isEmpty(), "Full farm cycle/repair: grown=" + grown[0] + ", damaged=" + damaged[0] + ", wheat=" + wheat + "; a=" + a.position() + "/" + a.getWorkDebugState() + "; b=" + b.position() + "/" + b.getWorkDebugState());
			for (int cell = 0; cell < 256; cell++) if (farm.layout().contains(cell)) {
				var state = helper.getLevel().getBlockState(farm.ground(cell)); helper.assertTrue(farm.layout().water.get(cell) ? state.is(Blocks.WATER) && state.getFluidState().isSource() : farm.layout().lights.get(cell) ? state.is(RatsBlockRegistry.RAT_FARM_LIGHT.get()) : state.is(Blocks.FARMLAND), "Ground, irrigation and lighting repaired");
			}
			helper.assertTrue(accumulated[0] && accumulated[1], "Both workers accumulate harvests before returning"); a.setCommand(RatCommand.SIT); b.setCommand(RatCommand.SIT);
		});
	}
}
