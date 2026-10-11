package com.github.alexthe666.rats.server.misc;

import com.github.alexthe666.rats.registry.RatsBlockRegistry;
import com.github.alexthe666.rats.server.block.entity.RatQuarryBlockEntity;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashSet;
import java.util.Set;

/** Seal a whole slice before draining it. Generated lining never drops resources. */
public final class QuarrySafety {
	private QuarrySafety() { }
	public static boolean prepareSlice(RatQuarryBlockEntity quarry, TamedRat rat) {
		Level level = rat.level();
		int depth = quarry.getProgress(), width = quarry.getWidth();
		if (depth >= quarry.getDepthLimit()) return true;
		Set<BlockPos> lining = new LinkedHashSet<>();
		Set<BlockPos> drain = new LinkedHashSet<>();
		if (depth == 0 && quarry.getDigDirection() != Direction.DOWN) {
			for (int column = 0; column < width; column++) {
				BlockPos entry = quarry.cell(-1, column, -1);
				if (!level.hasChunkAt(entry) || level.isOutsideBuildHeight(entry)) return false;
				BlockState state = level.getBlockState(entry);
				if (state.isAir() || !state.getFluidState().isEmpty() || state.canBeReplaced()) lining.add(entry);
			}
		}
		for (int row = -1; row <= width; row++) for (int column = -1; column <= width; column++) {
			boolean shell = row == -1 || column == -1 || row == width || column == width;
			BlockPos pos = quarry.cell(depth, column, row);
			if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) return false;
			BlockState state = level.getBlockState(pos);
			if (shell && (state.isAir() || !state.getFluidState().isEmpty() || state.canBeReplaced())) lining.add(pos);
			else if (!shell && !state.getFluidState().isEmpty()) drain.add(pos);
		}
		// Cap fluids at the next face before exposing the current slice.
		for (int row = 0; row < width; row++) for (int column = 0; column < width; column++) {
			BlockPos pos = quarry.cell(depth + 1, column, row);
			if (level.isOutsideBuildHeight(pos)) continue;
			if (!level.hasChunkAt(pos)) return false;
			if (!level.getFluidState(pos).isEmpty()) lining.add(pos);
		}
		for (BlockPos pos : lining) if (!canReplace(level, pos, rat)) return false;
		for (BlockPos pos : drain) if (!canReplace(level, pos, rat)) return false;
		for (BlockPos pos : lining) level.setBlockAndUpdate(pos, RatsBlockRegistry.RAT_QUARRY_LINING.get().defaultBlockState());
		for (BlockPos pos : drain) {
			BlockState state = level.getBlockState(pos);
			// Keep waterlogged solid blocks for normal mining; remove their contained water.
			if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
				level.setBlockAndUpdate(pos, state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false));
			} else if (state.liquid()) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			else return false;
		}
		if (quarry.getDigDirection() == Direction.DOWN) {
			// Provide the original entry bridge and an exact-width spiral return path.
			if (depth == 0) {
				for (int z = 0; z >= -width / 2; z--) placePlatform(level, quarry.getBlockPos().offset(0, -1, z), rat);
				for (int x = 0; x >= -width / 2; x--) placePlatform(level, quarry.getBlockPos().offset(x, -1, -width / 2), rat);
			}
			placePlatform(level, QuarryGeometry.stair(quarry.getBlockPos(), width, depth), rat);
		}
		return true;
	}
	private static boolean canReplace(Level level, BlockPos pos, TamedRat rat) {
		BlockState state = level.getBlockState(pos);
		return level.getBlockEntity(pos) == null && (state.isAir() || state.liquid() || RatUtils.canRatBreakBlock(level, pos, rat))
				&& !RatUtils.isBlockProtected(level, pos, rat) && RatUtils.canRatPlaceBlock(level, pos, rat)
				&& level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(pos)).isEmpty();
	}
	private static void placePlatform(Level level, BlockPos pos, TamedRat rat) {
		if (level.isEmptyBlock(pos) && canReplace(level, pos, rat)) level.setBlockAndUpdate(pos, RatsBlockRegistry.RAT_QUARRY_PLATFORM.get().defaultBlockState());
	}
	public static void repairReturnStairs(RatQuarryBlockEntity quarry, TamedRat rat, int currentDepth) {
		for (int depth = Math.max(0, currentDepth - 2); depth <= Math.min(quarry.getProgress(), currentDepth + 2); depth++) {
			BlockPos pos = QuarryGeometry.stair(quarry.getBlockPos(), quarry.getWidth(), depth);
			if (rat.level().hasChunkAt(pos) && pos.getY() >= rat.level().getMinBuildHeight()) placePlatform(rat.level(), pos, rat);
		}
	}
}
