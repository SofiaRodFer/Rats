package com.github.alexthe666.rats.server.message;
import com.github.alexthe666.rats.server.inventory.RatStorageMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record StorageActionPacket(int menu, int action, int index, int amount) {
	public static void encode(StorageActionPacket p, FriendlyByteBuf b) { b.writeInt(p.menu); b.writeInt(p.action); b.writeInt(p.index); b.writeInt(p.amount); }
	public static StorageActionPacket decode(FriendlyByteBuf b) { return new StorageActionPacket(b.readInt(), b.readInt(), b.readInt(), b.readInt()); }
	public static void handle(StorageActionPacket p, Supplier<NetworkEvent.Context> supplier) { var c = supplier.get(); c.enqueueWork(() -> { var player = c.getSender(); if (player != null && player.containerMenu instanceof RatStorageMenu menu && menu.containerId == p.menu) menu.action(player, p.action, p.index, p.amount); }); c.setPacketHandled(true); }
}
