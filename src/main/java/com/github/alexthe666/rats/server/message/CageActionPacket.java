package com.github.alexthe666.rats.server.message;

import com.github.alexthe666.rats.server.inventory.RatCageMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record CageActionPacket(int menuId, int action, int ratId, int partnerId, String name) {
	public static void encode(CageActionPacket packet, FriendlyByteBuf buf) { buf.writeInt(packet.menuId); buf.writeInt(packet.action); buf.writeInt(packet.ratId); buf.writeInt(packet.partnerId); buf.writeUtf(packet.name, 32); }
	public static CageActionPacket decode(FriendlyByteBuf buf) { return new CageActionPacket(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readUtf(32)); }
	public static void handle(CageActionPacket packet, Supplier<NetworkEvent.Context> supplier) {
		NetworkEvent.Context context = supplier.get();
		context.enqueueWork(() -> {
			if (context.getSender() != null && context.getSender().containerMenu instanceof RatCageMenu menu && menu.containerId == packet.menuId) menu.action(context.getSender(), packet.action, packet.ratId, packet.partnerId, packet.name);
		});
		context.setPacketHandled(true);
	}
}
