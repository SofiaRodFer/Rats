package com.github.alexthe666.rats.server.storage;
import com.github.alexthe666.rats.server.block.RatStorageBlock;
import com.github.alexthe666.rats.server.block.entity.RatStorageBlockEntity;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import java.util.*;

public final class StorageStock {
	private StorageStock() { }
	public record Key(Item item, CompoundTag tag) { public static Key of(ItemStack stack) { return new Key(stack.getItem(), stack.getTag() == null ? null : stack.getTag().copy()); } }
	public record Entry(ItemStack item, long stored, long reserved, long transit, long input) { public long available() { return Math.max(0, this.stored - this.reserved); } }
	public static BlockPos identity(Level level, BlockPos pos) {
		var state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) { BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state)); if (other.compareTo(pos) < 0) return other; }
		return pos;
	}
	public static boolean output(RatStorageBlockEntity controller, BlockPos pos) { return controller.endpoints.contains(pos) && controller.getLevel().hasChunkAt(pos) && controller.getLevel().getBlockEntity(pos) instanceof RatStorageBlockEntity output && output.kind() == RatStorageBlock.Kind.OUTPUT && controller.getBlockPos().distManhattan(pos) == 1 && controller.network.equals(output.controllerNetwork); }
	public static List<Entry> scan(RatStorageBlockEntity controller) {
		Level level = controller.getLevel(); if (level == null) return List.of(); var graph = StorageRoutes.graph(level, controller.getBlockPos());
		Map<Key, ItemStack> samples = new LinkedHashMap<>(); Map<Key, Long> stored = new HashMap<>(), input = new HashMap<>(), reserved = new HashMap<>(), transit = new HashMap<>(); Set<BlockPos> seen = new HashSet<>();
		for (BlockPos pos : controller.endpoints) {
			if (!level.hasChunkAt(pos) || !seen.add(identity(level, pos))) continue;
			boolean isInput = level.getBlockEntity(pos) instanceof RatStorageBlockEntity b && b.kind() == RatStorageBlock.Kind.INPUT && controller.network.equals(b.controllerNetwork) && controller.getBlockPos().distManhattan(pos) == 1;
			if (level.getBlockEntity(pos) instanceof RatStorageBlockEntity && !isInput) continue;
			var port = isInput ? new StorageRoutes.Port(pos, Direction.UP, controller.getBlockPos()) : StorageRoutes.port(level, pos, graph); if (port == null) continue;
			var handler = StorageRoutes.handler(level, pos, port.face()); if (handler == null) continue;
			for (int i = 0; i < handler.getSlots(); i++) { ItemStack item = handler.getStackInSlot(i); if (item.isEmpty()) continue; Key key = Key.of(item); samples.putIfAbsent(key, item.copyWithCount(1)); (isInput ? input : stored).merge(key, (long)item.getCount(), Long::sum); }
		}
		for (var request : controller.requests) { var key = Key.of(request.item()); samples.putIfAbsent(key, request.item().copyWithCount(1)); reserved.merge(key, (long)Math.max(0, request.amount() - request.delivered()), Long::sum); }
		for (var task : controller.tasks.values()) if (task.collected && task.request != null) { Key key = Key.of(task.item); transit.merge(key, (long)Math.max(0, task.loaded - task.delivered), Long::sum); }
		var entries = new ArrayList<Entry>(); samples.forEach((key, sample) -> { long moving = transit.getOrDefault(key, 0L); entries.add(new Entry(sample, stored.getOrDefault(key, 0L), Math.max(0, reserved.getOrDefault(key, 0L) - moving), moving, input.getOrDefault(key, 0L))); }); return entries;
	}
	public static String category(ItemStack item) {
		var tags = item.getTags().map(t -> t.location().getPath()).toList();
		if (item.getItem() instanceof TieredItem || item.getItem() instanceof ArmorItem || item.getItem() instanceof BowItem || item.getItem() instanceof ShieldItem) return "equipment";
		if (tags.stream().anyMatch(t -> t.startsWith("ores") || t.startsWith("ingots") || t.startsWith("gems") || t.startsWith("raw_materials"))) return "minerals";
		if (tags.stream().anyMatch(t -> t.contains("logs") || t.contains("planks") || t.contains("wood"))) return "wood";
		if (item.isEdible() || tags.stream().anyMatch(t -> t.contains("seed") || t.contains("crop") || t.contains("sapling"))) return "farming";
		if (item.getItem() instanceof BlockItem) return "building";
		if (item.is(Items.ROTTEN_FLESH) || item.is(Items.BONE) || item.is(Items.STRING) || item.is(Items.GUNPOWDER) || item.is(Items.SPIDER_EYE) || item.is(Items.ENDER_PEARL) || item.is(Items.LEATHER) || item.is(Items.FEATHER) || item.is(Items.SLIME_BALL)) return "drops";
		return "other";
	}
}
