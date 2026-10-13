package com.github.alexthe666.rats.server.storage;

import com.github.alexthe666.rats.server.block.RatTubeBlock;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import java.util.*;

/** Bounded, loaded-only, six-axis tube graph. Endpoints are not traversable. */
public final class StorageRoutes {
	private StorageRoutes() { }
	public record Port(BlockPos inventory, Direction face, BlockPos tube) { }
	public static boolean within(BlockPos controller, BlockPos pos) { return controller.distSqr(pos) <= 400; }
	public static IItemHandler handler(Level level, BlockPos inventory, Direction face) {
		if (!level.hasChunkAt(inventory)) return null;
		var entity = level.getBlockEntity(inventory);
		return entity == null ? null : entity.getCapability(ForgeCapabilities.ITEM_HANDLER, face).resolve().orElse(null);
	}
	public static boolean tube(Level level, BlockPos controller, BlockPos pos) {
		return within(controller, pos) && level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() instanceof RatTubeBlock;
	}
	public static Map<BlockPos, BlockPos> graph(Level level, BlockPos controller) {
		Map<BlockPos, BlockPos> previous = new LinkedHashMap<>(); var queue = new ArrayDeque<BlockPos>();
		for (Direction dir : Direction.values()) { var start = controller.relative(dir); if (tube(level, controller, start)) { previous.put(start, controller); queue.add(start); } }
		while (!queue.isEmpty() && previous.size() < 32768) {
			BlockPos node = queue.remove();
			for (Direction dir : Direction.values()) { BlockPos next = node.relative(dir); if (!previous.containsKey(next) && tube(level, controller, next)) { previous.put(next, node); queue.add(next); } }
		}
		return previous;
	}
	public static Port port(Level level, BlockPos inventory, Map<BlockPos, BlockPos> graph) {
		for (Direction face : Direction.values()) { BlockPos tube = inventory.relative(face); if (graph.containsKey(tube) && handler(level, inventory, face) != null) return new Port(inventory, face, tube); }
		return null;
	}
	public static List<BlockPos> route(Level level, BlockPos controller, BlockPos from, BlockPos to) {
		if (from.equals(to)) return List.of(to);
		Map<BlockPos, BlockPos> prev = new HashMap<>(); var queue = new ArrayDeque<BlockPos>(); queue.add(from); prev.put(from, from);
		while (!queue.isEmpty() && prev.size() < 32768) {
			BlockPos node = queue.remove();
			for (Direction dir : Direction.values()) {
				BlockPos next = node.relative(dir);
				if (prev.containsKey(next) || !(next.equals(controller) || tube(level, controller, next))) continue;
				prev.put(next, node);
				if (next.equals(to)) { var path = new ArrayList<BlockPos>(); for (BlockPos p = to; !p.equals(from); p = prev.get(p)) path.add(p); Collections.reverse(path); return path; }
				queue.add(next);
			}
		}
		return List.of();
	}
}
