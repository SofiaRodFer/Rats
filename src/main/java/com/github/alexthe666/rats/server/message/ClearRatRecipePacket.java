package com.github.alexthe666.rats.server.message;

import com.github.alexthe666.rats.server.block.entity.RatCraftingTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ClearRatRecipePacket(long blockPos) {

	public static ClearRatRecipePacket decode(FriendlyByteBuf buf) {
		return new ClearRatRecipePacket(buf.readLong());
	}

	public static void encode(ClearRatRecipePacket packet, FriendlyByteBuf buf) {
		buf.writeLong(packet.blockPos());
	}

	public static class Handler {
		public static void handle(ClearRatRecipePacket packet, Supplier<NetworkEvent.Context> context) {
			context.get().enqueueWork(() -> {
				Player player = context.get().getSender();
				if (player != null) {
					var menu = RatPacketAuthority.table(player, packet.blockPos());
					if (menu != null) {
						var table = menu.getCraftingTable();
						table.matrixHandler.ifPresent(handler -> {
							for (int slot = 0; slot < handler.getSlots(); slot++) {
								handler.setStackInSlot(slot, ItemStack.EMPTY);
							}
						});
						table.setChanged();
					}
				}
			});
			context.get().setPacketHandled(true);
		}
	}
}
