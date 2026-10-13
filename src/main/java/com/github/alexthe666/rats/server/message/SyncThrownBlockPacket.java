package com.github.alexthe666.rats.server.message;

import com.github.alexthe666.rats.server.entity.projectile.ThrownBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SyncThrownBlockPacket(int blockEntityId, long blockPos) {

	public static SyncThrownBlockPacket decode(FriendlyByteBuf buf) {
		return new SyncThrownBlockPacket(buf.readInt(), buf.readLong());
	}

	public static void encode(SyncThrownBlockPacket packet, FriendlyByteBuf buf) {
		buf.writeInt(packet.blockEntityId());
		buf.writeLong(packet.blockPos());
	}

	public static class Handler {

		public static void handle(SyncThrownBlockPacket packet, Supplier<NetworkEvent.Context> context) {
			// Retain the legacy discriminator; CARRIED_BLOCK entity metadata is authoritative.
			// The source position may already be air when this packet arrives.
			context.get().setPacketHandled(true);
		}
	}
}
