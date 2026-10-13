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
	private final SimpleContainer inspection = new SimpleContainer(13) {
		@Override public ItemStack getItem(int index) {
			if (RatCageMenu.this.viewer.level().isClientSide()) return super.getItem(index);
			TamedRat rat = RatCageMenu.this.selectedRat();
			return rat == null ? ItemStack.EMPTY : index < 10 ? rat.cargo.get(index) : rat.getInventory().getItem(index - 7);
		}
		@Override public void setItem(int index, ItemStack stack) {
			if (RatCageMenu.this.viewer.level().isClientSide()) { super.setItem(index, stack); return; }
			TamedRat rat = RatCageMenu.this.selectedRat();
			if (rat == null || !owns(RatCageMenu.this.viewer, rat)) return;
			if (index < 10) rat.cargo.set(index, stack);
			else rat.getInventory().setItem(index - 7, stack);
			this.setChanged();
		}
		@Override public ItemStack removeItem(int index, int count) {
			ItemStack old = this.getItem(index).copy(), removed = old.split(count);
			this.setItem(index, old); return removed;
		}
		@Override public ItemStack removeItemNoUpdate(int index) { ItemStack old = this.getItem(index); this.setItem(index, ItemStack.EMPTY); return old; }
		@Override public void setChanged() { RatCageMenu.this.refreshAt = 0; super.setChanged(); }
	};
	private int selected = -1, refreshAt;
	private int nextNetworkAction;
	public boolean acceptNetworkAction() {
		if (this.viewer.tickCount < this.nextNetworkAction) return false;
		this.nextNetworkAction = this.viewer.tickCount + 2;
		return true;
	}
	public CageRosterPacket snapshot;
	public boolean showPlayerInventory;

	public RatCageMenu(int id, Inventory inventory, FriendlyByteBuf buffer) { this(id, inventory, buffer.readBlockPos()); }
	public RatCageMenu(int id, Inventory inventory, BlockPos pos) {
		super(RatsMenuRegistry.RAT_CAGE_CONTAINER.get(), id);
		this.position = pos.immutable(); this.viewer = inventory.player;
		for (int i = 0; i < 13; i++) {
			final int index = i;
			this.addSlot(new Slot(this.inspection, i, 162 + (i < 10 ? i % 5 : i - 10) * 18, i < 10 ? 110 + i / 5 * 18 : 146) {
				@Override public boolean isActive() { return RatCageMenu.this.cargoSlots() > 0 && (index >= 10 || index < RatCageMenu.this.cargoSlots()); }
				@Override public int getMaxStackSize() { return index >= 10 ? 1 : RatCageMenu.this.cargoLimit(); }
				@Override public int getMaxStackSize(ItemStack stack) { return Math.min(stack.getMaxStackSize(), this.getMaxStackSize()); }
				@Override public boolean mayPlace(ItemStack stack) {
					TamedRat rat = RatCageMenu.this.selectedRat();
					if (!RatCageMenu.this.canEditSelected() || !this.isActive()) return false;
					if (index < 10) return rat == null || rat.canRatPickupItem(stack);
					if (!(stack.getItem() instanceof com.github.alexthe666.rats.server.items.upgrades.BaseRatUpgradeItem) || !RatCageMenu.this.canChangeUpgrade(index - 10, stack)) return false;
					for (int other = 10; other < 13; other++) if (other != index && com.github.alexthe666.rats.registry.RatsUpgradeConflictRegistry.doesConflict(stack, RatCageMenu.this.inspection.getItem(other))) return false;
					return true;
				}
				@Override public boolean mayPickup(Player player) { return player == RatCageMenu.this.viewer && RatCageMenu.this.canEditSelected() && this.isActive() && (index < 10 || RatCageMenu.this.canChangeUpgrade(index - 10, ItemStack.EMPTY)); }
			});
		}
		// Inventory tab replaces the roster, keeping this screen usable at GUI
		// scale 3 on a 720p window. All 36 player slots remain available.
		for (int i = 0; i < 36; i++) {
			int index = i < 27 ? i + 9 : i - 27;
			this.addSlot(new Slot(inventory,index,74+(i<27?i%9:i-27)*18,i<27?164+i/9*18:222));
		}
	}
	private TamedRat selectedRat() {
		if (this.viewer.level().isClientSide() || this.selected < 0 || !this.stillValid(this.viewer)) return null;
		var entity = this.viewer.level().getEntity(this.selected);
		return entity instanceof TamedRat rat && owns(this.viewer,rat) && rat.isAlive() && !rat.isPassenger() && CageHabitat.discover(this.viewer.level(), this.position).nodes().contains(rat.blockPosition()) ? rat : null;
	}
	private static boolean owns(Player player, TamedRat rat) { return rat.isTame() && player.getUUID().equals(rat.getOwnerUUID()); }
	private boolean canEditSelected() {
		if (this.viewer.level().isClientSide()) return this.snapshot != null && this.snapshot.residents().stream().anyMatch(rat -> rat.id() == this.snapshot.selected() && rat.owned());
		TamedRat rat = this.selectedRat(); return rat != null && owns(this.viewer, rat);
	}
	public int cargoSlots() { TamedRat rat = this.selectedRat(); return this.viewer.level().isClientSide() ? this.snapshot == null ? 0 : this.snapshot.cargoSlots() : rat == null ? 0 : rat.cargo.slots(); }
	public int cargoLimit() { TamedRat rat = this.selectedRat(); return this.viewer.level().isClientSide() ? this.snapshot == null ? 1 : this.snapshot.cargoLimit() : rat == null ? 1 : rat.cargo.limit(new ItemStack(net.minecraft.world.item.Items.COBBLESTONE)); }
	private boolean canChangeUpgrade(int index, ItemStack replacement) {
		if (!this.canEditSelected()) return false;
		boolean[] capacity = {false, false};
		for (int slot = 0; slot < 3; slot++) {
			ItemStack upgrade = slot == index ? replacement : this.inspection.getItem(slot + 10);
			capacity[0] |= com.github.alexthe666.rats.server.misc.RatUpgradeUtils.containsUpgrade(upgrade, com.github.alexthe666.rats.registry.RatsItemRegistry.RAT_UPGRADE_SILVER_TRAY.get());
			capacity[1] |= com.github.alexthe666.rats.server.misc.RatUpgradeUtils.containsUpgrade(upgrade, com.github.alexthe666.rats.registry.RatsItemRegistry.RAT_UPGRADE_PLATTER.get());
			capacity[1] |= com.github.alexthe666.rats.server.misc.RatUpgradeUtils.containsUpgrade(upgrade, com.github.alexthe666.rats.registry.RatsItemRegistry.RAT_UPGRADE_STORAGE_KEEPER.get());
		}
		for (int i = 0; i < 10; i++) { ItemStack stack = this.inspection.getItem(i); if (!stack.isEmpty() && ((!capacity[0] && i > 0) || (!capacity[0] && !capacity[1] && stack.getCount() > 1))) return false; }
		return true;
	}
	@Override public boolean stillValid(Player player) { return player.level() == this.viewer.level() && player.level().hasChunkAt(this.position) && player.level().getBlockState(this.position).getBlock() instanceof RatCageBlock && player.distanceToSqr(this.position.getX() + 0.5, this.position.getY() + 0.5, this.position.getZ() + 0.5) <= 64; }
	@Override public ItemStack quickMoveStack(Player player, int index) {
		if (player != this.viewer || !this.stillValid(player) || index < 0 || index >= this.slots.size()) return ItemStack.EMPTY;
		Slot slot = this.slots.get(index); if (!slot.isActive() || !slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
		ItemStack held = slot.getItem(), before = held.copy();
		if (index < 13) { if (!this.moveItemStackTo(held, 13, 49, true)) return ItemStack.EMPTY; }
		else if (held.getItem() instanceof com.github.alexthe666.rats.server.items.upgrades.BaseRatUpgradeItem) { if (!this.moveToRat(held, 10, 13)) return ItemStack.EMPTY; }
		else if (!this.moveToRat(held, 0, this.cargoSlots())) return ItemStack.EMPTY;
		if (held.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
		slot.onTake(player, held); return before;
	}
	private boolean moveToRat(ItemStack stack, int start, int end) {
		int count = stack.getCount();
		for (int pass = 0; pass < 2; pass++) for (int i = start; i < end && !stack.isEmpty(); i++) {
			Slot target = this.slots.get(i); ItemStack old = target.getItem();
			if (!target.isActive() || !target.mayPlace(stack) || (pass == 0 ? old.isEmpty() || !ItemStack.isSameItemSameTags(old, stack) : !old.isEmpty())) continue;
			int amount = Math.min(stack.getCount(), target.getMaxStackSize(stack) - old.getCount());
			if (amount > 0) { ItemStack merged = stack.copyWithCount(old.getCount() + amount); target.set(merged); stack.shrink(amount); }
		}
		return count != stack.getCount();
	}

	public void action(Player player, int action, int ratId, int partnerId, String name) {
		if (player != this.viewer || !this.stillValid(player) || action < 0 || action > 6) return;
		CageHabitat habitat = CageHabitat.discover(player.level(), this.position);
		if (action == 6) {
			habitat.lantern(player.level()).ifPresent(lantern -> lantern.toggleAutoBreed(player, habitat));
			this.refreshAt = 0; this.broadcastChanges(); return;
		}
		List<TamedRat> residents = habitat.rats(player.level()).stream().filter(entity->owns(player,entity)).toList();
		TamedRat rat = residents.stream().filter(entity -> entity.getId() == ratId).findFirst().orElse(null);
		if (rat == null) return;
		if (action == 0) { if (!this.getCarried().isEmpty()) return; this.selected = ratId; this.refreshAt = 0; this.broadcastChanges(); return; }
		if (!rat.isTame() || !player.getUUID().equals(rat.getOwnerUUID())) return;
		boolean success = switch (action) {
			case 1 -> {
				boolean released = habitat.release(rat, this.position);
				if (released) rat.cageReturnAfter = rat.tickCount + 1200;
				yield released;
			}
			case 2 -> { rat.setCageHome(GlobalPos.of(player.level().dimension(), this.position)); yield true; }
			case 3 -> { rat.setCageHome(null); yield true; }
			case 4 -> {
				TamedRat partner = residents.stream().filter(entity -> entity.getId() == partnerId && entity.isTame() && player.getUUID().equals(entity.getOwnerUUID())).findFirst().orElse(null);
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
			List<TamedRat> residents = habitat.rats(server.level()).stream().filter(entity->owns(server,entity)).toList();
			TamedRat rat = residents.stream().filter(entity -> entity.getId() == this.selected).findFirst().orElse(null);
			if (rat == null) this.selected = -1;
			var lantern = habitat.lantern(server.level()).orElse(null);
			boolean canAuto = lantern != null && lantern.canControlAutoBreed(server) && (lantern.isAutoBreeding() || residents.stream().anyMatch(entity -> owns(server, entity)));
			this.snapshot = new CageRosterPacket(this.containerId, this.selected, habitat.capacity(), habitat.complete(),
					lantern == null ? -1 : lantern.getBreedingCooldown(), rat == null ? 0 : rat.cargo.slots(),
					rat == null ? 1 : rat.cargo.limit(new ItemStack(net.minecraft.world.item.Items.COBBLESTONE)),
					lantern != null && lantern.isAutoBreeding(), canAuto,
					residents.stream().map(entity -> new CageRosterPacket.Resident(entity.getId(), entity.getDisplayName(), entity.isMale(), entity.isBaby(), owns(server, entity), entity.breedCooldown, entity.getCageHome().isPresent(), com.github.alexthe666.rats.server.misc.RatVariant.getVariantId(entity.getColorVariant()))).toList());
			RatsNetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> server), this.snapshot);
		}
		super.broadcastChanges();
	}
}
