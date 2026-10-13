package com.github.alexthe666.rats.server.storage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;
public record StorageRequest(UUID id, UUID player, ItemStack item, int amount, int delivered, BlockPos output, int craftSlot) {
	public StorageRequest deliver(int amount) { return new StorageRequest(this.id, this.player, this.item, this.amount, this.delivered + amount, this.output, this.craftSlot); }
	public CompoundTag save() { var t = new CompoundTag(); t.putUUID("Id", this.id); t.putUUID("Player", this.player); t.put("Item", this.item.save(new CompoundTag())); t.putInt("Amount", this.amount); t.putInt("Delivered", this.delivered); t.putLong("Output", this.output.asLong()); t.putInt("CraftSlot", this.craftSlot); return t; }
	public static StorageRequest load(CompoundTag t) { return new StorageRequest(t.getUUID("Id"), t.getUUID("Player"), ItemStack.of(t.getCompound("Item")), Math.max(1, Math.min(100000, t.getInt("Amount"))), Math.max(0, t.getInt("Delivered")), BlockPos.of(t.getLong("Output")), Math.max(-1, Math.min(8, t.getInt("CraftSlot")))); }
}
