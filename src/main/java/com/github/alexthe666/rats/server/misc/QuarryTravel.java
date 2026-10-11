package com.github.alexthe666.rats.server.misc;

import com.github.alexthe666.rats.server.block.entity.RatQuarryBlockEntity;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Short waypoints avoid requesting a 512-block path from a short-range navigator. */
public final class QuarryTravel {
	private QuarryTravel() { }
	public static void moveTo(TamedRat rat, RatQuarryBlockEntity quarry, Vec3 destination) {
		Vec3 next = destination;
		if (rat.distanceToSqr(destination) > 144.0D) {
			if (quarry.getDigDirection() == Direction.DOWN && rat.getY() < quarry.getBlockPos().getY()) {
				int currentDepth = Math.max(0, quarry.getBlockPos().getY() - rat.blockPosition().getY());
				int nextDepth = Math.max(0, currentDepth + (destination.y < rat.getY() ? 4 : -4));
				next = Vec3.atBottomCenterOf(QuarryGeometry.stair(quarry.getBlockPos(), quarry.getWidth(), nextDepth).above());
			} else {
				Vec3 delta = destination.subtract(rat.position());
				next = rat.position().add(delta.normalize().scale(10.0D));
			}
		}
		BlockPos point = BlockPos.containing(next);
		if (rat.level().hasChunkAt(point) && rat.level().getFluidState(point).isEmpty()) rat.getNavigation().moveTo(next.x, next.y, next.z, 1.25D);
	}
}
