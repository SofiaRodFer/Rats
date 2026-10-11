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
	private final Map<BlockPos, Reservation> reservations = new HashMap<>();
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
		if (!level.isClientSide() && ++te.tick % 20 == 0) te.reservations.entrySet().removeIf(entry -> entry.getValue().expires() < level.getGameTime());
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
		this.reservations.clear();
	}

	public void saveAdditional(CompoundTag compound) {
		super.saveAdditional(compound);
		ContainerHelper.saveAllItems(compound, this.inventory);
		compound.putInt("QuarryDirection", this.directionIndex);
		compound.putInt("QuarryLength", this.length);
		compound.putBoolean("QuarryPaused", this.paused);
		compound.putIntArray("QuarryProgress", this.progress);
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

	public int getWidth() { return this.getBlockState().getBlock() instanceof RatQuarryBlock block && block.isAdvanced() ? 6 : 4; }
	public int getMaxLength() { return this.getWidth() == 6 ? 512 : 256; }
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
		return this.getWidth() == 4 ? worldDepth : Math.min(worldDepth, this.length);
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
		this.releaseTarget(rat.getUUID());
		for (int slices = 0; slices < 8 && this.getProgress() < this.getDepthLimit(); slices++) {
			// Empty caves still need a floor and fluid containment before advancing.
			if (!QuarrySafety.prepareSlice(this, rat)) { this.status = 4; return null; }
			BlockPos best = null;
			boolean remaining = false;
			boolean unreserved = false;
			for (int row = 0; row < this.getWidth(); row++) for (int column = 0; column < this.getWidth(); column++) {
				BlockPos pos = this.cell(this.getProgress(), column, row);
				if (!this.level.hasChunkAt(pos) || this.level.isOutsideBuildHeight(pos)) { this.status = 3; return null; }
				BlockState state = this.level.getBlockState(pos);
				if (state.isAir() || state.is(RatsBlockTags.QUARRY_IGNORABLES)) continue;
				remaining = true;
				Reservation reserved = this.reservations.get(pos);
				if (reserved != null && reserved.expires() >= this.level.getGameTime()) continue;
				unreserved = true;
				if (eligible.test(pos) && (best == null || rat.distanceToSqr(pos.getCenter()) < rat.distanceToSqr(best.getCenter()))) best = pos;
			}
			if (best != null) {
				this.reservations.put(best, new Reservation(rat.getUUID(), this.level.getGameTime() + 100));
				this.status = 0;
				return best;
			}
			if (remaining) { this.status = unreserved ? 4 : 5; return null; }
			this.progress[this.directionIndex]++;
			this.setChanged();
		}
		this.status = this.getProgress() >= this.getDepthLimit() ? 2 : 0;
		return null;
	}

	public boolean ownsTarget(BlockPos pos, UUID rat) {
		Reservation reservation = this.reservations.get(pos);
		return !this.paused && reservation != null && reservation.rat().equals(rat);
	}

	public void renewTarget(BlockPos pos, UUID rat) {
		if (this.level != null && this.ownsTarget(pos, rat)) this.reservations.put(pos, new Reservation(rat, this.level.getGameTime() + 100));
	}

	public void releaseTarget(UUID rat) {
		this.reservations.entrySet().removeIf(entry -> entry.getValue().rat().equals(rat));
	}
}
