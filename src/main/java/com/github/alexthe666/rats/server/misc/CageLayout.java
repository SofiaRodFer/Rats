package com.github.alexthe666.rats.server.misc;

import com.github.alexthe666.rats.server.block.RatCageBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import java.util.HashSet;
import java.util.Comparator;
import java.util.Set;

/** Derived topology: never replaces Cage IDs, contained items or residents. */
public final class CageLayout {
	private CageLayout() { }
	public static int base(CageHabitat habitat) { return habitat.cages().stream().mapToInt(BlockPos::getY).min().orElse(0); }
	public static boolean capacityLevel(CageHabitat habitat, BlockPos pos) { return habitat.cages().contains(pos) && (pos.getY() - base(habitat)) % 2 == 0; }
	public static void synchronize(Level level, CageHabitat habitat) {
		if (level.isClientSide() || !habitat.complete() || habitat.cages().isEmpty()) return;
		int base = base(habitat);
		Set<BlockPos> ladders = new HashSet<>();
		for (int y = base + 2; y <= habitat.cages().stream().mapToInt(BlockPos::getY).max().orElse(base); y += 2) {
			final int floorY = y;
			// One interior ladder per separate floor component, not one per block.
			Set<BlockPos> remaining = new HashSet<>(habitat.cages().stream().filter(pos -> pos.getY() == floorY).toList());
			while (!remaining.isEmpty()) {
				var component = new HashSet<BlockPos>(); var pending = new java.util.ArrayDeque<BlockPos>();
				pending.add(remaining.iterator().next());
				while (!pending.isEmpty()) { var pos = pending.removeFirst(); if (!remaining.remove(pos)) continue; component.add(pos); for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) pending.add(pos.relative(direction)); }
				var direct = component.stream().filter(pos -> habitat.cages().contains(pos.below()) && habitat.cages().contains(pos.below(2)))
						.min(Comparator.<BlockPos>comparingInt(pos -> level.getBlockEntity(pos) == null && level.getBlockEntity(pos.below()) == null && level.getBlockEntity(pos.below(2)) == null ? 0 : 1).thenComparing(Comparator.naturalOrder()))
						.orElse(null);
				if (direct != null) { ladders.add(direct); ladders.add(direct.below()); ladders.add(direct.below(2)); }
				else connectOffsetFloor(habitat, component, base, floorY, ladders);
			}
		}
		for (BlockPos pos : habitat.cages()) {
			var state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof RatCageBlock)) continue;
			boolean shelf = (pos.getY() - base) % 2 == 0 && state.getValue(RatCageBlock.DOWN) != 0;
			var updated = state.setValue(RatCageBlock.SHELF, shelf).setValue(RatCageBlock.LADDER, ladders.contains(pos));
			// Connectivity did not change. Avoid validating neighbouring decorations
			// against a half-updated layout while another shelf is still pending.
			if (updated != state) level.setBlock(pos, updated, net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
		}
	}
	private static void connectOffsetFloor(CageHabitat habitat, Set<BlockPos> floor, int base, int y, Set<BlockPos> ladders) {
		var previous = new java.util.HashMap<BlockPos, BlockPos>(); var pending = new java.util.ArrayDeque<BlockPos>();
		for (var pos : floor.stream().sorted().toList()) { previous.put(pos, pos); pending.add(pos); }
		BlockPos end = null;
		while (!pending.isEmpty()) {
			var current = pending.removeFirst();
			if (habitat.cages().contains(current) && current.getY() < y && (current.getY() - base) % 2 == 0) { end = current; break; }
			for (var direction : net.minecraft.core.Direction.values()) { var next = current.relative(direction); if (habitat.nodes().contains(next) && !previous.containsKey(next)) { previous.put(next, current); pending.add(next); } }
		}
		if (end == null) return;
		for (var current = end; !previous.get(current).equals(current); current = previous.get(current)) {
			var next = previous.get(current);
			if (next.getY() != current.getY() && habitat.cages().contains(current) && habitat.cages().contains(next)) { ladders.add(current); ladders.add(next); }
		}
	}
}
