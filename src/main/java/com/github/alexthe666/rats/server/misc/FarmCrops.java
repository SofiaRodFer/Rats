package com.github.alexthe666.rats.server.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraftforge.registries.ForgeRegistries;

public final class FarmCrops {
	private FarmCrops() { }
	public static boolean seed(ItemStack stack) {
		if (!(stack.getItem() instanceof BlockItem item)) return false;
		var state = item.getBlock().defaultBlockState();
		return item.getBlock() instanceof CropBlock || (state.is(net.minecraft.tags.BlockTags.CROPS) && !(item.getBlock() instanceof net.minecraft.world.level.block.StemBlock));
	}
	public static BlockState plant(ItemStack stack) { return ((BlockItem) stack.getItem()).getBlock().defaultBlockState(); }
	public static boolean mature(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return false;
		var id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
		if (id != null && id.toString().equals("farmersdelight:budding_tomatoes")) return false;
		if (state.getBlock() instanceof CropBlock crop) return crop.isMaxAge(state);
		if (!state.is(net.minecraft.tags.BlockTags.CROPS)) return false;
		for (var property : state.getProperties()) if (property instanceof IntegerProperty age && property.getName().equals("age")) return state.getValue(age).equals(age.getPossibleValues().stream().max(Integer::compareTo).orElse(0));
		return false;
	}
}
