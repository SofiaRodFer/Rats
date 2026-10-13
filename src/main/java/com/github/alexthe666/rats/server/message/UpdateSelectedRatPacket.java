package com.github.alexthe666.rats.server.message;

import com.github.alexthe666.rats.registry.RatsCapabilityRegistry;
import com.github.alexthe666.rats.server.capability.SelectedRat;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record UpdateSelectedRatPacket(int entityId, int ratId) {

	public UpdateSelectedRatPacket(Entity entity, SelectedRat cap) {
		this(entity.getId(), cap.getSelectedRat() == null ? -1 : cap.getSelectedRat().getId());
	}

	public static UpdateSelectedRatPacket decode(FriendlyByteBuf buf) {
		return new UpdateSelectedRatPacket(buf.readInt(), buf.readInt());
	}

	public static void encode(UpdateSelectedRatPacket packet, FriendlyByteBuf buf) {
		buf.writeInt(packet.entityId());
		buf.writeInt(packet.ratId());
	}

	public static class Handler {

		@SuppressWarnings("Convert2Lambda")
		public static void handle(UpdateSelectedRatPacket packet, Supplier<NetworkEvent.Context> context) {
			context.get().enqueueWork(new Runnable() {
				@Override
				public void run() {
					if (Minecraft.getInstance().level == null) return;
					Entity entity = Minecraft.getInstance().level.getEntity(packet.entityId());
					if (entity instanceof LivingEntity) {
						Entity selected = Minecraft.getInstance().level.getEntity(packet.ratId());
						entity.getCapability(RatsCapabilityRegistry.SELECTED_RAT).ifPresent(cap -> cap.setSelectedRat(selected instanceof TamedRat rat ? rat : null));
					}
				}
			});
			context.get().setPacketHandled(true);
		}
	}
}
