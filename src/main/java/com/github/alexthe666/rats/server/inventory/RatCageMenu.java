package com.github.alexthe666.rats.server.inventory;

import com.github.alexthe666.rats.registry.RatsMenuRegistry;
import com.github.alexthe666.rats.server.block.RatCageBlock;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.message.CageRosterPacket;
import com.github.alexthe666.rats.server.message.RatsNetworkHandler;
import com.github.alexthe666.rats.server.misc.CageHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

public class RatCageMenu extends AbstractContainerMenu {
	public final BlockPos position;
	private final Player viewer;
	private final SimpleContainer inspection = new SimpleContainer(15);
	private int selected = -1, refreshAt;
	public CageRosterPacket snapshot;

	public RatCageMenu(int id, Inventory inventory, FriendlyByteBuf buffer) { this(id, inventory, buffer.readBlockPos()); }
	public RatCageMenu(int id, Inventory inventory, BlockPos pos) {
		super(RatsMenuRegistry.RAT_CAGE_CONTAINER.get(), id);
		this.position = pos.immutable(); this.viewer = inventory.player;
		for (int i = 0; i < 15; i++) {
			this.addSlot(new Slot(this.inspection, i, 168 + i % 5 * 18, 109 + i / 5 * 18) {
				@Override public boolean mayPlace(ItemStack stack) { return false; }
				@Override public boolean mayPickup(Player player) { return false; }
			});
		}
	}
	@Override public boolean stillValid(Player player) { return player.level() == this.viewer.level() && player.level().hasChunkAt(this.position) && player.level().getBlockState(this.position).getBlock() instanceof RatCageBlock && player.distanceToSqr(this.position.getX() + 0.5, this.position.getY() + 0.5, this.position.getZ() + 0.5) <= 64; }
	@Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

	public void action(Player player, int action, int ratId, int partnerId, String name) {
		if (player != this.viewer || !this.stillValid(player) || action < 0 || action > 5) return;
		CageHabitat habitat = CageHabitat.discover(player.level(), this.position);
		List<TamedRat> residents = habitat.rats(player.level());
		TamedRat rat = residents.stream().filter(entity -> entity.getId() == ratId).findFirst().orElse(null);
		if (rat == null) return;
		if (action == 0) { this.selected = ratId; this.refreshAt = 0; this.broadcastChanges(); return; }
		if (!rat.isOwnedBy(player)) return;
		boolean success = switch (action) {
			case 1 -> {
				boolean released = habitat.release(rat, this.position);
				if (released) rat.cageReturnAfter = rat.tickCount + 1200;
				yield released;
			}
			case 2 -> { rat.setCageHome(GlobalPos.of(player.level().dimension(), this.position)); yield true; }
			case 3 -> { rat.setCageHome(null); yield true; }
			case 4 -> {
				TamedRat partner = residents.stream().filter(entity -> entity.getId() == partnerId && entity.isOwnedBy(player)).findFirst().orElse(null);
				yield partner != null && habitat.lantern(player.level()).map(lantern -> lantern.breed(habitat, rat.isMale() ? partner : rat, rat.isMale() ? rat : partner)).orElse(false);
			}
			case 5 -> {
				String clean = name.strip();
				if (clean.length() > 32 || clean.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 167)) yield false;
				rat.setCustomName(clean.isEmpty() ? null : Component.literal(clean)); yield true;
			}
			default -> false;
		};
		player.displayClientMessage(Component.translatable(success ? "cage.rats.success" : "cage.rats.failed"), true);
		this.refreshAt = 0; this.broadcastChanges();
	}

	@Override public void broadcastChanges() {
		if (this.viewer instanceof ServerPlayer server && this.stillValid(server) && this.viewer.tickCount >= this.refreshAt) {
			this.refreshAt = this.viewer.tickCount + 20;
			CageHabitat habitat = CageHabitat.discover(server.level(), this.position);
			List<TamedRat> residents = habitat.rats(server.level());
			TamedRat rat = residents.stream().filter(entity -> entity.getId() == this.selected).findFirst().orElse(null);
			if (rat == null) { this.selected = -1; this.inspection.clearContent(); }
			else {
				for (int i = 0; i < 6; i++) this.inspection.setItem(i, rat.getInventory().getItem(i).copy());
				for (int i = 0; i < 9; i++) this.inspection.setItem(i + 6, rat.cargo.get(i + 1).copy());
			}
			this.snapshot = new CageRosterPacket(this.containerId, this.selected, habitat.capacity(), habitat.complete(), habitat.lantern(server.level()).map(lantern -> lantern.getBreedingCooldown()).orElse(-1), residents.stream().map(entity -> new CageRosterPacket.Resident(entity.getId(), entity.getDisplayName(), entity.isMale(), entity.isBaby(), entity.isOwnedBy(server), entity.breedCooldown, entity.getCageHome().isPresent())).toList());
			RatsNetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> server), this.snapshot);
		}
		super.broadcastChanges();
	}
}
