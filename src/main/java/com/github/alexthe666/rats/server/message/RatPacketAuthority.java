package com.github.alexthe666.rats.server.message;

import com.github.alexthe666.rats.registry.RatsCapabilityRegistry;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.inventory.*;
import com.github.alexthe666.rats.server.items.RatStaffItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

/** Server authority shared by packet handlers; menus and staff have different reach. */
public final class RatPacketAuthority {
	private RatPacketAuthority() {}
	public static TamedRat menuRat(Player player, int id) {
		if (player == null || !(player.containerMenu instanceof RatMenu menu) || !menu.stillValid(player)) return null;
		var rat = menu.getRat();
		return rat != null && rat.getId() == id && rat.isOwnedBy(player) ? rat : null;
	}
	public static TamedRat staffRat(Player player, int id, int kind) {
		if (player == null || !player.isAlive() || !(player.level().getEntity(id) instanceof TamedRat rat) || !rat.isAlive() || !rat.isOwnedBy(player)) return null;
		boolean holding = java.util.stream.Stream.of(player.getMainHandItem(), player.getOffhandItem())
			.anyMatch(stack -> stack.getItem() instanceof RatStaffItem staff && staff.getStaff(stack) == kind);
		var selected = player.getCapability(RatsCapabilityRegistry.SELECTED_RAT).resolve().map(cap -> cap.getSelectedRat()).orElse(null);
		return holding && selected == rat ? rat : null;
	}
	public static RatCraftingTableMenu table(Player player, long position) {
		var pos = BlockPos.of(position);
		return player != null && player.level().hasChunkAt(pos) && player.containerMenu instanceof RatCraftingTableMenu menu
			&& menu.getCraftingTable().getBlockPos().equals(pos) && menu.stillValid(player) ? menu : null;
	}
}
