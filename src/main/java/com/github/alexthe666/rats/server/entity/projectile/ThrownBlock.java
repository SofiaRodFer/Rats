package com.github.alexthe666.rats.server.entity.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class ThrownBlock extends Entity {
	public LivingEntity shootingEntity;
	private static final EntityDataAccessor<Optional<BlockState>> CARRIED_BLOCK = SynchedEntityData.defineId(ThrownBlock.class, EntityDataSerializers.OPTIONAL_BLOCK_STATE);
	public boolean dropBlock = true;
	public CompoundTag tileEntityData;
	private int ticksAlive;
	private int ticksInAir;

	public ThrownBlock(EntityType<? extends Entity> type, Level level) {
		super(type, level);
	}

	public ThrownBlock(EntityType<? extends Entity> type, Level level, BlockState blockState, LivingEntity entityNeoRatlantean) {
		super(type, level);
		this.setHeldBlockState(blockState);
		this.shootingEntity = entityNeoRatlantean;
	}

	protected void defineSynchedData() {
		this.getEntityData().define(CARRIED_BLOCK, Optional.empty());
	}

	public void setHeldBlockState(@Nullable BlockState state) {
		this.getEntityData().set(CARRIED_BLOCK, Optional.ofNullable(state));
	}

	@Nullable
	public BlockState getHeldBlockState() {
		return this.getEntityData().get(CARRIED_BLOCK).orElse(null);
	}

	public boolean shouldRenderAtSqrDistance(double distance) {
		double d0 = this.getBoundingBox().getSize() * 4.0D;

		if (Double.isNaN(d0)) {
			d0 = 4.0D;
		}

		d0 = d0 * 64.0D;
		return distance < d0 * d0;
	}

	@Override
	public boolean canCollideWith(Entity entity) {
		if (!entity.isSpectator() && entity.isAlive() && entity.canBeCollidedWith()) {
			Entity shooter = this.shootingEntity;
			return shooter == null || !entity.isPassengerOfSameVehicle(entity);
		} else {
			return false;
		}
	}

	public void tick() {
		if (this.level().isClientSide() || (this.shootingEntity == null || this.shootingEntity.isAlive()) && this.level().isLoaded(this.blockPosition())) {
			super.tick();

			++this.ticksInAir;
			if (ticksInAir > 25) {
				this.noPhysics = true;
				HitResult raytraceresult = ProjectileUtil.getHitResultOnMoveVector(this, this::canCollideWith);
				this.onHit(raytraceresult);
			} else {
				this.noPhysics = false;
			}
			this.setPos(this.getX() + this.getDeltaMovement().x, this.getY() + this.getDeltaMovement().y, this.getZ() + this.getDeltaMovement().z);
			ProjectileUtil.rotateTowardsMovement(this, 0.2F);
			if (this.isInWater()) {
				for (int i = 0; i < 4; ++i) {
					this.level().addParticle(ParticleTypes.BUBBLE, this.getX() - this.getDeltaMovement().x * 0.25D, this.getY() - this.getDeltaMovement().y * 0.25D, this.getZ() - this.getDeltaMovement().z * 0.25D, this.getDeltaMovement().x, this.getDeltaMovement().y, this.getDeltaMovement().z);
				}
			}
			if (this.shootingEntity != null && shootingEntity instanceof Mob mob) {
				if (mob.getTarget() != null) {
					LivingEntity target = mob.getTarget();
					double d0 = target.getX() - this.getX();
					double d1 = target.getY() - this.getY();
					double d2 = target.getZ() - this.getZ();
					double d3 = d0 * d0 + d1 * d1 + d2 * d2;
					d3 = Mth.sqrt((float) d3);
					Vec3 vec3d = this.getDeltaMovement();
					vec3d = vec3d.add(d0 / d3 * 0.2D, d1 / d3 * 0.2D, d2 / d3 * 0.2D);
					this.setDeltaMovement(vec3d);
				}
			}
		} else {
			this.discard();
		}
		this.move(MoverType.SELF, this.getDeltaMovement());
	}

	protected void onHit(HitResult result) {
		if (this.level().isClientSide() || this.isRemoved() || this.getHeldBlockState() == null) return;
		BlockPos impact = result instanceof BlockHitResult hit ? hit.getBlockPos()
				: result instanceof EntityHitResult hit ? hit.getEntity().blockPosition() : null;
		if (impact == null) return;
		for (Entity entity : this.level().getEntities(this, this.getBoundingBox().inflate(1)))
			entity.hurt(this.damageSources().inWall(), 8);
		if (this.dropBlock) {
			BlockPos destination = impact.above();
			BlockState state = this.getHeldBlockState();
			boolean placed = this.level().hasChunkAt(destination) && !this.level().isOutsideBuildHeight(destination)
					&& this.level().getWorldBorder().isWithinBounds(destination)
					&& this.level().getBlockEntity(destination) == null
					&& this.level().getBlockState(destination).canBeReplaced()
					&& state.canSurvive(this.level(), destination)
					&& this.level().setBlockAndUpdate(destination, state);
			BlockEntity blockEntity = placed ? this.level().getBlockEntity(destination) : null;
			if (blockEntity != null && this.tileEntityData != null) {
				CompoundTag restored = blockEntity.saveWithoutMetadata();
				for (String key : this.tileEntityData.getAllKeys())
					if (!key.equals("id") && !key.equals("x") && !key.equals("y") && !key.equals("z")) restored.put(key, this.tileEntityData.get(key).copy());
				blockEntity.load(restored);
				blockEntity.setChanged();
				this.level().sendBlockUpdated(destination, state, state, 3);
			}
			if (!placed) {
				// This is a physical block taken from the world, not bonus mob loot.
				ItemStack fallback = new ItemStack(state.getBlock());
				if (this.tileEntityData != null) {
					CompoundTag data = this.tileEntityData.copy();
					data.remove("x"); data.remove("y"); data.remove("z");
					fallback.addTagElement("BlockEntityTag", data);
				}
				this.spawnAtLocation(fallback);
			}
		}
		this.discard();
	}

	/**
	 * (abstract) Protected helper method to write subclass entity data to NBT.
	 */
	public void addAdditionalSaveData(CompoundTag compound) {
		compound.put("direction", this.newDoubleList(this.getDeltaMovement().x, this.getDeltaMovement().y, this.getDeltaMovement().z));
		compound.putInt("life", this.ticksAlive);
		compound.putBoolean("DropBlock", this.dropBlock);
		BlockState blockstate = this.getHeldBlockState();
		if (blockstate != null) {
			compound.put("carriedBlockState", NbtUtils.writeBlockState(blockstate));
		}
		if (this.tileEntityData != null) {
			compound.put("TileEntityData", this.tileEntityData);
		}
	}

	/**
	 * (abstract) Protected helper method to read subclass entity data from NBT.
	 */
	public void readAdditionalSaveData(CompoundTag compound) {
		this.ticksAlive = compound.getInt("life");
		this.dropBlock = !compound.contains("DropBlock") || compound.getBoolean("DropBlock");

		if (compound.contains("direction", 9) && compound.getList("direction", 6).size() == 3) {
			ListTag nbttaglist1 = compound.getList("direction", 6);
			this.setDeltaMovement(nbttaglist1.getDouble(0), nbttaglist1.getDouble(1), nbttaglist1.getDouble(2));
		} else {
			this.discard();
		}

		BlockState blockstate = null;
		if (compound.contains("carriedBlockState", 10)) {
			blockstate = NbtUtils.readBlockState(this.level().registryAccess().lookupOrThrow(Registries.BLOCK), compound.getCompound("carriedBlockState"));
			if (blockstate.isAir()) {
				blockstate = null;
			}
		}
		if (blockstate != null) {
			this.setHeldBlockState(blockstate);
		}
		if (compound.contains("TileEntityData", 10)) {
			this.tileEntityData = compound.getCompound("TileEntityData");
		}
	}

	/**
	 * Returns true if other Entities should be prevented from moving through this Entity.
	 */
	public boolean canBeCollidedWith() {
		return true;
	}

	/**
	 * Called when the entity is attacked.
	 */
	public boolean hurt(DamageSource source, float amount) {
		if (this.isInvulnerableTo(source)) {
			return false;
		} else {
			this.markHurt();

			if (source.getEntity() != null) {
				Vec3 vec3d = source.getEntity().getLookAngle();
				this.setDeltaMovement(vec3d);
				if (source.getEntity() instanceof LivingEntity living) {
					this.shootingEntity = living;
				}

				return true;
			} else {
				return false;
			}
		}
	}
}
