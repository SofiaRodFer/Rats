package com.github.alexthe666.rats.server.entity.ai.goal.harvest;

import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.misc.RatUtils;
import com.github.alexthe666.rats.server.misc.FarmCrops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class RatFarmGoal extends BaseRatHarvestGoal {
	private final TamedRat rat;
	private final RatFarmingBlockGoal managed;
	private final RatHarvestCropsGoal harvesting;
	private boolean managedActive, harvestActive;
	public String debugState() { return "managed=" + this.managedActive + "; harvest=" + this.harvestActive + "; returning=" + this.rat.farmReturning + "; " + this.managed.debugState(); }

	public RatFarmGoal(TamedRat rat) {
		super(rat);
		this.rat = rat;
		this.managed = new RatFarmingBlockGoal(rat); this.harvesting = new RatHarvestCropsGoal(rat);
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		this.managedActive = false; this.harvestActive = false;
		if (this.rat.farmReturning) return false;
		if (RatFarmingBlockGoal.station(this.rat) != null) { this.managedActive = this.managed.canUse(); return this.managedActive; }
		if (!super.canUse() || !this.checkTheBasics(this.rat.getDepositPos().isPresent(), false)) {
			return false;
		}
		this.selectSupplies(); this.resetTarget();
		if (this.getTargetBlock() != null) return true;
		if (!this.rat.cargo.isFull() && this.harvesting.findWork()) { this.harvestActive = true; return true; }
		if (!this.rat.cargo.isEmpty() && this.rat.getDepositPos().isPresent()) this.rat.farmReturning = true;
		return false;
	}
	private void selectSupplies() {
		if (this.holdingSeeds() || this.holdingBonemeal()) return;
		for (int i = 1; i < this.rat.cargo.slots(); i++) if (FarmCrops.seed(this.rat.cargo.get(i)) || this.rat.cargo.get(i).is(Items.BONE_MEAL)) { var old = this.rat.cargo.get(0); this.rat.cargo.set(0, this.rat.cargo.get(i)); this.rat.cargo.set(i, old); return; }
	}

	private boolean holdingSeeds() {
		ItemStack stack = this.rat.getItemInHand(InteractionHand.MAIN_HAND);
		return !stack.isEmpty() && FarmCrops.seed(stack);
	}

	private boolean holdingBonemeal() {
		ItemStack stack = this.rat.getItemInHand(InteractionHand.MAIN_HAND);
		return !stack.isEmpty() && stack.is(Items.BONE_MEAL);
	}

	@Override
	public boolean canContinueToUse() {
		if (this.rat.farmReturning) return false;
		if (this.managedActive) return this.managed.canContinueToUse();
		if (this.harvestActive) return this.harvesting.canContinueToUse();
		return this.checkTheBasics(false, false) && this.getTargetBlock() != null && (this.holdingSeeds() || this.holdingBonemeal());
	}
	@Override public void start() { super.start(); if (this.managedActive) this.managed.start(); if (this.harvestActive) this.harvesting.start(); }
	@Override public void stop() { if (this.managedActive) this.managed.stop(); if (this.harvestActive) this.harvesting.stop(); this.managedActive = false; this.harvestActive = false; super.stop(); }

	@Override
	public void tick() {
		if (this.managedActive) { this.managed.tick(); return; }
		if (this.harvestActive) { this.harvesting.tick(); return; }
		if (this.getTargetBlock() != null) {
			if (this.holdingSeeds()) {
				BlockState block = this.rat.level().getBlockState(this.getTargetBlock());
				this.rat.getNavigation().moveTo(this.getTargetBlock().getX() + 0.5D, this.getTargetBlock().getY(), this.getTargetBlock().getZ() + 0.5D, 1.25D);
				if (block.getBlock().isFertile(block, this.rat.level(), this.getTargetBlock()) && this.rat.level().isEmptyBlock(this.getTargetBlock().above())) {
					double distance = this.rat.getRatDistanceCenterSq(this.getTargetBlock().getX(), this.getTargetBlock().getY(), this.getTargetBlock().getZ());
					if (distance < this.rat.getRatHarvestDistance(0.0F)) {
						if (this.holdingSeeds()) {
							ItemStack seedStack = this.rat.getItemInHand(InteractionHand.MAIN_HAND).copy();
							seedStack.setCount(1);
							this.rat.getItemInHand(InteractionHand.MAIN_HAND).shrink(1);
							if (seedStack.getItem() instanceof ItemNameBlockItem item) {
								this.rat.level().setBlockAndUpdate(this.getTargetBlock().above(), item.getBlock().defaultBlockState());
							}
						}
						this.stop();
					}
				} else {
					this.stop();
				}
			} else if (this.holdingBonemeal()) {
				BlockState block = this.rat.level().getBlockState(this.getTargetBlock());
				this.rat.getNavigation().moveTo(this.getTargetBlock().getX() + 0.5D, this.getTargetBlock().getY(), this.getTargetBlock().getZ() + 0.5D, 1.25D);
				if (this.canPlantBeBonemealed(this.getTargetBlock(), block)) {
					double distance = this.rat.getRatDistanceCenterSq(this.getTargetBlock().getX(), this.getTargetBlock().getY(), this.getTargetBlock().getZ());
					if (distance < 4.5F) {
						if (this.holdingBonemeal()) {
							this.rat.getItemInHand(InteractionHand.MAIN_HAND).shrink(1);
							if (block.getBlock() instanceof BonemealableBlock bonemealable) {
								if (bonemealable.isValidBonemealTarget(this.rat.level(), this.getTargetBlock(), block, this.rat.level().isClientSide())) {
									if (!this.rat.level().isClientSide()) {
										this.rat.level().levelEvent(2005, this.getTargetBlock(), 0);
										this.rat.level().playSound(null, this.getTargetBlock(), SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS);
										bonemealable.performBonemeal((ServerLevel) this.rat.level(), this.rat.level().getRandom(), this.getTargetBlock(), block);
									}
								}
							}
						}
						this.stop();
					}
				} else {
					this.stop();
				}
			}
		}
	}

	private void resetTarget() {
		int RADIUS = this.rat.getRadius();
		if (this.holdingBonemeal()) {
			List<BlockPos> allBlocks = new ArrayList<>();
			for (BlockPos pos : BlockPos.betweenClosedStream(this.rat.getSearchCenter().offset(-RADIUS, -RADIUS, -RADIUS), this.rat.getSearchCenter().offset(RADIUS, RADIUS, RADIUS)).map(BlockPos::immutable).toList()) {
				if (this.canTargetBlock(pos) && this.canPlantBeBonemealed(pos, this.rat.level().getBlockState(pos)) && !RatUtils.isBlockProtected(this.rat.level(), pos, this.rat)) {
					allBlocks.add(pos);
				}
			}
			if (!allBlocks.isEmpty()) {
				allBlocks.sort(this.getTargetSorter());
				this.setTargetBlock(allBlocks.get(0));
			}
		} else if (this.holdingSeeds()) {
			List<BlockPos> allBlocks = new ArrayList<>();
			for (BlockPos pos : BlockPos.betweenClosedStream(this.rat.getSearchCenter().offset(-RADIUS, -RADIUS, -RADIUS), this.rat.getSearchCenter().offset(RADIUS, RADIUS, RADIUS)).map(BlockPos::immutable).toList()) {
				if (this.canTargetBlock(pos) && this.rat.level().getBlockState(pos).getBlock().isFertile(this.rat.level().getBlockState(pos), this.rat.level(), pos) && this.rat.level().isEmptyBlock(pos.above()) && !RatUtils.isBlockProtected(this.rat.level(), pos, this.rat)) {
					allBlocks.add(pos);
				}
			}
			if (!allBlocks.isEmpty()) {
				allBlocks.sort(this.getTargetSorter());
				this.setTargetBlock(allBlocks.get(0));
			}
		}
	}

	private boolean canPlantBeBonemealed(BlockPos pos, BlockState state) {
		if (state.getBlock() instanceof BonemealableBlock bonemealable && state.is(BlockTags.BEE_GROWABLES)) {
			if (bonemealable.isValidBonemealTarget(this.rat.level(), pos, state, this.rat.level().isClientSide())) {
				return bonemealable.isBonemealSuccess(this.rat.level(), this.rat.level().getRandom(), pos, state);
			}
		}
		return false;
	}
}
