package com.github.alexthe666.rats.server.misc;

import com.github.alexthe666.rats.server.block.RatCageBlock;
import com.github.alexthe666.rats.server.block.RatTubeBlock;
import com.github.alexthe666.rats.server.block.entity.RatCageBreedingLanternBlockEntity;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** A fresh, bounded view: no global cache, forced chunk loads or stale topology. */
public record CageHabitat(Set<BlockPos> nodes, Set<BlockPos> cages, boolean complete) {

	public static final int MAX_BLOCKS = 1024;

	public static CageHabitat discover(Level level, BlockPos start) {
		Set<BlockPos> nodes = new LinkedHashSet<>(), cages = new LinkedHashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		boolean complete = level.hasChunkAt(start);
		if (complete && isHabitat(level, start)) { nodes.add(start.immutable()); queue.add(start.immutable()); }
		while (!queue.isEmpty()) {
			BlockPos current = queue.removeFirst();
			if (level.getBlockState(current).getBlock() instanceof RatCageBlock) cages.add(current);
			for (Direction direction : Direction.values()) {
				BlockPos next = current.relative(direction);
				if (!level.hasChunkAt(next)) { complete = false; continue; }
				if (nodes.contains(next) || !isHabitat(level, next)) continue;
				if (nodes.size() >= MAX_BLOCKS) { complete = false; continue; }
				nodes.add(next); queue.add(next);
			}
		}
		return new CageHabitat(Set.copyOf(nodes), Set.copyOf(cages), complete);
	}

	public static boolean isHabitat(Level level, BlockPos pos) {
		return level.hasChunkAt(pos) && (level.getBlockState(pos).getBlock() instanceof RatCageBlock || level.getBlockState(pos).getBlock() instanceof RatTubeBlock);
	}

	public int capacity() { int base = CageLayout.base(this); return (int) this.cages.stream().filter(pos -> (pos.getY() - base) % 2 == 0).count() * 2; }
	public static boolean hasRestSupport(Level level, BlockPos pos) {
		var state = level.getBlockState(pos);
		if (state.getBlock() instanceof RatTubeBlock) return true;
		if (state.getBlock() instanceof RatCageBlock && (state.getValue(RatCageBlock.DOWN) == 0 || state.getValue(RatCageBlock.SHELF))) return true;
		if (level.getBlockEntity(pos) instanceof com.github.alexthe666.rats.server.block.entity.DecoratedRatCageBlockEntity deco) {
			var item = deco.getContainedItem().getItem();
			return item instanceof com.github.alexthe666.rats.server.items.RatIglooItem || item instanceof com.github.alexthe666.rats.server.items.RatHammockItem;
		}
		return false;
	}

	public List<TamedRat> rats(Level level) {
		// Query per occupied block rather than one enormous bounding box for a long tube.
		Map<UUID, TamedRat> found = new HashMap<>();
		for (BlockPos pos : this.nodes) for (TamedRat rat : level.getEntitiesOfClass(TamedRat.class, new AABB(pos), rat -> rat.isAlive() && !rat.isPassenger() && this.nodes.contains(rat.blockPosition()))) found.put(rat.getUUID(), rat);
		return found.values().stream().sorted(Comparator.comparing(TamedRat::getUUID)).toList();
	}

	public boolean hasRoom(Level level) { return this.complete && !this.cages.isEmpty() && this.rats(level).size() < this.capacity(); }

	public Optional<RatCageBreedingLanternBlockEntity> lantern(Level level) {
		return this.cages.stream().sorted().map(level::getBlockEntity).filter(RatCageBreedingLanternBlockEntity.class::isInstance).map(RatCageBreedingLanternBlockEntity.class::cast).findFirst();
	}

	public boolean admit(TamedRat rat, BlockPos cage) {
		CageLayout.synchronize(rat.level(), this);
		if (!this.cages.contains(cage) || !this.hasRoom(rat.level())) return false;
		cage = this.entryCell(rat.level(), cage);
		var vehicle = rat.getVehicle();
		rat.stopRiding();
		if (rat.isPassenger()) return false; // Honor canceled Forge dismount events.
		rat.goalSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
		rat.getNavigation().stop(); rat.setTarget(null);
		rat.setInSittingPose(false);
		rat.setCageResting(false); rat.setInWheel(false); rat.setFlying(false);
		rat.setJumping(false); rat.setDeltaMovement(Vec3.ZERO); rat.fallDistance = 0;
		rat.setNoGravity(true);
		rat.switchNavigator(5);
		Vec3 rest = restPoint(rat.level(), cage);
		rat.moveTo(rest.x, rest.y, rest.z, rat.getYRot(), rat.getXRot());
		if (rat.level() instanceof net.minecraft.server.level.ServerLevel level) {
			// Explicit ordering: detach on clients BEFORE applying the cage position.
			if (vehicle != null) level.getChunkSource().broadcastAndSend(vehicle, new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(vehicle));
			// Let the vanilla tracker send/reset its position codec. A manual
			// teleport followed by its pending relative packet applies the move twice.
			rat.hasImpulse = true;
			level.getChunkSource().broadcast(rat, new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(rat));
		}
		return true;
	}
	public BlockPos entryCell(Level level, BlockPos preferred) {
		while (this.cages.contains(preferred.below()) && !hasRestSupport(level, preferred)) preferred = preferred.below();
		Map<BlockPos, Integer> occupancy = new HashMap<>();
		for (TamedRat resident : this.rats(level)) occupancy.merge(resident.blockPosition(), 1, Integer::sum);
		if (this.cages.contains(preferred) && occupancy.getOrDefault(preferred, 0) < 2) return preferred;
		return this.cages.stream().filter(pos -> hasRestSupport(level, pos)).min(Comparator.<BlockPos>comparingInt(pos -> occupancy.getOrDefault(pos, 0)).thenComparing(Comparator.naturalOrder())).orElse(preferred);
	}
	public static Vec3 restPoint(Level level, BlockPos pos) {
		var state = level.getBlockState(pos);
		boolean opening = state.getBlock() instanceof RatCageBlock && state.getValue(RatCageBlock.SHELF) && state.getValue(RatCageBlock.LADDER);
		boolean tube = state.getBlock() instanceof RatCageBlock && state.getValue(RatCageBlock.SHELF) && state.getValue(RatCageBlock.DOWN) == 2;
		return new Vec3(pos.getX() + (tube ? 0.68 : 0.5), pos.getY() + 0.125, pos.getZ() + (opening || tube ? 0.68 : 0.5));
	}
	public static boolean canTraverse(Level level, BlockPos from, BlockPos to) {
		if (from.getY() == to.getY()) return true;
		var state = level.getBlockState(from.getY() > to.getY() ? from : to);
		if (!(state.getBlock() instanceof RatCageBlock)) return true;
		if (state.getValue(RatCageBlock.DOWN) == 0) return false;
		return !state.getValue(RatCageBlock.SHELF) || state.getValue(RatCageBlock.LADDER) || state.getValue(RatCageBlock.DOWN) == 2;
	}

	public boolean release(TamedRat rat, BlockPos near) {
		if (!this.nodes.contains(rat.blockPosition())) return false;
		for (int radius = 1; radius <= 4; radius++) for (int y = -1; y <= 1; y++) for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
			if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
			BlockPos pos = near.offset(x, y, z);
			if (this.nodes.contains(pos) || !rat.level().hasChunkAt(pos) || !rat.level().getFluidState(pos).isEmpty() || !rat.level().getFluidState(pos.above()).isEmpty()) continue;
			if (!rat.level().getBlockState(pos.below()).isFaceSturdy(rat.level(), pos.below(), Direction.UP)) continue;
			Vec3 destination = Vec3.atBottomCenterOf(pos);
			AABB bounds = rat.getBoundingBox().move(destination.subtract(rat.position()));
			if (!rat.level().noCollision(rat, bounds)) continue;
			rat.getNavigation().stop(); rat.setInSittingPose(false); rat.setCageResting(false); rat.setInWheel(false);
			rat.fallDistance = 0; rat.setDeltaMovement(Vec3.ZERO); rat.moveTo(destination.x, destination.y, destination.z, rat.getYRot(), rat.getXRot());
			rat.setNoGravity(rat.isFlying()); rat.switchNavigator(0);
			return true;
		}
		return false;
	}
}
