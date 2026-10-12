package com.github.alexthe666.rats.server.message;

import com.github.alexthe666.rats.server.inventory.RatQuarryMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;

public record QuarryRosterPacket(int menuId, int assigned, int limit, List<Worker> workers) {
	public record Worker(Component name, boolean male, String variant, boolean returning, boolean owned) { }
	public static void encode(QuarryRosterPacket packet, FriendlyByteBuf buf) {
		buf.writeInt(packet.menuId); buf.writeInt(packet.assigned); buf.writeInt(packet.limit); buf.writeVarInt(packet.workers.size());
		for (Worker worker : packet.workers) { buf.writeComponent(worker.name); buf.writeBoolean(worker.male); buf.writeUtf(worker.variant, 128); buf.writeBoolean(worker.returning); buf.writeBoolean(worker.owned); }
	}
	public static QuarryRosterPacket decode(FriendlyByteBuf buf) {
		int menu = buf.readInt(), assigned = buf.readInt(), limit = buf.readInt(), count = buf.readVarInt();
		if (count < 0 || count > 4) throw new IllegalArgumentException("Invalid quarry roster");
		List<Worker> workers = new ArrayList<>();
		for (int i = 0; i < count; i++) workers.add(new Worker(buf.readComponent(), buf.readBoolean(), buf.readUtf(128), buf.readBoolean(), buf.readBoolean()));
		return new QuarryRosterPacket(menu, assigned, limit, List.copyOf(workers));
	}
	public static void handle(QuarryRosterPacket packet, Supplier<NetworkEvent.Context> supplier) {
		var context = supplier.get();
		context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.accept(packet)));
		context.setPacketHandled(true);
	}
	private static class ClientHandler {
		static void accept(QuarryRosterPacket packet) {
			if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.containerMenu instanceof RatQuarryMenu menu && menu.containerId == packet.menuId) menu.snapshot = packet;
		}
	}
}
