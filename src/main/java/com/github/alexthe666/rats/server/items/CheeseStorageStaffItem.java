package com.github.alexthe666.rats.server.items;
import com.github.alexthe666.rats.server.block.RatStorageBlock;
import com.github.alexthe666.rats.server.block.entity.RatStorageBlockEntity;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.storage.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;

public class CheeseStorageStaffItem extends Item {
	public CheeseStorageStaffItem(Properties properties) { super(properties); }
	private RatStorageBlockEntity selected(ItemStack stack, Player player) {
		var tag = stack.getTag(); if (tag == null || !tag.hasUUID("Network") || !tag.getString("Dimension").equals(player.level().dimension().location().toString())) return null;
		BlockPos pos = BlockPos.of(tag.getLong("Controller")); if (!player.level().hasChunkAt(pos)) return null;
		return player.level().getBlockEntity(pos) instanceof RatStorageBlockEntity storage && storage.kind() == RatStorageBlock.Kind.CONTROLLER && storage.network.equals(tag.getUUID("Network")) ? storage : null;
	}
	@Override public InteractionResult useOn(UseOnContext context) {
		if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
		Player player = context.getPlayer(); if (player == null) return InteractionResult.FAIL;
		BlockPos pos = context.getClickedPos(); var selected = this.selected(context.getItemInHand(), player); boolean success = false;
		if (context.getLevel().getBlockEntity(pos) instanceof RatStorageBlockEntity storage && storage.kind() == RatStorageBlock.Kind.CONTROLLER) {
			var tag = context.getItemInHand().getOrCreateTag(); tag.putUUID("Network", storage.network); tag.putLong("Controller", pos.asLong()); tag.putString("Dimension", player.level().dimension().location().toString()); success = true;
		} else if (selected != null && (selected.owner == null || selected.owner.equals(player.getUUID()))) {
			if (player.isCrouching()) { success = selected.disconnect(pos); }
			else success = selected.connect(pos);
		}
		player.displayClientMessage(Component.translatable(success ? "storage.rats.linked" : "storage.rats.link_failed"), true); return InteractionResult.SUCCESS;
	}
	@Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
		if (!(entity instanceof TamedRat rat)) return InteractionResult.PASS;
		if (!player.level().isClientSide()) {
			var selected = this.selected(stack, player); boolean success = false;
			if (selected != null && rat.isOwnedBy(player) && StorageRoutes.within(selected.getBlockPos(), rat.blockPosition())) {
				if (player.isCrouching() && selected.workers.contains(rat.getUUID())) { selected.recalling.add(rat.getUUID()); selected.setChanged(); success = true; } else success = selected.assign(rat);
			}
			player.displayClientMessage(Component.translatable(success ? "storage.rats.linked" : "storage.rats.rat_failed"), true);
		}
		return InteractionResult.SUCCESS;
	}
}
