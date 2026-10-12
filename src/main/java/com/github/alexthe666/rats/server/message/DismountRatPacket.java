package com.github.alexthe666.rats.server.message;

import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record DismountRatPacket(int ratId) {
	public static boolean canDismount(Player player, TamedRat rat) {
		return rat.isAlive() && rat.getVehicle() == player && player.getUUID().equals(rat.getOwnerUUID());
	}
	public static boolean dismount(Player player, TamedRat rat) {
		if (!canDismount(player, rat)) return false;
		Vec3 destination = null;
		Vec3 facing = player.getLookAngle().multiply(1, 0, 1).normalize();
		for (int radius = 1; radius <= 3 && destination == null; radius++) for (int side = 0; side < 8 && destination == null; side++) {
			double angle = side * Math.PI / 4;
			Vec3 offset = new Vec3(facing.x * Math.cos(angle) - facing.z * Math.sin(angle), 0, facing.x * Math.sin(angle) + facing.z * Math.cos(angle)).scale(radius);
			if (offset.lengthSqr() < 0.1) offset = new Vec3(radius, 0, 0);
			Vec3 candidate = player.position().add(offset);
			var pos = net.minecraft.core.BlockPos.containing(candidate);
			if (player.level().hasChunkAt(pos) && !player.level().isOutsideBuildHeight(pos) && player.level().getFluidState(pos).isEmpty() && player.level().noCollision(rat, rat.getBoundingBox().move(candidate.subtract(rat.position())))) destination = candidate;
		}
		if (destination == null) return false;
		rat.stopRiding(); if (rat.isPassenger()) return false;
		rat.getNavigation().stop(); rat.setJumping(false); rat.setDeltaMovement(Vec3.ZERO); rat.fallDistance = 0;
		rat.setNoGravity(rat.isFlying());
		rat.moveTo(destination.x, destination.y, destination.z, rat.getYRot(), rat.getXRot()); rat.hasImpulse = true;
		if (player.level() instanceof net.minecraft.server.level.ServerLevel level) level.getChunkSource().broadcastAndSend(player, new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(player));
		return true;
	}

	public static DismountRatPacket decode(FriendlyByteBuf buf) {
		return new DismountRatPacket(buf.readInt());
	}

	public static void encode(DismountRatPacket packet, FriendlyByteBuf buf) {
		buf.writeInt(packet.ratId());
	}

	public static class Handler {
		public static void handle(DismountRatPacket packet, Supplier<NetworkEvent.Context> context) {
			context.get().enqueueWork(() -> {
				Player player = context.get().getSender();
				if (player != null) {
					Entity entity = player.level().getEntity(packet.ratId());
					if (entity instanceof TamedRat rat) dismount(player, rat);
				}
			});
			context.get().setPacketHandled(true);
		}
	}
}
