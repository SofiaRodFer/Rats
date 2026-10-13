package com.github.alexthe666.rats.server.message;

import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record SyncRatTagPacket(int ratId, List<GlobalPos> nodes) {
	public static final int MAX_NODES = 256;

	public static SyncRatTagPacket decode(FriendlyByteBuf buf) {
		int id = buf.readInt();
		int size = buf.readVarInt();
		if (size < 0 || size > MAX_NODES) throw new io.netty.handler.codec.DecoderException("Patrol exceeds 256 nodes");
		var nodes = new java.util.ArrayList<GlobalPos>(size);
		for (int i = 0; i < size; i++) nodes.add(buf.readGlobalPos());
		return new SyncRatTagPacket(id, List.copyOf(nodes));
	}

	public static void encode(SyncRatTagPacket packet, FriendlyByteBuf buf) {
		buf.writeInt(packet.ratId());
		buf.writeCollection(packet.nodes(), FriendlyByteBuf::writeGlobalPos);
	}

	public static class Handler {
		public static void handle(SyncRatTagPacket packet, Supplier<NetworkEvent.Context> context) {
			context.get().setPacketHandled(true);
			context.get().enqueueWork(() -> {
				Player player = context.get().getSender();

				if (player != null) {
					TamedRat rat = RatPacketAuthority.staffRat(player, packet.ratId(), 2);
					if (rat != null && packet.nodes().size() <= MAX_NODES && packet.nodes().stream().allMatch(pos -> pos.dimension().equals(player.level().dimension()) && !player.level().isOutsideBuildHeight(pos.pos()))) {
						rat.getPatrolNodes().clear();
						rat.getPatrolNodes().addAll(packet.nodes());
					}
				}

			});
		}
	}
}
