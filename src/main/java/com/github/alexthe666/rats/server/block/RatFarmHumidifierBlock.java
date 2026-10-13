package com.github.alexthe666.rats.server.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/** Solid light-emitting blue-cheese reservoir; vanilla farmland sees real moisture. */
public final class RatFarmHumidifierBlock extends SetupHorizontalBlock {
	public RatFarmHumidifierBlock(Properties properties) { super(properties); }
	@Override public FluidState getFluidState(BlockState state) { return Fluids.WATER.getSource(false); }
}
