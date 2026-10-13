package com.github.alexthe666.rats.server.storage;
import com.github.alexthe666.rats.server.block.RatStorageBlock;
import com.github.alexthe666.rats.server.block.entity.RatStorageBlockEntity;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class StorageScheduler {
	private StorageScheduler() { }
	public static StorageTask claim(RatStorageBlockEntity c, TamedRat rat) {
		var level = c.getLevel(); var graph = StorageRoutes.graph(level, c.getBlockPos()); Set<BlockPos> seen = new HashSet<>();
		List<StorageRoutes.Port> chests = new ArrayList<>(), inputs = new ArrayList<>();
		for (BlockPos pos : c.endpoints) {
			if (!level.hasChunkAt(pos) || !seen.add(StorageStock.identity(level, pos))) continue;
			if (level.getBlockEntity(pos) instanceof RatStorageBlockEntity endpoint) { if (endpoint.kind() == RatStorageBlock.Kind.INPUT && c.network.equals(endpoint.controllerNetwork) && pos.distManhattan(c.getBlockPos()) == 1) inputs.add(new StorageRoutes.Port(pos, Direction.UP, c.getBlockPos())); }
			else { var port = StorageRoutes.port(level, pos, graph); if (port != null) chests.add(port); }
		}
		for (var request : c.requests) {
			int reserved = c.tasks.values().stream().filter(t -> request.id().equals(t.request)).mapToInt(t -> (t.collected ? t.loaded : t.maximum) - t.delivered).sum();
			int need = request.amount() - request.delivered() - reserved; if (need <= 0) continue;
			if (request.craftSlot() < 0 && !StorageStock.output(c, request.output())) continue;
			for (var source : chests) { var task = make(c, rat, source, request.craftSlot() < 0 ? request.output() : c.getBlockPos(), Direction.UP, request.item(), need, request.id()); if (task != null) return task; }
		}
		for (var input : inputs) {
			var handler = StorageRoutes.handler(level, input.inventory(), input.face()); if (handler == null) continue;
			for (int i = 0; i < handler.getSlots(); i++) {
				var item = handler.getStackInSlot(i); if (item.isEmpty()) continue; String category = StorageStock.category(item);
				chests.sort(Comparator.comparingInt(p -> score(c, p, item, category)));
				for (var dest : chests) {
					int score = score(c, dest, item, category); if (score >= 10) continue;
					var task = make(c, rat, input, dest.inventory(), dest.face(), item, rat.cargo.slots() * item.getMaxStackSize(), null);
					if (task != null) { if (score == 2) { c.categories.put(dest.inventory(), category); c.setChanged(); } return task; }
				}
			}
		}
		if (c.organize) {
			for (var source : chests) {
				var handler = StorageRoutes.handler(level, source.inventory(), source.face()); if (handler == null) continue;
				String assigned = c.categories.get(source.inventory());
				if (assigned == null || "mixed".equals(assigned)) {
					Map<String, Long> totals = new TreeMap<>(); for (int i = 0; i < handler.getSlots(); i++) { var item = handler.getStackInSlot(i); if (!item.isEmpty()) totals.merge(StorageStock.category(item), (long)item.getCount(), Long::sum); }
					if (totals.isEmpty()) continue; assigned = totals.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey(); c.categories.put(source.inventory(), assigned); c.setChanged();
				}
				for (int i = 0; i < handler.getSlots(); i++) {
					var item = handler.getStackInSlot(i); if (item.isEmpty() || StorageStock.category(item).equals(assigned)) continue;
					for (var dest : chests) if (!dest.inventory().equals(source.inventory()) && score(c, dest, item, StorageStock.category(item)) < 3) {
						var task = make(c, rat, source, dest.inventory(), dest.face(), item, rat.cargo.slots() * item.getMaxStackSize(), null);
						if (task != null) { c.categories.putIfAbsent(dest.inventory(), StorageStock.category(item)); c.setChanged(); return task; }
					}
				}
			}
			if (c.tasks.isEmpty()) { c.organize = false; c.setChanged(); }
		}
		return null;
	}
	private static int score(RatStorageBlockEntity c, StorageRoutes.Port p, ItemStack item, String category) {
		var handler = StorageRoutes.handler(c.getLevel(), p.inventory(), p.face()); if (handler == null || StorageInventory.capacity(handler, item, item.getMaxStackSize()) == 0) return 10;
		String assigned = c.categories.get(p.inventory()); if ("mixed".equals(assigned)) return 3;
		if (assigned != null && !assigned.equals(category)) return 10;
		if (StorageInventory.count(handler, item) > 0) return 0;
		if (category.equals(assigned)) return 1;
		for (int i = 0; i < handler.getSlots(); i++) if (!handler.getStackInSlot(i).isEmpty()) return 10;
		return 2;
	}
	private static StorageTask make(RatStorageBlockEntity c, TamedRat rat, StorageRoutes.Port source, BlockPos destination, Direction face, ItemStack item, int need, UUID request) {
		var from = StorageRoutes.handler(c.getLevel(), source.inventory(), source.face()); var to = StorageRoutes.handler(c.getLevel(), destination, face); if (from == null || to == null) return null;
		if (request != null) { var r = c.requests.stream().filter(x -> x.id().equals(request)).findFirst().orElse(null); if (r != null && r.craftSlot() >= 0) to = new net.minecraftforge.items.wrapper.RangedWrapper(new net.minecraftforge.items.wrapper.InvWrapper(c.session(r.player())), 9 + r.craftSlot(), 10 + r.craftSlot()); }
		long claimed = c.tasks.values().stream().filter(t -> !t.collected && StorageStock.identity(c.getLevel(), t.source).equals(StorageStock.identity(c.getLevel(), source.inventory())) && StorageInventory.same(t.item, item)).mapToLong(t -> t.maximum).sum();
		int count = (int)Math.min(Math.min(need, rat.cargo.slots() * item.getMaxStackSize()), Math.max(0, StorageInventory.count(from, item) - claimed));
		int reservedSpace = c.tasks.values().stream().filter(t -> t.destination.equals(destination) && StorageInventory.same(t.item, item)).mapToInt(t -> (t.collected ? t.loaded : t.maximum) - t.delivered).sum();
		int fits = StorageInventory.capacity(to, item, count + reservedSpace);
		count = Math.min(count, Math.max(0, fits - reservedSpace)); if (count <= 0 || !rat.cargo.canAccept(item)) return null;
		var task = new StorageTask(source.inventory(), source.face(), destination, face, item, count, request); c.tasks.put(rat.getUUID(), task); c.setChanged(); return task;
	}
}
