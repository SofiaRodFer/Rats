package com.github.alexthe666.rats.server.storage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;
/** Claims survive unloading and prevent two controllers using the same chest. */
public final class StorageLedger extends SavedData {
	private final Map<BlockPos, UUID> claims = new HashMap<>();
	public static StorageLedger get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(StorageLedger::load, StorageLedger::new, "rats_storage_claims"); }
	public boolean claim(BlockPos pos, UUID network) { UUID owner = this.claims.get(pos); if (owner != null && !owner.equals(network)) return false; this.claims.put(pos.immutable(), network); this.setDirty(); return true; }
	private static List<BlockPos> chestParts(net.minecraft.world.level.Level level, BlockPos pos) {
		if (!level.hasChunkAt(pos)) return List.of(pos);
		var state = level.getBlockState(pos); if (state.hasProperty(net.minecraft.world.level.block.ChestBlock.TYPE) && state.hasProperty(net.minecraft.world.level.block.ChestBlock.FACING) && state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) return List.of(pos, pos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state))); return List.of(pos);
	}
	public boolean claimChest(net.minecraft.world.level.Level level, BlockPos pos, UUID network) { var parts = chestParts(level, pos); if (parts.stream().anyMatch(p -> this.claims.containsKey(p) && !network.equals(this.claims.get(p)))) return false; parts.forEach(p -> this.claim(p, network)); return true; }
	public boolean usable(net.minecraft.world.level.Level level, BlockPos pos, UUID network) { var parts = chestParts(level, pos); return parts.stream().anyMatch(p -> network.equals(this.claims.get(p))) && parts.stream().noneMatch(p -> this.claims.containsKey(p) && !network.equals(this.claims.get(p))); }
	public void releaseChest(net.minecraft.world.level.Level level, BlockPos pos, UUID network) { chestParts(level,pos).forEach(p -> this.release(p,network)); }
	public void releaseNetwork(UUID network) { this.claims.entrySet().removeIf(e -> e.getValue().equals(network)); this.setDirty(); }
	public void release(BlockPos pos, UUID network) { if (network.equals(this.claims.get(pos))) { this.claims.remove(pos); this.setDirty(); } }
	@Override public CompoundTag save(CompoundTag tag) { ListTag list = new ListTag(); this.claims.forEach((pos, id) -> { var t = new CompoundTag(); t.putLong("Pos", pos.asLong()); t.putUUID("Network", id); list.add(t); }); tag.put("Claims", list); return tag; }
	public static StorageLedger load(CompoundTag tag) { var ledger = new StorageLedger(); for (Tag value : tag.getList("Claims", Tag.TAG_COMPOUND)) { var t = (CompoundTag)value; if (t.hasUUID("Network")) ledger.claims.put(BlockPos.of(t.getLong("Pos")), t.getUUID("Network")); } return ledger; }
}
