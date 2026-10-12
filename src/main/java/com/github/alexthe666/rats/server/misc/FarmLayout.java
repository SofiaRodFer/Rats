package com.github.alexthe666.rats.server.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.BitSet;

/** Fixed 16-column seed indices survive dimension changes without shifting rows. */
public final class FarmLayout {
	public final int width, height;
	public final BitSet water = new BitSet(256), lights = new BitSet(256);
	public FarmLayout(int width, int height) {
		this.width = Math.max(1, Math.min(16, width)); this.height = Math.max(1, Math.min(16, height));
		int nx = (this.width + 8) / 9, nz = (this.height + 8) / 9;
		for (int z = 0; z < nz; z++) for (int x = 0; x < nx; x++) {
			int cx = (x * this.width / nx + (x + 1) * this.width / nx - 1) / 2;
			int cz = (z * this.height / nz + (z + 1) * this.height / nz - 1) / 2;
			this.water.set(cz * 16 + cx);
		}
		// Ground emits 15, crop above receives 14; Manhattan distance <=5
		// keeps crop light >=9. Greedy coverage uses no perimeter outside the plot.
		BitSet uncovered = new BitSet(256);
		for (int z = 0; z < this.height; z++) for (int x = 0; x < this.width; x++) if (!this.water.get(z * 16 + x)) uncovered.set(z * 16 + x);
		while (!uncovered.isEmpty()) {
			int best = -1, score = -1;
			for (int z = 0; z < this.height; z++) for (int x = 0; x < this.width; x++) {
				int index = z * 16 + x; if (this.water.get(index) || this.lights.get(index)) continue;
				int count = 0; for (int cell = uncovered.nextSetBit(0); cell >= 0; cell = uncovered.nextSetBit(cell + 1)) if (Math.abs(cell % 16 - x) + Math.abs(cell / 16 - z) <= 5) count++;
				if (count > score) { score = count; best = index; }
			}
			if (best < 0) break; this.lights.set(best);
			for (int cell = uncovered.nextSetBit(0); cell >= 0; cell = uncovered.nextSetBit(cell + 1)) if (Math.abs(cell % 16 - best % 16) + Math.abs(cell / 16 - best / 16) <= 5) uncovered.clear(cell);
		}
	}
	public boolean contains(int index) { return index >= 0 && index < 256 && index % 16 < this.width && index / 16 < this.height; }
	public boolean cropCell(int index) { return this.contains(index) && !this.water.get(index) && !this.lights.get(index); }
	public BlockPos ground(BlockPos station, Direction direction, int index) { return station.relative(direction, index / 16 + 1).relative(direction.getClockWise(), index % 16 - this.width / 2).below(); }
}
