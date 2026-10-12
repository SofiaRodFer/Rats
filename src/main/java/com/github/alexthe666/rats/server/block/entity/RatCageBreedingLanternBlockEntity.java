package com.github.alexthe666.rats.server.block.entity;

import com.github.alexthe666.rats.RatConfig;
import com.github.alexthe666.rats.registry.RatsBlockEntityRegistry;
import com.github.alexthe666.rats.registry.RatsItemRegistry;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.misc.CageHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public class RatCageBreedingLanternBlockEntity extends DecoratedRatCageBlockEntity {

	private int breedingCooldown = 0;
	private java.util.UUID autoBreedOwner;
	public boolean isAutoBreeding() { return this.autoBreedOwner != null; }
	public boolean canControlAutoBreed(net.minecraft.world.entity.player.Player player) { return this.autoBreedOwner == null || this.autoBreedOwner.equals(player.getUUID()); }
	public boolean toggleAutoBreed(net.minecraft.world.entity.player.Player player, CageHabitat habitat) {
		if (this.level == null || this.level.isClientSide() || !habitat.complete() || !this.canControlAutoBreed(player) || this.autoBreedOwner == null && habitat.rats(this.level).stream().noneMatch(rat -> rat.isTame() && player.getUUID().equals(rat.getOwnerUUID()))) return false;
		this.autoBreedOwner = this.autoBreedOwner == null ? player.getUUID() : null; this.setChanged(); return true;
	}

	public RatCageBreedingLanternBlockEntity(BlockPos pos, BlockState state) {
		super(RatsBlockEntityRegistry.RAT_CAGE_BREEDING_LANTERN.get(), pos, state);
	}

	@Override
	public ItemStack getContainedItem() {
		return new ItemStack(RatsItemRegistry.RAT_BREEDING_LANTERN.get());
	}

	@Override
	public void setContainedItem(ItemStack stack) {
	}

	public int getBreedingCooldown() {
		return this.breedingCooldown;
	}

	@Override
	public void saveAdditional(CompoundTag compound) {
		compound.putInt("BreedingCooldown", this.breedingCooldown);
		if (this.autoBreedOwner != null) compound.putUUID("AutoBreedOwner", this.autoBreedOwner);
		super.saveAdditional(compound);
	}

	@Override
	public void load(CompoundTag compound) {
		super.load(compound);
		this.breedingCooldown = compound.getInt("BreedingCooldown");
		this.autoBreedOwner = compound.hasUUID("AutoBreedOwner") ? compound.getUUID("AutoBreedOwner") : null;
	}


	public boolean breed(CageHabitat habitat, TamedRat female, TamedRat male) {
		return this.breed(habitat, female, male, 64);
	}
	private boolean breed(CageHabitat habitat, TamedRat female, TamedRat male, int maxLitter) {
		if (this.level == null || this.level.isClientSide || !habitat.complete() || !habitat.cages().contains(this.worldPosition) || this.breedingCooldown > 0) return false;
		if (female == male || female.isMale() || !male.isMale() || female.isBaby() || male.isBaby() || female.breedCooldown > 0 || male.breedCooldown > 0) return false;
		if (!female.isAlive() || !male.isAlive() || female.isPassenger() || male.isPassenger() || female.level() != this.level || male.level() != this.level || !habitat.nodes().contains(female.blockPosition()) || !habitat.nodes().contains(male.blockPosition())) return false;
		int room = habitat.capacity() - habitat.rats(this.level).size();
		if (room <= 0) return false;
		int litter = Math.min(Math.min(room, maxLitter), this.level.random.nextInt(Math.max(1, Math.min(64, RatConfig.maxRatLitterSize))) + 1);
		// Lock before spawning: other lanterns cannot breed the same parents again.
		this.breedingCooldown = Math.max(1, RatConfig.ratBreedingCooldown);
		female.breedCooldown = this.breedingCooldown;
		male.breedCooldown = this.breedingCooldown;
		for (int i = 0; i < litter; i++) female.createBabiesFrom(female, male);
		this.level.broadcastEntityEvent(female, (byte) 83);
		this.level.broadcastEntityEvent(male, (byte) 83);
		this.setChanged();
		return true;
	}
	public boolean autoBreed(CageHabitat habitat) {
		if (this.level == null || this.level.isClientSide() || !this.isAutoBreeding() || !habitat.complete() || this.breedingCooldown > 0 || habitat.lantern(this.level).orElse(null) != this) return false;
		var residents = habitat.rats(this.level);
		long adults = residents.stream().filter(rat -> !rat.isBaby()).count(), babies = residents.size() - adults;
		if (babies >= adults / 10 || residents.size() >= habitat.capacity()) return false;
		var parents = residents.stream().filter(rat -> rat.isTame() && this.autoBreedOwner.equals(rat.getOwnerUUID()) && !rat.isBaby() && rat.breedCooldown == 0).toList();
		var female = parents.stream().filter(rat -> !rat.isMale()).findFirst().orElse(null);
		var male = parents.stream().filter(TamedRat::isMale).findFirst().orElse(null);
		return female != null && male != null && this.breed(habitat, female, male, 1);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, RatCageBreedingLanternBlockEntity te) {
		float f = ((float) RatConfig.ratBreedingCooldown - te.breedingCooldown) / (float) RatConfig.ratBreedingCooldown;
		float f1 = f * 0.6F + 0.4F;
		float f2 = Math.max(0.0F, f * f * 0.7F - 0.5F);
		float f3 = Math.max(0.0F, f * f * 0.6F - 0.7F);
		float i = pos.getX() + 0.5F;
		float j = pos.getY() + 0.5F;
		float k = pos.getZ() + 0.5F;
		if (te.breedingCooldown <= 0) {
			if (level.isClientSide()) {
				level.addParticle(DustParticleOptions.REDSTONE, i + level.getRandom().nextDouble() - 0.5D, j + level.getRandom().nextDouble() - 0.5D, k + level.getRandom().nextDouble() - 0.5D, f1, f2, f3);
			}
		} else if (!level.isClientSide) {
			te.breedingCooldown--;
			te.setChanged();
		}
		if (!level.isClientSide() && te.isAutoBreeding() && te.breedingCooldown == 0 && level.getGameTime() % 100 == 0) te.autoBreed(CageHabitat.discover(level, pos));
	}
}
