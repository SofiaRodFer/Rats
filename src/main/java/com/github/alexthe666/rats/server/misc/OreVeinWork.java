package com.github.alexthe666.rats.server.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.Tags;
import java.util.LinkedHashSet;
import java.util.ArrayDeque;
import java.util.List;

/** Bounded connected ore search; no chunk generation and no non-ore excavation. */
public final class OreVeinWork {
	public static final int MAX_BLOCKS = 512, MAX_DISTANCE = 16;
	private final LinkedHashSet<BlockPos> pending = new LinkedHashSet<>();
	private BlockPos quarry;
	private net.minecraft.world.phys.Vec3 returnPoint;
	public void rememberReturnPoint(net.minecraft.world.phys.Vec3 point) { if (this.returnPoint == null) this.returnPoint = point; }
	public net.minecraft.world.phys.Vec3 returnPoint() { return this.returnPoint; }
	public void clearReturnPoint() { this.returnPoint = null; }
	public List<BlockPos> targets(BlockPos quarry) {
		if (!quarry.equals(this.quarry)) { this.pending.clear(); this.returnPoint = null; this.quarry = quarry.immutable(); }
		return List.copyOf(this.pending);
	}
	public void remove(BlockPos pos) { this.pending.remove(pos); }
	public void discover(Level level, BlockPos quarry, BlockPos exposed) {
		this.targets(quarry);
		var queue = new ArrayDeque<BlockPos>(); var visited = new java.util.HashSet<BlockPos>();
		queue.add(exposed); visited.add(exposed);
		while (!queue.isEmpty() && this.pending.size() < MAX_BLOCKS && visited.size() < MAX_BLOCKS * 27) {
			BlockPos current = queue.removeFirst();
			for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
				if (x == 0 && y == 0 && z == 0) continue;
				BlockPos next = current.offset(x, y, z);
				if (Math.abs(next.getX() - exposed.getX()) > MAX_DISTANCE || Math.abs(next.getY() - exposed.getY()) > MAX_DISTANCE || Math.abs(next.getZ() - exposed.getZ()) > MAX_DISTANCE || !visited.add(next)) continue;
				if (!level.isOutsideBuildHeight(next) && level.hasChunkAt(next) && level.getBlockState(next).is(Tags.Blocks.ORES) && level.getBlockEntity(next) == null && this.pending.size() < MAX_BLOCKS) { this.pending.add(next); queue.add(next); }
			}
		}
	}
	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		if (this.quarry != null) tag.putLong("Quarry", this.quarry.asLong());
		if (this.returnPoint != null) { tag.putDouble("ReturnX", this.returnPoint.x); tag.putDouble("ReturnY", this.returnPoint.y); tag.putDouble("ReturnZ", this.returnPoint.z); }
		tag.putLongArray("Targets", this.pending.stream().mapToLong(BlockPos::asLong).toArray()); return tag;
	}
	public void load(CompoundTag tag) {
		this.pending.clear(); this.quarry = tag.contains("Quarry") ? BlockPos.of(tag.getLong("Quarry")) : null;
		this.returnPoint = tag.contains("ReturnX") ? new net.minecraft.world.phys.Vec3(tag.getDouble("ReturnX"), tag.getDouble("ReturnY"), tag.getDouble("ReturnZ")) : null;
		for (long pos : tag.getLongArray("Targets")) { if (this.pending.size() == MAX_BLOCKS) break; this.pending.add(BlockPos.of(pos)); }
	}
}
