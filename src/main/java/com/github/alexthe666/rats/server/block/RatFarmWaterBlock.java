package com.github.alexthe666.rats.server.block;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.*;

/** A full-walled irrigation reservoir. Its real source fluid hydrates crops,
 * while solid faces contain flow even on narrow plots or cavern edges. */
public class RatFarmWaterBlock extends Block {
	public RatFarmWaterBlock(Properties p){super(p);}
	@Override public FluidState getFluidState(BlockState state){return Fluids.WATER.getSource(false);}
}
