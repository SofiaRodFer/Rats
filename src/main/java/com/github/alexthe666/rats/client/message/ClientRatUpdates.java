package com.github.alexthe666.rats.client.message;

import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.message.UpdateRatFluidPacket;
import net.minecraft.client.Minecraft;

/** Trusted S2C mirror updates. Authoritative state always belongs to the server. */
public final class ClientRatUpdates {
	private ClientRatUpdates() {}
	public static void fluid(UpdateRatFluidPacket packet) {
		var level = Minecraft.getInstance().level;
		if (level != null && level.getEntity(packet.ratId()) instanceof TamedRat rat)
			rat.transportingFluid = packet.fluid().copy();
	}
}
