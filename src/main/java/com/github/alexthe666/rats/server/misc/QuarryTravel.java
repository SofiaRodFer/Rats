package com.github.alexthe666.rats.server.misc;

import com.github.alexthe666.rats.server.block.entity.RatQuarryBlockEntity;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Short waypoints avoid requesting a 512-block path from a short-range navigator. */
public final class QuarryTravel {
	private QuarryTravel() { }
	public static String debugState(TamedRat rat, RatQuarryBlockEntity quarry) {
		StringBuilder result = new StringBuilder("path=").append(rat.getNavigation().getPath()).append("; stairs=");
		for (int i = 0; i < Math.min(10, quarry.getProgress()); i++) {
			BlockPos pos = QuarryGeometry.stair(quarry.getBlockPos(), quarry.getWidth(), i);
			result.append(i).append(':').append(rat.level().hasChunkAt(pos) ? rat.level().getBlockState(pos) : "unloaded").append(' ');
		}
		return result.toString();
	}
	public static void moveTo(TamedRat rat, RatQuarryBlockEntity quarry, Vec3 destination) {
		moveTo(rat, quarry, quarry.getDigDirection(), destination);
	}
	public static void moveTo(TamedRat rat, RatQuarryBlockEntity quarry, Direction direction, Vec3 destination) {
		if (direction != Direction.DOWN) {
			int depth = Math.max(0, (rat.blockPosition().getX() - quarry.getBlockPos().getX()) * direction.getStepX() + (rat.blockPosition().getZ() - quarry.getBlockPos().getZ()) * direction.getStepZ() - 1);
			int extent = Math.max(quarry.historicalExtent(direction), direction == quarry.getDigDirection() ? quarry.getDepthLimit() : 0);
			for (int i = Math.max(-1, depth - 1); i <= Math.min(extent - 1, depth + 2); i++) if (!QuarrySafety.ensureHorizontalFloor(quarry, rat, direction, i)) return;
		}
		Vec3 anchor = rat.oreVein.returnPoint();
		if (rat.quarryReturning && anchor != null) {
			// This is a one-way checkpoint, not a tether. Retaining it after
			// arrival alternates anchor/quarry paths forever as the rat leaves it.
			if (rat.distanceToSqr(anchor) <= 4) rat.oreVein.clearReturnPoint();
			else {
				BlockPos point = BlockPos.containing(anchor);
				var path = rat.level().hasChunkAt(point) ? rat.getNavigation().createPath(point, 0) : null;
				if (path != null && path.canReach()) { rat.getNavigation().moveTo(path, 1.25D); return; }
				// Old saved waypoints may have been obstructed since recording.
				rat.oreVein.clearReturnPoint();
			}
		}
		Vec3 next = destination;
		if (direction == Direction.DOWN && rat.getY() < quarry.getBlockPos().getY() - 2.0D && destination.y > rat.getY() + 1) {
			// Climb one spiral step at a time, even when the destination is nearby.
			int currentDepth = Math.max(0, quarry.getBlockPos().getY() - rat.blockPosition().getY());
			QuarrySafety.repairReturnStairs(quarry, rat, currentDepth);
			BlockPos higher = QuarryGeometry.stair(quarry.getBlockPos(), quarry.getWidth(), Math.max(0, currentDepth - 1));
			if (!rat.level().hasChunkAt(higher)) return;
			// A stair skipped while occupied is repaired after the rat steps aside.
			if (rat.level().getBlockState(higher).isAir()) higher = QuarryGeometry.stair(quarry.getBlockPos(), quarry.getWidth(), currentDepth);
			next = Vec3.atBottomCenterOf(higher.above());
		} else if (rat.distanceToSqr(destination) > 144.0D) {
			if (direction == Direction.DOWN && rat.getY() < quarry.getBlockPos().getY() - 2.0D) {
				int currentDepth = Math.max(0, quarry.getBlockPos().getY() - rat.blockPosition().getY());
				int nextDepth = Math.max(0, currentDepth + (destination.y < rat.getY() ? 4 : -4));
				next = Vec3.atBottomCenterOf(QuarryGeometry.stair(quarry.getBlockPos(), quarry.getWidth(), nextDepth).above());
			} else {
				Vec3 delta = destination.subtract(rat.position());
				next = rat.position().add(delta.normalize().scale(10.0D));
			}
		}
		BlockPos point = BlockPos.containing(next);
		if (next.y > rat.getY() + 0.25 && rat.getY() < quarry.getBlockPos().getY() - 1) rat.getJumpControl().jump();
		if (rat.level().hasChunkAt(point) && rat.level().getFluidState(point).isEmpty()) {
			// Accuracy one treats an adjacent stair as already reached, leaving the
			// rat standing in the very cell whose platform needs to be rebuilt.
			rat.getNavigation().moveTo(rat.getNavigation().createPath(point, 0), 1.25D);
		}
	}
}
