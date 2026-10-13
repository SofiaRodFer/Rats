package com.github.alexthe666.rats.server.message;

import com.github.alexthe666.rats.server.events.ForgeEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.function.Supplier;

public record SyncArmSwingPacket(ItemStack stack) {

	public static SyncArmSwingPacket decode(FriendlyByteBuf buf) {
		return new SyncArmSwingPacket(buf.readItem());
	}

	public static void encode(SyncArmSwingPacket packet, FriendlyByteBuf buf) {
		buf.writeItem(packet.stack());
	}

	public static class Handler {
		public static void handle(SyncArmSwingPacket packet, Supplier<NetworkEvent.Context> context) {
			context.get().enqueueWork(() -> {
				var player = context.get().getSender();
				if (player != null) ForgeEvents.handleArmSwing(player.getMainHandItem(), player);
			});
			context.get().setPacketHandled(true);
		}
	}
}
