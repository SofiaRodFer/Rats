package com.github.alexthe666.rats.server.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Exact cross sections: unlike inclusive radius boxes, even widths stay even. */
public final class QuarryGeometry {
	private QuarryGeometry() { }

	public static BlockPos cell(BlockPos origin, Direction direction, int width, int depth, int column, int row) {
		int lateral = column - width / 2;
		if (direction == Direction.DOWN) return origin.offset(lateral, -depth - 1, row - width / 2);
		if (direction.getAxis().isVertical()) throw new IllegalArgumentException("Quarries cannot mine upwards");
		return origin.relative(direction, depth + 1).relative(direction.getClockWise(), lateral).above(row);
	}

	public static BlockPos stair(BlockPos origin, int width, int depth) {
		int edge = width - 1;
		int step = depth % (edge * 4);
		int x = -width / 2;
		int z = -width / 2;
		if (step < edge) z += step;
		else if (step < edge * 2) { z += edge; x += step - edge; }
		else if (step < edge * 3) { x += edge; z += edge - (step - edge * 2); }
		else x += edge - (step - edge * 3);
		return origin.offset(x, -depth - 1, z);
	}
}
