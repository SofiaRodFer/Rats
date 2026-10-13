package com.github.alexthe666.rats.server.block.entity;

import com.github.alexthe666.rats.data.tags.RatsBlockTags;
import com.github.alexthe666.rats.registry.RatsBlockEntityRegistry;
import com.github.alexthe666.rats.registry.RatsBlockRegistry;
import com.github.alexthe666.rats.server.misc.RatsLangConstants;
import com.github.alexthe666.rats.server.misc.QuarryGeometry;
import com.github.alexthe666.rats.server.misc.QuarrySafety;
import com.github.alexthe666.rats.server.block.RatQuarryBlock;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.misc.RatUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ContainerData;
import com.github.alexthe666.rats.server.inventory.RatQuarryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.IntStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public class RatQuarryBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
	private static final int[] STACKS = IntStream.range(0, 64).toArray();
	private final LazyOptional<? extends IItemHandler>[] handlers = SidedInvWrapper.create(this, Direction.UP, Direction.DOWN);
	private NonNullList<ItemStack> inventory = NonNullList.withSize(64, ItemStack.EMPTY);
	private int tick;
	public static final Direction[] DIRECTIONS = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
	private int directionIndex;
	private int length = 256;
	private boolean paused;
	private final int[] progress = new int[5];
	private final int[] builtExtent = new int[5];
	private int auditDirection, auditDepth;
	private int oreAuditDirection, oreAuditDepth;
	private long nextOreAudit;
	private com.github.alexthe666.rats.server.misc.QuarryAudit.Task repairTask;
	private UUID repairWorker;
	private long repairExpires;
	private final Map<com.github.alexthe666.rats.server.misc.QuarryAudit.Task, Long> repairBackoff = new HashMap<>();
	private final Map<BlockPos, Reservation> reservations = new HashMap<>();
	private final Map<BlockPos, Reservation> veinClaims = new HashMap<>();
	private final java.util.List<UUID> workers = new java.util.ArrayList<>();
	private final Map<UUID, CompoundTag> workerDetails = new HashMap<>();
	private int status;
	private final ContainerData menuData = new ContainerData() {
		public int get(int index) {
			return switch (index) {
				case 0 -> getDirectionIndex(); case 1 -> getLength(); case 2 -> getProgress();
				case 3 -> getStatus(); case 4 -> getWidth(); case 5 -> getDepthLimit(); default -> 0;
			};
		}
		public void set(int index, int value) { }
		public int getCount() { return 6; }
	};
	private record Reservation(UUID rat, long expires) { }

	public RatQuarryBlockEntity(BlockPos pos, BlockState state) {
		super(RatsBlockEntityRegistry.RAT_QUARRY.get(), pos, state);
		this.length = this.getMaxLength();
	}

	@Override
	public int getContainerSize() {
		return this.inventory.size();
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack itemstack : this.inventory) {
			if (!itemstack.isEmpty()) {
				return false;
			}
		}

		return true;
	}

	@Override
	public ItemStack getItem(int index) {
		return this.inventory.get(index);
	}

	@Override
	public ItemStack removeItem(int index, int count) {
		return ContainerHelper.removeItem(this.inventory, index, count);
	}

	@Override
	public ItemStack removeItemNoUpdate(int index) {
		return ContainerHelper.takeItem(this.inventory, index);
	}

	@Override
	public int[] getSlotsForFace(Direction direction) {
		return STACKS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
		return this.canPlaceItem(index, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
		return true;
	}

	public void setItem(int index, ItemStack stack) {
		this.inventory.set(index, stack);

		if (stack.getCount() > this.getMaxStackSize()) {
			stack.setCount(this.getMaxStackSize());
		}
		this.setChanged();
	}

	public boolean stillValid(Player player) {
		if (player.level().getBlockEntity(this.getBlockPos()) != this) {
			return false;
		} else {
			return player.distanceToSqr((double) this.getBlockPos().getX() + 0.5D, (double) this.getBlockPos().getY() + 0.5D, (double) this.getBlockPos().getZ() + 0.5D) <= 64.0D;
		}
	}

	@Override
	public void clearContent() {
		this.inventory.clear();
	}


	public static void tick(Level level, BlockPos pos, BlockState state, RatQuarryBlockEntity te) {
		if (!level.isClientSide() && ++te.tick % 20 == 0) { te.reservations.entrySet().removeIf(entry -> entry.getValue().expires() < level.getGameTime()); te.veinClaims.entrySet().removeIf(entry -> entry.getValue().expires() < level.getGameTime()); if (!te.isPaused() && !te.loadedWorkers().isEmpty()) te.scanMaintenance(); }
	}

	public void load(CompoundTag compound) {
		super.load(compound);
		this.inventory = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(compound, this.inventory);
		this.directionIndex = Math.max(0, Math.min(4, compound.getInt("QuarryDirection")));
		this.length = compound.contains("QuarryLength") ? Math.max(1, Math.min(this.getMaxLength(), compound.getInt("QuarryLength"))) : this.getMaxLength();
		this.paused = compound.getBoolean("QuarryPaused");
		int[] savedProgress = compound.getIntArray("QuarryProgress");
		for (int i = 0; i < this.progress.length; i++) this.progress[i] = i < savedProgress.length ? Math.max(0, savedProgress[i]) : 0;
		int[] savedExtent = compound.getIntArray("QuarryBuiltExtent");
		for (int i = 0; i < this.builtExtent.length; i++) this.builtExtent[i] = Math.min(512, Math.max(this.progress[i], i < savedExtent.length ? savedExtent[i] : 0));
		this.auditDirection = Math.floorMod(compound.getInt("QuarryAuditDirection"), 5); this.auditDepth = Math.max(0, Math.min(512, compound.getInt("QuarryAuditDepth")));
		this.repairTask = null; this.repairWorker = null; this.repairBackoff.clear();
		this.reservations.clear();
		this.veinClaims.clear();
		this.workers.clear();
		this.workerDetails.clear();
		for (net.minecraft.nbt.Tag entry : compound.getList("QuarryWorkers", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
			CompoundTag worker = (CompoundTag) entry;
			if (worker.hasUUID("Id") && this.workers.size() < this.getWorkerLimit() && !this.workers.contains(worker.getUUID("Id"))) { this.workers.add(worker.getUUID("Id")); this.workerDetails.put(worker.getUUID("Id"), worker.copy()); }
		}
		// Revisit old narrow tunnels to widen them, preserving inventory and config.
		if (compound.getInt("QuarryWidth") != this.getWidth()) java.util.Arrays.fill(this.progress, 0);
	}

	public void saveAdditional(CompoundTag compound) {
		super.saveAdditional(compound);
		ContainerHelper.saveAllItems(compound, this.inventory);
		compound.putInt("QuarryDirection", this.directionIndex);
		compound.putInt("QuarryLength", this.length);
		compound.putBoolean("QuarryPaused", this.paused);
		compound.putIntArray("QuarryProgress", this.progress);
		compound.putIntArray("QuarryBuiltExtent", this.builtExtent);
		compound.putInt("QuarryAuditDirection", this.auditDirection); compound.putInt("QuarryAuditDepth", this.auditDepth);
		compound.putInt("QuarryWidth", this.getWidth());
		var savedWorkers = new net.minecraft.nbt.ListTag();
		for (UUID id : this.workers) { CompoundTag worker = this.workerDetails.getOrDefault(id, new CompoundTag()).copy(); worker.putUUID("Id", id); savedWorkers.add(worker); }
		compound.put("QuarryWorkers", savedWorkers);
	}

	@Override
	protected Component getDefaultName() {
		return this.getBlockState().getBlock().getName();
	}

	@Override
	protected AbstractContainerMenu createMenu(int id, Inventory player) {
		return new RatQuarryMenu(id, player, this, this.menuData);
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
		return this.createMenu(id, playerInventory);
	}

	@Override
	public boolean hasCustomName() {
		return false;
	}

	@NotNull
	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction facing) {
		if (!this.remove && facing != null && capability == ForgeCapabilities.ITEM_HANDLER) {
			return handlers[0].cast();
		}
		return super.getCapability(capability, facing);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this, BlockEntity::getUpdateTag);
	}

	@Override
	public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
		this.handleUpdateTag(packet.getTag());
	}

	public CompoundTag getUpdateTag() {
		return this.saveWithId();
	}

	public int getRadius() {
		return this.getWidth() / 2;
	}

	public BlockPos getNextPosForStairs(Level level) {
		return QuarryGeometry.stair(this.getBlockPos(), this.getWidth(), this.progress[0]);
	}

	public int getWidth() { return this.getBlockState().getBlock() instanceof RatQuarryBlock block && block.isAdvanced() ? 7 : 5; }
	public int getMaxLength() { return this.getWidth() == 7 ? 512 : 256; }
	public int getWorkerLimit() { return this.getWidth() == 7 ? 4 : 2; }
	public java.util.List<UUID> workerIds() { this.pruneWorkers(); return java.util.List.copyOf(this.workers); }
	private void pruneWorkers() {
		if (!(this.level instanceof net.minecraft.server.level.ServerLevel server)) return;
		boolean changed = this.workers.removeIf(id -> {
			if (com.github.alexthe666.rats.server.misc.WorkstationLedger.get(server).take(this.worldPosition,id)) return true;
			var entity = server.getEntity(id);
			var info = this.workerDetails.getOrDefault(id, new CompoundTag());
			// Older real assignments came from Quarry AI and have no marker.
			boolean wasMiner = !info.contains("HasQuarryUpgrade") || info.getBoolean("HasQuarryUpgrade");
			return entity != null && (!(entity instanceof TamedRat rat) || !rat.isAlive() || rat.getDepositPos().filter(pos -> pos.dimension().equals(server.dimension()) && pos.pos().equals(this.worldPosition)).isEmpty()
					|| (wasMiner && !com.github.alexthe666.rats.server.misc.RatUpgradeUtils.hasUpgrade(rat, com.github.alexthe666.rats.registry.RatsItemRegistry.RAT_UPGRADE_QUARRY.get())));
		});
		if (changed) { this.workerDetails.keySet().retainAll(this.workers); this.setChanged(); }
	}
	public boolean assignWorker(TamedRat rat) {
		this.pruneWorkers();
		if (this.workers.contains(rat.getUUID())) { this.describeWorker(rat); return true; }
		if (this.workers.size() >= this.getWorkerLimit()) return false;
		this.workers.add(rat.getUUID()); this.describeWorker(rat); this.setChanged(); return true;
	}
	private void describeWorker(TamedRat rat) {
		CompoundTag info = new CompoundTag(); info.putString("Name", rat.getDisplayName().getString()); info.putString("Variant", com.github.alexthe666.rats.server.misc.RatVariant.getVariantId(rat.getColorVariant())); info.putBoolean("Male", rat.isMale());
		info.putBoolean("HasQuarryUpgrade", com.github.alexthe666.rats.server.misc.RatUpgradeUtils.hasUpgrade(rat, com.github.alexthe666.rats.registry.RatsItemRegistry.RAT_UPGRADE_QUARRY.get()));
		if (rat.getOwnerUUID() != null) info.putUUID("Owner", rat.getOwnerUUID());
		if (!info.equals(this.workerDetails.put(rat.getUUID(), info))) this.setChanged();
	}
	public java.util.List<com.github.alexthe666.rats.server.message.QuarryRosterPacket.Worker> workerDescriptions(Player viewer) {
		var loaded = this.loadedWorkers(); loaded.forEach(this::describeWorker);
		return this.workers.stream().map(id -> {
			TamedRat rat = loaded.stream().filter(entity -> entity.getUUID().equals(id)).findFirst().orElse(null);
			CompoundTag info = this.workerDetails.getOrDefault(id, new CompoundTag());
			return new com.github.alexthe666.rats.server.message.QuarryRosterPacket.Worker(Component.literal(info.contains("Name") ? info.getString("Name") : "Rat"), info.getBoolean("Male"), info.contains("Variant") ? info.getString("Variant") : "rats:blue", rat != null && rat.quarryReturning, info.hasUUID("Owner") && viewer.getUUID().equals(info.getUUID("Owner")), rat != null);
		}).toList();
	}
	public void unassignWorker(UUID id) { if (this.workers.remove(id)) { this.workerDetails.remove(id); this.releaseTarget(id); this.setChanged(); } }
	public java.util.List<TamedRat> loadedWorkers() {
		this.pruneWorkers();
		if (!(this.level instanceof net.minecraft.server.level.ServerLevel server)) return java.util.List.of();
		return this.workers.stream().map(server::getEntity).filter(TamedRat.class::isInstance).map(TamedRat.class::cast).toList();
	}
	public void recallWorkers(Player player) {
		for (TamedRat rat : this.loadedWorkers()) if (player.getUUID().equals(rat.getOwnerUUID()) && !rat.cargo.isEmpty()) { rat.quarryReturning = true; this.releaseTarget(rat.getUUID()); rat.getNavigation().stop(); }
	}
	public boolean ownsColumn(TamedRat rat, int column) {
		var active = this.loadedWorkers().stream().filter(entity -> entity.getCommand() == com.github.alexthe666.rats.server.entity.rat.RatCommand.HARVEST && !entity.isInCage() && !entity.isInTube()).toList();
		int index = active.indexOf(rat);
		return index >= 0 && column * active.size() / this.getWidth() == index;
	}
	public int getDirectionIndex() { return this.directionIndex; }
	public Direction getDigDirection() { return DIRECTIONS[this.directionIndex]; }
	public int getLength() { return this.length; }
	public int getProgress() { return this.progress[this.directionIndex]; }
	public boolean isPaused() { return this.paused; }
	public int getStatus() { return this.paused ? 1 : this.status; }
	public void markBlocked() { this.status = 4; }

	public int getDepthLimit() {
		if (this.getDigDirection() != Direction.DOWN) return this.length;
		int worldDepth = this.getBlockPos().getY() - (this.level == null ? -64 : this.level.getMinBuildHeight());
		return worldDepth;
	}

	public void configure(int button) {
		if (button >= 0 && button < 5) this.directionIndex = button;
		else if (button == 5) this.length = Math.max(16, this.length - 16);
		else if (button == 6) this.length = Math.min(this.getMaxLength(), this.length + 16);
		else if (button == 7) this.paused = !this.paused;
		else return;
		this.reservations.clear();
		this.status = 0;
		this.setChanged();
		if (this.level != null) this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
	}

	public BlockPos cell(int depth, int column, int row) {
		return QuarryGeometry.cell(this.worldPosition, this.getDigDirection(), this.getWidth(), depth, column, row);
	}

	/** Inspect at most eight empty slices per call. Never load chunks to find work. */
	@Nullable
	public BlockPos reserveTarget(TamedRat rat, Predicate<BlockPos> eligible) {
		if (this.level == null || this.paused) return null;
		if (!this.assignWorker(rat)) return null;
		this.releaseTarget(rat.getUUID());
		// The crew cannot change during this server-thread scan. Resolve it once,
		// not once per candidate block (up to 8 * 49 full entity lookups).
		var active = this.loadedWorkers().stream().filter(entity -> entity.getCommand() == com.github.alexthe666.rats.server.entity.rat.RatCommand.HARVEST && !entity.isInCage() && !entity.isInTube()).toList();
		int workerIndex = active.indexOf(rat);
		boolean blocked = false;
		for (int depth = this.getProgress(), slices = 0; slices < 8 && depth < this.getDepthLimit(); slices++, depth++) {
			// Empty caves still need a floor and fluid containment before advancing.
			if (!QuarrySafety.prepareSlice(this, rat, depth)) { this.status = 4; return null; }
			BlockPos best = null;
			BlockPos borrowed = null;
			boolean remaining = false;
			for (int row = 0; row < this.getWidth(); row++) for (int column = 0; column < this.getWidth(); column++) {
				BlockPos pos = this.cell(depth, column, row);
				if (!this.level.hasChunkAt(pos) || this.level.isOutsideBuildHeight(pos)) { this.status = 3; return null; }
				BlockState state = this.level.getBlockState(pos);
				if (state.isAir() || state.is(RatsBlockTags.QUARRY_IGNORABLES)) continue;
				remaining = true;
				if (!this.canClaimOre(pos, rat.getUUID())) continue;
				Reservation reserved = this.reservations.get(pos);
				if (reserved != null && reserved.expires() >= this.level.getGameTime()) continue;
				boolean eligibleTarget = eligible.test(pos);
				if (!eligibleTarget) blocked = true;
				if (workerIndex >= 0 && column * active.size() / this.getWidth() == workerIndex && eligibleTarget && (best == null || rat.distanceToSqr(pos.getCenter()) < rat.distanceToSqr(best.getCenter()))) best = pos;
				if (eligibleTarget && (borrowed == null || rat.distanceToSqr(pos.getCenter()) < rat.distanceToSqr(borrowed.getCenter()))) borrowed = pos;
			}
			if (best == null) best = borrowed; // Work stealing, never steal a live lease.
			if (best != null) {
				this.reservations.put(best, new Reservation(rat.getUUID(), this.level.getGameTime() + 100));
				this.status = 0;
				return best;
			}
			if (!remaining && depth == this.getProgress()) { this.progress[this.directionIndex]++; this.builtExtent[this.directionIndex] = Math.max(this.builtExtent[this.directionIndex], this.getProgress()); this.setChanged(); }
		}
		this.status = this.getProgress() >= this.getDepthLimit() ? 2 : blocked ? 4 : 5;
		return null;
	}
	public int depthOf(BlockPos pos) {
		if (this.getDigDirection() == Direction.DOWN) return Math.max(0, this.worldPosition.getY() - pos.getY() - 1);
		return Math.max(0, (pos.getX() - this.worldPosition.getX()) * this.getDigDirection().getStepX() + (pos.getZ() - this.worldPosition.getZ()) * this.getDigDirection().getStepZ() - 1);
	}
	public int historicalExtent(Direction direction) { for (int i = 0; i < 5; i++) if (DIRECTIONS[i] == direction) return Math.max(this.progress[i], this.builtExtent[i]); return 0; }
	/** One shared historical slice per second; no full-shaft scans per worker. */
	public void discoverHistoricalOre(TamedRat rat) {
		if (this.level == null || this.level.getGameTime() < this.nextOreAudit) return;
		this.nextOreAudit = this.level.getGameTime() + 20;
		for (int attempts = 0; attempts < 6; attempts++) {
			Direction direction = DIRECTIONS[this.oreAuditDirection];
			if (this.oreAuditDepth >= this.historicalExtent(direction)) {
				this.oreAuditDepth = 0; this.oreAuditDirection = (this.oreAuditDirection + 1) % 5; continue;
			}
			int depth = this.oreAuditDepth++;
			for (int col = 0; col < this.getWidth(); col++) for (int row = 0; row < this.getWidth(); row++) {
				BlockPos exposed = QuarryGeometry.cell(this.worldPosition, direction, this.getWidth(), depth, col, row);
				if (!this.level.hasChunkAt(exposed) || !this.level.getBlockState(exposed).isAir()) continue;
				if (Direction.stream().map(exposed::relative).anyMatch(p -> this.level.hasChunkAt(p) && this.level.getBlockState(p).is(net.minecraftforge.common.Tags.Blocks.ORES)))
					rat.oreVein.discover(this.level, this.worldPosition, exposed);
			}
			this.claimVein(rat);
			return;
		}
	}
	public void scanMaintenance() {
		if (this.level == null || this.paused) return;
		this.repairBackoff.entrySet().removeIf(entry -> entry.getValue() <= this.level.getGameTime());
		if (this.repairTask != null) return;
		for (int inspected = 0, attempts = 0; inspected < 4 && attempts < 9; attempts++) {
			Direction direction = DIRECTIONS[this.auditDirection];
			if (this.auditDepth >= this.historicalExtent(direction)) { this.auditDepth = 0; this.auditDirection = (this.auditDirection + 1) % 5; continue; }
			var task = com.github.alexthe666.rats.server.misc.QuarryAudit.inspect(this, direction, this.auditDepth++); inspected++;
			if (task != null && !this.repairBackoff.containsKey(task)) { this.repairTask = task; this.setChanged(); return; }
		}
		this.setChanged();
	}
	public com.github.alexthe666.rats.server.misc.QuarryAudit.Task claimMaintenance(TamedRat rat) {
		if (this.level == null || this.paused || this.repairTask == null || !this.assignWorker(rat)) return null;
		if (this.repairWorker != null && this.repairExpires >= this.level.getGameTime() && !this.repairWorker.equals(rat.getUUID())) return null;
		this.repairWorker = rat.getUUID(); this.repairExpires = this.level.getGameTime() + 100; return this.repairTask;
	}
	public boolean ownsMaintenance(TamedRat rat) { return this.level != null && !this.paused && this.repairTask != null && rat.getUUID().equals(this.repairWorker) && this.repairExpires >= this.level.getGameTime(); }
	public void renewMaintenance(TamedRat rat) { if (this.ownsMaintenance(rat)) this.repairExpires = this.level.getGameTime() + 100; }
	public void finishMaintenance(TamedRat rat, boolean successful) {
		if (this.repairTask == null || !rat.getUUID().equals(this.repairWorker)) return;
		if (!successful && this.level != null) this.repairBackoff.put(this.repairTask, this.level.getGameTime() + 600);
		this.repairTask = null; this.repairWorker = null;
	}

	public boolean ownsTarget(BlockPos pos, UUID rat) {
		Reservation reservation = this.reservations.get(pos);
		return !this.paused && reservation != null && reservation.rat().equals(rat) && this.level != null && reservation.expires() >= this.level.getGameTime();
	}
	public boolean reserveOre(BlockPos pos, TamedRat rat) {
		if (this.level == null || this.paused || !this.level.hasChunkAt(pos) || !this.assignWorker(rat) || !this.canClaimOre(pos, rat.getUUID())) return false;
		Reservation reservation = this.reservations.get(pos);
		if (reservation != null && reservation.expires() >= this.level.getGameTime() && !reservation.rat().equals(rat.getUUID())) return false;
		this.releaseTarget(rat.getUUID()); this.reservations.put(pos.immutable(), new Reservation(rat.getUUID(), this.level.getGameTime() + 100)); return true;
	}
	public boolean canClaimOre(BlockPos pos, UUID rat) {
		var claim = this.veinClaims.get(pos);
		return this.level != null && (claim == null || claim.expires() < this.level.getGameTime() || claim.rat().equals(rat));
	}
	public void claimVein(TamedRat rat) {
		if (this.level == null) return;
		for (BlockPos pos : rat.oreVein.targets(this.worldPosition)) {
			if (!this.canClaimOre(pos, rat.getUUID())) { rat.oreVein.remove(pos); continue; }
			var lease = this.reservations.get(pos);
			if (lease != null && lease.expires() >= this.level.getGameTime() && !lease.rat().equals(rat.getUUID())) { rat.oreVein.remove(pos); continue; }
			this.veinClaims.put(pos, new Reservation(rat.getUUID(), this.level.getGameTime() + 200));
		}
	}
	public void completeOre(BlockPos pos) { this.veinClaims.remove(pos); }

	public void renewTarget(BlockPos pos, UUID rat) {
		if (this.level != null && this.ownsTarget(pos, rat)) this.reservations.put(pos, new Reservation(rat, this.level.getGameTime() + 100));
	}

	public void releaseTarget(UUID rat) {
		this.reservations.entrySet().removeIf(entry -> entry.getValue().rat().equals(rat));
	}
}
