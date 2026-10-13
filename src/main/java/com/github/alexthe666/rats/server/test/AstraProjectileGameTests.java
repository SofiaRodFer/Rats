package com.github.alexthe666.rats.server.test;

import com.github.alexthe666.rats.registry.RatsEntityRegistry;
import com.github.alexthe666.rats.server.entity.projectile.ThrownBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Conservation regressions for actual projectile impacts and legacy entity saves. */
@GameTestHolder("rats")
@PrefixGameTestTemplate(false)
public class AstraProjectileGameTests {

	private static final class TestBlock extends ThrownBlock {
		private TestBlock(ServerLevel level, BlockPos pos) {
			super(RatsEntityRegistry.THROWN_BLOCK.get(), level);
			this.setPos(pos.getCenter());
		}
		private void impact(BlockPos pos) {
			this.onHit(new BlockHitResult(pos.getCenter(), Direction.UP, pos, false));
		}
	}

	private static int dropped(GameTestHelper h, BlockPos pos) {
		return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream().mapToInt(e -> e.getItem().getCount()).sum();
	}

	@GameTest(template = "quarry_test_empty")
	public static void thrownBlockPlacesOnceWithoutBonusDrop(GameTestHelper h) {
		var floor = h.absolutePos(new BlockPos(8, 4, 8));
		h.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
		h.getLevel().setBlockAndUpdate(floor.above(), Blocks.AIR.defaultBlockState());
		var projectile = new TestBlock(h.getLevel(), floor);
		projectile.setHeldBlockState(Blocks.DIAMOND_BLOCK.defaultBlockState());
		projectile.impact(floor);
		projectile.impact(floor); // An already-discarded entity cannot repeat the transaction.
		h.assertTrue(h.getLevel().getBlockState(floor.above()).is(Blocks.DIAMOND_BLOCK), "Physical block is placed");
		h.assertTrue(dropped(h, floor) == 0 && projectile.isRemoved(), "Successful placement has no duplicate item, including repeat impact");
		h.succeed();
	}

	@GameTest(template = "quarry_test_empty")
	public static void occupiedDestinationKeepsBlockAndDropsContainerNbtOnce(GameTestHelper h) {
		var floor = h.absolutePos(new BlockPos(8, 4, 8));
		h.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
		h.getLevel().setBlockAndUpdate(floor.above(), Blocks.EMERALD_BLOCK.defaultBlockState());
		var projectile = new TestBlock(h.getLevel(), floor);
		projectile.setHeldBlockState(Blocks.CHEST.defaultBlockState());
		var source = new ChestBlockEntity(floor.east(5), Blocks.CHEST.defaultBlockState());
		var diamonds = new ItemStack(Items.DIAMOND, 7);
		diamonds.setHoverName(net.minecraft.network.chat.Component.literal("Saved payload"));
		source.setItem(0, diamonds);
		projectile.tileEntityData = source.saveWithFullMetadata();
		projectile.impact(floor); projectile.impact(floor);
		h.assertTrue(h.getLevel().getBlockState(floor.above()).is(Blocks.EMERALD_BLOCK), "Occupied destination is never overwritten");
		var items = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(floor).inflate(3));
		h.assertTrue(items.size() == 1 && items.get(0).getItem().is(Items.CHEST) && items.get(0).getItem().getCount() == 1, "Exactly one physical fallback chest");
		var saved = items.get(0).getItem().getTagElement("BlockEntityTag");
		h.assertTrue(saved != null && !saved.contains("x") && !saved.contains("y") && !saved.contains("z"), "Fallback carries portable block entity NBT");
		var restored = new ChestBlockEntity(floor, Blocks.CHEST.defaultBlockState()); restored.load(saved);
		h.assertTrue(ItemStack.isSameItemSameTags(restored.getItem(0), diamonds) && restored.getItem(0).getCount() == 7, "Fallback conserves named inventory items exactly");
		h.succeed();
	}

	@GameTest(template = "quarry_test_empty")
	public static void placedContainerActuallyLoadsSavedItems(GameTestHelper h) {
		var floor = h.absolutePos(new BlockPos(8, 4, 8));
		h.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
		h.getLevel().setBlockAndUpdate(floor.above(), Blocks.AIR.defaultBlockState());
		var source = new ChestBlockEntity(floor.east(5), Blocks.CHEST.defaultBlockState());
		source.setItem(4, new ItemStack(Items.GOLD_INGOT, 19));
		var projectile = new TestBlock(h.getLevel(), floor);
		projectile.setHeldBlockState(Blocks.CHEST.defaultBlockState());
		projectile.tileEntityData = source.saveWithFullMetadata();
		projectile.impact(floor);
		var placed = (ChestBlockEntity) h.getLevel().getBlockEntity(floor.above());
		h.assertTrue(placed != null && placed.getItem(4).is(Items.GOLD_INGOT) && placed.getItem(4).getCount() == 19, "Placed block entity loads the merged tag, not a discarded temporary compound");
		h.assertTrue(dropped(h, floor) == 0, "Restored inventory does not also become a dropped chest");
		h.succeed();
	}

	@GameTest(template = "quarry_test_empty")
	public static void psychicNoDropFlagSurvivesReload(GameTestHelper h) {
		var floor = h.absolutePos(new BlockPos(8, 4, 8));
		h.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
		h.getLevel().setBlockAndUpdate(floor.above(), Blocks.AIR.defaultBlockState());
		var original = new TestBlock(h.getLevel(), floor);
		original.setHeldBlockState(Blocks.DIAMOND_BLOCK.defaultBlockState()); original.dropBlock = false;
		var tag = new CompoundTag(); original.addAdditionalSaveData(tag);
		var restored = new TestBlock(h.getLevel(), floor); restored.readAdditionalSaveData(tag);
		h.assertTrue(!restored.dropBlock, "Visual psychic copy remains nonphysical after save/load");
		restored.impact(floor);
		h.assertTrue(h.getLevel().isEmptyBlock(floor.above()) && dropped(h, floor) == 0, "Psychic copy never creates a physical block or item");
		tag.remove("DropBlock");
		var legacy = new TestBlock(h.getLevel(), floor); legacy.readAdditionalSaveData(tag);
		h.assertTrue(legacy.dropBlock, "Legacy saves preserve their original physical-block default");
		h.succeed();
	}
}
