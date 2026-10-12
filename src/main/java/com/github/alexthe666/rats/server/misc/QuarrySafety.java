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
	public static boolean prepareOreBlock(TamedRat rat, BlockPos target) {
		Level level = rat.level(); var lining = new LinkedHashSet<BlockPos>();
		for (Direction direction : Direction.values()) {
			BlockPos neighbor = target.relative(direction);
			if (!level.hasChunkAt(neighbor) || level.isOutsideBuildHeight(neighbor)) return false;
			if (!level.getFluidState(neighbor).isEmpty()) { if (!canReplace(level, neighbor, rat)) return false; lining.add(neighbor); }
		}
		for (BlockPos pos : lining) level.setBlockAndUpdate(pos, RatsBlockRegistry.RAT_QUARRY_LINING.get().defaultBlockState());
		return true;
	}
	public static boolean prepareSlice(RatQuarryBlockEntity quarry, TamedRat rat) {
		return prepareSlice(quarry, rat, quarry.getProgress());
	}
	public static boolean prepareSlice(RatQuarryBlockEntity quarry, TamedRat rat, int depth) {
		if (depth >= quarry.getDepthLimit()) return true;
		return repairSlice(quarry, rat, quarry.getDigDirection(), depth);
	}
	public static boolean repairSlice(RatQuarryBlockEntity quarry, TamedRat rat, Direction direction, int depth) {
		Level level = rat.level();
		int width = quarry.getWidth();
		if (depth < 0) return false;
		Set<BlockPos> lining = new LinkedHashSet<>();
		Set<BlockPos> drain = new LinkedHashSet<>();
		for (int row = -1; row <= width; row++) for (int column = -1; column <= width; column++) {
			boolean shell = row == -1 || column == -1 || row == width || column == width;
			BlockPos pos = QuarryGeometry.cell(quarry.getBlockPos(), direction, width, depth, column, row);
			if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) return false;
			BlockState state = level.getBlockState(pos);
			if (shell && (!state.getFluidState().isEmpty() || (state.isAir() && touchesFluid(level, pos)))) lining.add(pos);
			else if (!shell && !state.getFluidState().isEmpty()) drain.add(pos);
		}
		// Cap fluids at the next face before exposing the current slice.
		for (int row = 0; row < width; row++) for (int column = 0; column < width; column++) {
			BlockPos pos = QuarryGeometry.cell(quarry.getBlockPos(), direction, width, depth + 1, column, row);
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
		if (direction == Direction.DOWN) {
			// Provide the original entry bridge and an exact-width spiral return path.
			if (depth == 0) {
				for (int z = 0; z >= -width / 2; z--) placePlatform(level, quarry.getBlockPos().offset(0, -1, z), rat);
				for (int x = 0; x >= -width / 2; x--) placePlatform(level, quarry.getBlockPos().offset(x, -1, -width / 2), rat);
			}
			placePlatform(level, QuarryGeometry.stair(quarry.getBlockPos(), width, depth), rat);
		} else {
			if (!ensureHorizontalFloor(quarry, rat, direction, depth) || (depth == 0 && !ensureHorizontalFloor(quarry, rat, direction, -1))) return false;
		}
		placeQuarryLights(quarry, rat, direction, depth);
		return true;
	}
	public static boolean ensureHorizontalFloor(RatQuarryBlockEntity quarry, TamedRat rat, int depth) {
		return ensureHorizontalFloor(quarry, rat, quarry.getDigDirection(), depth);
	}
	public static boolean ensureHorizontalFloor(RatQuarryBlockEntity quarry, TamedRat rat, Direction direction, int depth) {
		if (direction == Direction.DOWN) return true;
		Level level = rat.level();
		for (int column = 0; column < quarry.getWidth(); column++) {
			BlockPos floor = QuarryGeometry.cell(quarry.getBlockPos(), direction, quarry.getWidth(), depth, column, -1);
			if (!level.hasChunkAt(floor) || level.isOutsideBuildHeight(floor)) return false;
			var state = level.getBlockState(floor);
			if (!state.getCollisionShape(level, floor).isEmpty()) continue; // Preserve natural/partial floors.
			if (!(state.isAir() || state.liquid()) || !canReplace(level, floor, rat)) return false;
			level.setBlockAndUpdate(floor, RatsBlockRegistry.RAT_QUARRY_PLATFORM.get().defaultBlockState());
		}
		return true;
	}
	public static void placeQuarryLights(RatQuarryBlockEntity quarry, TamedRat rat, int depth) {
		placeQuarryLights(quarry, rat, quarry.getDigDirection(), depth);
	}
	public static void placeQuarryLights(RatQuarryBlockEntity quarry, TamedRat rat, Direction direction, int depth) {
		if (depth < 0 || (depth + 1) % 5 != 0) return;
		Level level = rat.level(); int width = quarry.getWidth();
		boolean down = direction == Direction.DOWN;
		// Do not light an open cave: require a roof and both lateral walls.
		if (!down) for (int column = 0; column < width; column++) { BlockPos roof = QuarryGeometry.cell(quarry.getBlockPos(), direction, width, depth, column, width); if (!level.hasChunkAt(roof) || !level.getBlockState(roof).isSolid()) return; }
		for (int side = 0; side < (down ? 4 : 2); side++) {
			BlockPos pos = lightPosition(quarry, direction, depth, side);
			Direction facing = lightFacing(direction, side);
			BlockPos support = pos.relative(facing.getOpposite());
			if (!level.hasChunkAt(support) || !level.getBlockState(support).isFaceSturdy(level, support, facing)) return;
		}
		for (int side = 0; side < (down ? 4 : 2); side++) {
			BlockPos pos = lightPosition(quarry, direction, depth, side);
			if (!level.isEmptyBlock(pos) || !canReplace(level, pos, rat)) continue;
			Direction facing = lightFacing(direction, side);
			var state = RatsBlockRegistry.RAT_QUARRY_TORCH.get().defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, facing);
			if (state.canSurvive(level, pos)) level.setBlockAndUpdate(pos, state);
		}
	}
	public static BlockPos lightPosition(RatQuarryBlockEntity quarry, Direction direction, int depth, int side) {
		int width = quarry.getWidth();
		if (direction != Direction.DOWN) return QuarryGeometry.cell(quarry.getBlockPos(), direction, width, depth, side == 0 ? 0 : width - 1, 1);
		return switch (side) { case 0 -> QuarryGeometry.cell(quarry.getBlockPos(), direction, width, depth, 0, width / 2); case 1 -> QuarryGeometry.cell(quarry.getBlockPos(), direction, width, depth, width - 1, width / 2); case 2 -> QuarryGeometry.cell(quarry.getBlockPos(), direction, width, depth, width / 2, 0); default -> QuarryGeometry.cell(quarry.getBlockPos(), direction, width, depth, width / 2, width - 1); };
	}
	public static Direction lightFacing(Direction direction, int side) {
		if (direction != Direction.DOWN) return side == 0 ? direction.getClockWise() : direction.getCounterClockWise();
		return switch (side) { case 0 -> Direction.EAST; case 1 -> Direction.WEST; case 2 -> Direction.SOUTH; default -> Direction.NORTH; };
	}
	private static boolean touchesFluid(Level level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			BlockPos neighbor = pos.relative(direction);
			if (level.hasChunkAt(neighbor) && !level.getFluidState(neighbor).isEmpty()) return true;
		}
		return false;
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
		for (int depth = Math.max(0, currentDepth - 2); depth <= Math.min(Math.max(quarry.getProgress(), quarry.historicalExtent(Direction.DOWN)), currentDepth + 2); depth++) {
			BlockPos pos = QuarryGeometry.stair(quarry.getBlockPos(), quarry.getWidth(), depth);
			if (rat.level().hasChunkAt(pos) && pos.getY() >= rat.level().getMinBuildHeight()) placePlatform(rat.level(), pos, rat);
		}
	}
}
