package com.github.alexthe666.rats.server.message;
import com.github.alexthe666.rats.server.inventory.RatStorageMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record StorageSnapshotPacket(int menu, CompoundTag data) {
	public static void encode(StorageSnapshotPacket p, FriendlyByteBuf b) { b.writeInt(p.menu); b.writeNbt(p.data); }
	public static StorageSnapshotPacket decode(FriendlyByteBuf b) { return new StorageSnapshotPacket(b.readInt(), b.readNbt()); }
	public static void handle(StorageSnapshotPacket p, Supplier<NetworkEvent.Context> supplier) { var c = supplier.get(); c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Client.accept(p))); c.setPacketHandled(true); }
	private static class Client { static void accept(StorageSnapshotPacket p) { var player = Minecraft.getInstance().player; if (player != null && player.containerMenu instanceof RatStorageMenu menu && menu.containerId == p.menu && p.data != null) menu.snapshot = p.data; } }
}
