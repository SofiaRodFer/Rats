package com.github.alexthe666.rats.server.misc;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Deferred unlink, never a source of items or a reason to load a station chunk. */
public final class WorkstationLedger extends SavedData {
	private final Map<BlockPos, Set<UUID>> retired = new HashMap<>();
	public static WorkstationLedger get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(WorkstationLedger::load, WorkstationLedger::new, "rats_workstation_retirements");
	}
	public void retire(BlockPos station, UUID rat) {
		this.retired.computeIfAbsent(station.immutable(), key -> new HashSet<>()).add(rat);
		this.setDirty();
	}
	public boolean take(BlockPos station, UUID rat) {
		var ids = this.retired.get(station);
		if (ids == null || !ids.remove(rat)) return false;
		if (ids.isEmpty()) this.retired.remove(station);
		this.setDirty();
		return true;
	}
	@Override public CompoundTag save(CompoundTag tag) {
		var records = new ListTag();
		this.retired.forEach((pos, ids) -> ids.forEach(id -> {
			var record = new CompoundTag(); record.putLong("Station", pos.asLong()); record.putUUID("Rat", id); records.add(record);
		}));
		tag.put("Retired", records); return tag;
	}
	public static WorkstationLedger load(CompoundTag tag) {
		var ledger = new WorkstationLedger();
		for (Tag entry : tag.getList("Retired", Tag.TAG_COMPOUND)) {
			var record = (CompoundTag)entry;
			if (record.hasUUID("Rat")) ledger.retire(BlockPos.of(record.getLong("Station")), record.getUUID("Rat"));
		}
		return ledger;
	}
}
