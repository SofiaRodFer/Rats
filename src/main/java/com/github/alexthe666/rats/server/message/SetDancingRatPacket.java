package com.github.alexthe666.rats.server.message;

import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SetDancingRatPacket(int ratId, boolean setDancing, long blockPos, int moves) {

	public static SetDancingRatPacket decode(FriendlyByteBuf buf) {
		return new SetDancingRatPacket(buf.readInt(), buf.readBoolean(), buf.readLong(), buf.readInt());
	}

	public static void encode(SetDancingRatPacket packet, FriendlyByteBuf buf) {
		buf.writeInt(packet.ratId());
		buf.writeBoolean(packet.setDancing());
		buf.writeLong(packet.blockPos());
		buf.writeInt(packet.moves());
	}

	public static class Handler {
		public static void handle(SetDancingRatPacket packet, Supplier<NetworkEvent.Context> context) {
			context.get().enqueueWork(() -> {
				Player player = context.get().getSender();
				if (player != null) {
					Entity entity = player.level().getEntity(packet.ratId());
					if (entity instanceof TamedRat rat) {
						BlockPos pos = BlockPos.of(packet.blockPos());
						if (!rat.isAlive() || packet.moves() < 0 || packet.moves() > 3 || !player.level().hasChunkAt(pos) || rat.distanceToSqr(pos.getCenter()) > 64) return;
						var state = player.level().getBlockState(pos);
						boolean playing = state.is(net.minecraft.world.level.block.Blocks.JUKEBOX) && player.level().getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.JukeboxBlockEntity jukebox && jukebox.isRecordPlaying();
						if (!packet.setDancing() && rat.isDancing() && !pos.equals(rat.jukeboxPos)) return;
						if (packet.setDancing() != playing) return;
						if (!rat.isDancing() && packet.setDancing()) {
							rat.setDanceMoves(packet.moves());
						}
						rat.setDancing(packet.setDancing());
						rat.jukeboxPos = BlockPos.of(packet.blockPos());
					}
				}
			});
			context.get().setPacketHandled(true);
		}
	}
}
