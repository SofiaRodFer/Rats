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
		for (int row = -1; row <= 4; row++) for (int col = -1; col <= 4; col++) {
			helper.assertTrue(helper.getLevel().getBlockState(quarry.cell(0, col, row)).isAir(), "Dry air must not become lining");
		}
		helper.succeed();
	}
	@GameTest(template = "quarry_test_empty")
	public static void exactCrossSections(GameTestHelper helper) {
		BlockPos origin = new BlockPos(7, 150, -11);
		for (int width : new int[]{4, 6}) for (Direction direction : RatQuarryBlockEntity.DIRECTIONS) {
			for (int depth : new int[]{0, 15, width == 4 ? 255 : 511}) {
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
		for (int i = 0; i < 2; i++) helper.getLevel().setBlockAndUpdate(quarry.cell(0, i, 0), Blocks.STONE.defaultBlockState());
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
	@GameTest(template = "quarry_test_empty", timeoutTicks = 3200)
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
		for (int depth = 0; depth < 2; depth++) for (int row = 0; row < 6; row++) for (int col = 0; col < 6; col++) helper.getLevel().setBlockAndUpdate(quarry.cell(depth, col, row), Blocks.STONE.defaultBlockState());
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
	private static void mineAndDeposit(GameTestHelper helper, boolean advanced) {
		RatQuarryBlockEntity quarry = quarry(helper, advanced);
		quarry.configure(1);
		for (int i = 0; i < 31; i++) quarry.configure(5);
		int width = quarry.getWidth();
		// Natural ground, not free generated cheese floors, supports dry tunnels.
		for (int depth = -1; depth < 16; depth++) for (int col = -1; col <= width; col++) helper.getLevel().setBlockAndUpdate(quarry.cell(depth, col, -1), Blocks.STONE.defaultBlockState());
		for (int depth = 0; depth < 2; depth++) for (int row = 0; row < width; row++) for (int col = 0; col < width; col++) helper.getLevel().setBlockAndUpdate(quarry.cell(depth, col, row), Blocks.COBBLESTONE.defaultBlockState());
		TamedRat rat = rat(helper, true);
		rat.setItemSlot(EquipmentSlot.LEGS, new ItemStack(RatsItemRegistry.RAT_UPGRADE_QUARRY.get()));
		rat.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos()));
		rat.setCommand(RatCommand.HARVEST);
		rat.moveTo(quarry.getBlockPos().above().getCenter());
		rat.setNoAi(false);
		helper.succeedWhen(() -> {
			helper.assertTrue(quarry.getProgress() == 16, "Rat finishes configured tunnel: progress=" + quarry.getProgress() + ", status=" + quarry.getStatus() + ", quarry=" + quarry.getBlockPos() + ", rat=" + rat.position() + ", alive=" + rat.isAlive() + ", movable=" + rat.canMove() + ", ticks=" + rat.tickCount + ", cargo=" + rat.getMainHandItem() + ", returning=" + rat.quarryReturning + ", goals=" + rat.getWorkDebugState() + ", command=" + rat.getCommand());
			int deposited = 0;
			for (int i = 0; i < quarry.getContainerSize(); i++) deposited += quarry.getItem(i).getCount();
			helper.assertTrue(deposited == width * width * 2 && rat.cargo.isEmpty(), "All mined blocks deposited");
		});
	}

	@GameTest(template = "quarry_test_empty", timeoutTicks = 6000)
	public static void downwardRatReturnsWithCargo(GameTestHelper helper) {
		RatQuarryBlockEntity quarry = quarry(helper, false);
		for (int depth = 0; depth < 2; depth++) for (int row = 0; row < 4; row++) for (int col = 0; col < 4; col++) helper.getLevel().setBlockAndUpdate(quarry.cell(depth, col, row), Blocks.COBBLESTONE.defaultBlockState());
		TamedRat rat = rat(helper, true);
		rat.setItemSlot(EquipmentSlot.LEGS, new ItemStack(RatsItemRegistry.RAT_UPGRADE_QUARRY.get()));
		rat.setDepositPos(GlobalPos.of(helper.getLevel().dimension(), quarry.getBlockPos()));
		rat.setCommand(RatCommand.HARVEST);
		rat.moveTo(quarry.getBlockPos().above().getCenter());
		rat.setNoAi(false);
		helper.succeedWhen(() -> {
			int deposited = 0;
			for (int i = 0; i < quarry.getContainerSize(); i++) deposited += quarry.getItem(i).getCount();
			helper.assertTrue(deposited >= 32 && rat.cargo.isEmpty() && rat.isAlive(), "Downward shaft returns mined cargo: deposited=" + deposited + ", progress=" + quarry.getProgress() + ", status=" + quarry.getStatus() + ", rat=" + rat.position() + ", work=" + rat.getWorkDebugState() + "; travel=" + com.github.alexthe666.rats.server.misc.QuarryTravel.debugState(rat, quarry));
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
