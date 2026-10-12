package com.github.alexthe666.rats.server.message;

import com.github.alexthe666.rats.server.inventory.RatCageMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record CageRosterPacket(int menuId, int selected, int capacity, boolean complete, int lanternCooldown, List<Resident> residents) {
	public record Resident(int id, Component name, boolean male, boolean baby, boolean owned, int cooldown, boolean home, String variant) { }
	public static void encode(CageRosterPacket packet, FriendlyByteBuf buf) {
		buf.writeInt(packet.menuId); buf.writeInt(packet.selected); buf.writeInt(packet.capacity); buf.writeBoolean(packet.complete); buf.writeInt(packet.lanternCooldown); buf.writeVarInt(packet.residents.size());
		for (Resident rat : packet.residents) { buf.writeInt(rat.id); buf.writeComponent(rat.name); buf.writeBoolean(rat.male); buf.writeBoolean(rat.baby); buf.writeBoolean(rat.owned); buf.writeInt(rat.cooldown); buf.writeBoolean(rat.home); buf.writeUtf(rat.variant, 128); }
	}
	public static CageRosterPacket decode(FriendlyByteBuf buf) {
		int menu = buf.readInt(), selected = buf.readInt(), capacity = buf.readInt(); boolean complete = buf.readBoolean(); int cooldown = buf.readInt();
		int count = buf.readVarInt();
		if (count < 0 || count > 8192) throw new IllegalArgumentException("Invalid cage roster size");
		List<Resident> residents = new ArrayList<>(count);
		for (int i = 0; i < count; i++) residents.add(new Resident(buf.readInt(), buf.readComponent(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readInt(), buf.readBoolean(), buf.readUtf(128)));
		return new CageRosterPacket(menu, selected, capacity, complete, cooldown, List.copyOf(residents));
	}
	public static void handle(CageRosterPacket packet, Supplier<NetworkEvent.Context> supplier) {
		NetworkEvent.Context context = supplier.get();
		context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.accept(packet)));
		context.setPacketHandled(true);
	}
	private static class ClientHandler {
		static void accept(CageRosterPacket packet) {
			if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.containerMenu instanceof RatCageMenu menu && menu.containerId == packet.menuId) menu.snapshot = packet;
		}
	}
}
