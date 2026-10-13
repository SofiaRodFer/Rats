package com.github.alexthe666.rats.server.storage;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;
public final class StorageTask {
	public final BlockPos source, destination;
	public final Direction sourceFace, destinationFace;
	public final ItemStack item;
	public final UUID request;
	public final int maximum;
	public boolean collected;
	public int delivered;
	public int loaded;
	public java.util.List<ItemStack> parcels = java.util.List.of();
	public void capture(com.github.alexthe666.rats.server.entity.rat.TamedRat rat) { var cargo = new java.util.ArrayList<ItemStack>(); for (int i = 0; i < 10; i++) if (!rat.cargo.get(i).isEmpty()) cargo.add(rat.cargo.get(i).copy()); this.parcels = java.util.List.copyOf(cargo); }
	public int reservedFor(ItemStack sample) { if (!this.collected) return StorageInventory.same(sample,this.item) ? this.maximum : 0; return this.parcels.stream().filter(p -> StorageInventory.same(p,sample)).mapToInt(ItemStack::getCount).sum(); }
	public StorageTask(BlockPos source, Direction sourceFace, BlockPos destination, Direction destinationFace, ItemStack item, int maximum, UUID request) { this.source = source; this.sourceFace = sourceFace; this.destination = destination; this.destinationFace = destinationFace; this.item = item.copyWithCount(1); this.maximum = maximum; this.request = request; }
	public CompoundTag save() { var t = new CompoundTag(); t.putLong("Source", this.source.asLong()); t.putLong("Destination", this.destination.asLong()); t.putInt("SourceFace", this.sourceFace.ordinal()); t.putInt("DestinationFace", this.destinationFace.ordinal()); t.put("Item", this.item.save(new CompoundTag())); t.putInt("Maximum", this.maximum); if (this.request != null) t.putUUID("Request", this.request); t.putBoolean("Collected", this.collected); t.putInt("Delivered", this.delivered); t.putInt("Loaded", this.loaded); var cargo = new net.minecraft.nbt.ListTag(); this.parcels.forEach(p -> cargo.add(p.save(new CompoundTag()))); t.put("Parcels",cargo); return t; }
	public static StorageTask load(CompoundTag t) { var task = new StorageTask(BlockPos.of(t.getLong("Source")), Direction.from3DDataValue(t.getInt("SourceFace")), BlockPos.of(t.getLong("Destination")), Direction.from3DDataValue(t.getInt("DestinationFace")), ItemStack.of(t.getCompound("Item")), Math.max(1, Math.min(640, t.getInt("Maximum"))), t.hasUUID("Request") ? t.getUUID("Request") : null); task.collected = t.getBoolean("Collected"); task.delivered = t.getInt("Delivered"); task.loaded = Math.max(0, Math.min(640, t.getInt("Loaded"))); var cargo = new java.util.ArrayList<ItemStack>(); for (var entry : t.getList("Parcels", net.minecraft.nbt.Tag.TAG_COMPOUND)) if (cargo.size()<10) { var stack = ItemStack.of((CompoundTag)entry); if (!stack.isEmpty()) cargo.add(stack); } task.parcels = java.util.List.copyOf(cargo); return task; }
}
