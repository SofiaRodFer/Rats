package com.github.alexthe666.rats.server.block;

import com.github.alexthe666.rats.server.block.entity.RatStorageBlockEntity;
import com.github.alexthe666.rats.registry.RatsItemRegistry;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

public class RatStorageBlock extends BaseEntityBlock {
	public enum Kind { CONTROLLER, INPUT, OUTPUT }
	public final Kind kind;
	public RatStorageBlock(Properties properties, Kind kind) { super(properties); this.kind = kind; this.registerDefaultState(this.stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)); }
	@Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(BlockStateProperties.HORIZONTAL_FACING); }
	@Override public BlockState getStateForPlacement(BlockPlaceContext context) { return this.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite()); }
	@Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
	@Override public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
		if (this.kind != Kind.CONTROLLER) return super.getCollisionShape(state, level, pos, context);
		return net.minecraft.world.phys.shapes.Shapes.or(Block.box(0,0,0,16,2,16), Block.box(0,13,0,16,16,16), Block.box(0,2,0,3,13,3), Block.box(13,2,0,16,13,3), Block.box(0,2,13,3,13,16), Block.box(13,2,13,16,13,16));
	}
	@Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RatStorageBlockEntity(pos, state); }
	@Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) { if (level.getBlockEntity(pos) instanceof RatStorageBlockEntity storage) { storage.owner = placer == null ? null : placer.getUUID(); storage.setChanged(); } }
	@Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(BlockStateProperties.HORIZONTAL_FACING,rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING))); }
	@Override public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING))); }
	@Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (player.getItemInHand(hand).is(RatsItemRegistry.CHEESE_STORAGE_STAFF.get())) return InteractionResult.PASS;
		if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer server && level.getBlockEntity(pos) instanceof RatStorageBlockEntity storage) net.minecraftforge.network.NetworkHooks.openScreen(server, storage, buf -> { buf.writeBoolean(this.kind == Kind.CONTROLLER); buf.writeBoolean(this.kind == Kind.OUTPUT); }); return InteractionResult.sidedSuccess(level.isClientSide());
	}
	@Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
		if (!state.is(next.getBlock())) { if (level.getBlockEntity(pos) instanceof RatStorageBlockEntity storage) { Containers.dropContents(level, pos, storage); storage.dropSessions(); storage.destroyNetwork(); } super.onRemove(state, level, pos, next, moving); }
	}
}
