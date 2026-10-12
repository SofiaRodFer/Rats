package com.github.alexthe666.rats.server.misc;

import com.github.alexthe666.rats.server.block.entity.RatQuarryBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Read-only inspection. Repair must be reserved, approached and performed by a rat. */
public final class QuarryAudit {
	private QuarryAudit() { }
	public record Task(Direction direction, int depth, BlockPos target) { }
	public static Task inspect(RatQuarryBlockEntity quarry, Direction direction, int depth) {
		Level level = quarry.getLevel(); if (level == null || depth < 0) return null;
		int width = quarry.getWidth();
		if (direction == Direction.DOWN) {
			if (depth == 0) {
				for (int z = 0; z >= -width / 2; z--) { BlockPos entry = quarry.getBlockPos().offset(0, -1, z); if (level.hasChunkAt(entry) && level.getBlockState(entry).isAir()) return new Task(direction, depth, entry); }
				for (int x = 0; x >= -width / 2; x--) { BlockPos entry = quarry.getBlockPos().offset(x, -1, -width / 2); if (level.hasChunkAt(entry) && level.getBlockState(entry).isAir()) return new Task(direction, depth, entry); }
			}
			BlockPos stair = QuarryGeometry.stair(quarry.getBlockPos(), width, depth);
			if (level.hasChunkAt(stair) && !level.isOutsideBuildHeight(stair) && level.getBlockState(stair).isAir()) return new Task(direction, depth, stair);
		} else for (int floorDepth = depth == 0 ? -1 : depth; floorDepth <= depth; floorDepth++) for (int column = 0; column < width; column++) {
			BlockPos floor = QuarryGeometry.cell(quarry.getBlockPos(), direction, width, floorDepth, column, -1);
			if (level.hasChunkAt(floor) && !level.isOutsideBuildHeight(floor) && level.getBlockState(floor).getCollisionShape(level, floor).isEmpty() && (level.getBlockState(floor).isAir() || level.getBlockState(floor).liquid())) return new Task(direction, depth, floor);
		}
		for (int row = -1; row <= width; row++) for (int column = -1; column <= width; column++) {
			BlockPos pos = QuarryGeometry.cell(quarry.getBlockPos(), direction, width, depth, column, row);
			if (level.hasChunkAt(pos) && !level.isOutsideBuildHeight(pos) && !level.getFluidState(pos).isEmpty()) return new Task(direction, depth, pos);
		}
		if ((depth + 1) % 5 != 0) return null;
		if (direction != Direction.DOWN) for (int column = 0; column < width; column++) {
			BlockPos roof = QuarryGeometry.cell(quarry.getBlockPos(), direction, width, depth, column, width);
			if (!level.hasChunkAt(roof) || !level.getBlockState(roof).isSolid()) return null;
		}
		int sides = direction == Direction.DOWN ? 4 : 2;
		for (int side = 0; side < sides; side++) {
			BlockPos pos = QuarrySafety.lightPosition(quarry, direction, depth, side), support = pos.relative(QuarrySafety.lightFacing(direction, side).getOpposite());
			if (!level.hasChunkAt(support) || !level.getBlockState(support).isFaceSturdy(level, support, QuarrySafety.lightFacing(direction, side))) return null;
		}
		for (int side = 0; side < sides; side++) {
			BlockPos pos = QuarrySafety.lightPosition(quarry, direction, depth, side);
			// Existing player lights/solid blocks are preserved, not replaced.
			if (level.hasChunkAt(pos) && level.isEmptyBlock(pos)) return new Task(direction, depth, pos);
		}
		return null;
	}
}
