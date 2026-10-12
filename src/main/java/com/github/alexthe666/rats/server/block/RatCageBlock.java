package com.github.alexthe666.rats.server.block;

import com.github.alexthe666.rats.registry.RatsBlockRegistry;
import com.github.alexthe666.rats.registry.RatsItemRegistry;
import com.github.alexthe666.rats.server.block.entity.DecoratedRatCageBlockEntity;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.items.RatCageDecoration;
import com.github.alexthe666.rats.server.misc.RatsLangConstants;
import com.github.alexthe666.rats.server.misc.CageHabitat;
import com.github.alexthe666.rats.server.inventory.RatCageMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@SuppressWarnings("deprecation")
public class RatCageBlock extends Block {

	public static final IntegerProperty NORTH = IntegerProperty.create("north", 0, 2);
	public static final IntegerProperty EAST = IntegerProperty.create("east", 0, 2);
	public static final IntegerProperty SOUTH = IntegerProperty.create("south", 0, 2);
	public static final IntegerProperty WEST = IntegerProperty.create("west", 0, 2);
	public static final IntegerProperty UP = IntegerProperty.create("up", 0, 2);
	public static final IntegerProperty DOWN = IntegerProperty.create("down", 0, 2);
	public static final net.minecraft.world.level.block.state.properties.BooleanProperty SHELF = net.minecraft.world.level.block.state.properties.BooleanProperty.create("shelf");
	public static final net.minecraft.world.level.block.state.properties.BooleanProperty LADDER = net.minecraft.world.level.block.state.properties.BooleanProperty.create("ladder");
	private static final VoxelShape SHELF_FULL = Block.box(0, 0, 0, 16, 2, 16);
	private static final VoxelShape SHELF_ACCESS = Block.box(0, 0, 10, 16, 2, 16);
	private static final VoxelShape SHELF_TUBE = Shapes.or(Block.box(0, 0, 0, 4, 2, 16), Block.box(12, 0, 0, 16, 2, 16), Block.box(4, 0, 0, 12, 2, 4), Block.box(4, 0, 12, 12, 2, 16));

	private static final VoxelShape BOTTOM_AABB = Block.box(0F, 0F, 0F, 16F, 2F, 16F);
	private static final VoxelShape TOP_AABB = Block.box(0F, 15F, 0F, 16F, 16F, 16F);
	private static final VoxelShape NORTH_AABB = Block.box(0F, 0F, 0F, 16F, 16F, 1F);
	private static final VoxelShape SOUTH_AABB = Block.box(0F, 0F, 15F, 16F, 16F, 16F);
	private static final VoxelShape EAST_AABB = Block.box(15F, 0F, 0F, 16F, 16F, 16F);
	private static final VoxelShape WEST_AABB = Block.box(0F, 0F, 0F, 1F, 16F, 16F);

	private static final Map<Direction, IntegerProperty> PROPERTY_BY_DIRECTION = ImmutableMap.copyOf(Util.make(Maps.newEnumMap(Direction.class), map -> {
		map.put(Direction.NORTH, NORTH);
		map.put(Direction.EAST, EAST);
		map.put(Direction.SOUTH, SOUTH);
		map.put(Direction.WEST, WEST);
		map.put(Direction.UP, UP);
		map.put(Direction.DOWN, DOWN);
	}));

	private static final Map<Direction, VoxelShape> SHAPE_BY_DIRECTION = Util.make(Maps.newEnumMap(Direction.class), map -> {
		map.put(Direction.NORTH, NORTH_AABB);
		map.put(Direction.EAST, EAST_AABB);
		map.put(Direction.SOUTH, SOUTH_AABB);
		map.put(Direction.WEST, WEST_AABB);
		map.put(Direction.UP, TOP_AABB);
		map.put(Direction.DOWN, BOTTOM_AABB);
	});

	public RatCageBlock(BlockBehaviour.Properties properties) {
		super(properties.randomTicks());
		this.registerDefaultState(getDefaultCage(this.getStateDefinition()));
	}

	private static BlockState getDefaultCage(StateDefinition<Block, BlockState> definition) {
		BlockState blockstate = definition.any();

		for (IntegerProperty property : PROPERTY_BY_DIRECTION.values()) {
			if (blockstate.hasProperty(property)) {
				blockstate = blockstate.setValue(property, 0);
			}
		}

		return blockstate.setValue(SHELF, false).setValue(LADDER, false);
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable BlockGetter getter, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("block.rats.rat_cage.desc0").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("block.rats.rat_cage.desc1").withStyle(ChatFormatting.GRAY));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SHELF, LADDER);
		for (Direction direction : Direction.values()) {
			builder.add(PROPERTY_BY_DIRECTION.get(direction));
		}
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState blockstate = this.defaultBlockState();

		for (Map.Entry<Direction, IntegerProperty> entry : PROPERTY_BY_DIRECTION.entrySet()) {
			if (blockstate.hasProperty(entry.getValue())) {
				blockstate = blockstate.setValue(entry.getValue(), runConnectionLogic(context.getLevel().getBlockState(context.getClickedPos().relative(entry.getKey()))));
			}
		}

		return blockstate;
	}

	public static int runConnectionLogic(BlockState state) {
		if (state.getBlock() instanceof RatTubeBlock) {
			return 2;
		}
		return state.getBlock() instanceof RatCageBlock ? 1 : 0;
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext context) {
		VoxelShape shape1 = Block.box(0, 0, 0, 0, 0, 0);
		if (state.getBlock() instanceof RatCageBlock) {
			for (Map.Entry<Direction, IntegerProperty> entry : PROPERTY_BY_DIRECTION.entrySet()) {
				if (state.getValue(entry.getValue()) == 0) {
					shape1 = Shapes.join(shape1, SHAPE_BY_DIRECTION.get(entry.getKey()), BooleanOp.OR);
				}
			}
		}
		if (state.getValue(SHELF)) shape1 = Shapes.or(shape1, state.getValue(DOWN) == 2 ? SHELF_TUBE : state.getValue(LADDER) ? SHELF_ACCESS : SHELF_FULL);
		return shape1;
	}
	@Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
		super.onPlace(state, level, pos, previous, moving);
		if (!level.isClientSide() && state.getBlock() != previous.getBlock()) level.scheduleTick(pos, this, 1);
	}
	@Override public void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
		com.github.alexthe666.rats.server.misc.CageLayout.synchronize(level, CageHabitat.discover(level, pos));
	}
	@Override public void randomTick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) { this.tick(state, level, pos, random); }

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!level.isClientSide()) { com.github.alexthe666.rats.server.misc.CageLayout.synchronize(level, CageHabitat.discover(level, pos)); state = level.getBlockState(pos); }
		if (level.isClientSide) return player.getItemInHand(hand).isEmpty() || player.getItemInHand(hand).getItem() instanceof RatCageDecoration || player.isShiftKeyDown() ? InteractionResult.SUCCESS : InteractionResult.PASS;
		if (player.getItemInHand(hand).getItem() instanceof RatCageDecoration decoration && level.getBlockEntity(pos) == null) {
			Direction limitedFacing = player.getDirection().getOpposite();
			if (supportsDecoration(state, level, pos, decoration.getSupportedFace(limitedFacing))) {
				if (player.getItemInHand(hand).is(RatsItemRegistry.RAT_BREEDING_LANTERN.get())) {
					BlockState pre = level.getBlockState(pos);
					BlockState decorated = RatsBlockRegistry.RAT_CAGE_BREEDING_LANTERN.get().withPropertiesOf(pre);
					this.setupCage(level, pos, decorated, limitedFacing, player, hand);
				} else if (player.getItemInHand(hand).is(RatsItemRegistry.RAT_WHEEL.get())) {
					BlockState pre = level.getBlockState(pos);
					BlockState decorated = RatsBlockRegistry.RAT_CAGE_WHEEL.get().withPropertiesOf(pre);
					this.setupCage(level, pos, decorated, limitedFacing, player, hand);
				} else {
					BlockState pre = level.getBlockState(pos);
					BlockState decorated = RatsBlockRegistry.RAT_CAGE_DECORATED.get().withPropertiesOf(pre);
					this.setupCage(level, pos, decorated, limitedFacing, player, hand);
				}

				return InteractionResult.SUCCESS;
			}
		}
		if (level.getBlockEntity(pos) != null) {
			ItemStack stack = this.getContainedItem(level, pos);
			if (!stack.isEmpty() && player.isShiftKeyDown()) {
				BlockState pre = level.getBlockState(pos);
				BlockState decorated = RatsBlockRegistry.RAT_CAGE.get().defaultBlockState();
				decorated = decorated.getBlock().withPropertiesOf(pre);
				level.setBlock(pos, decorated, 3);
				level.setBlockAndUpdate(pos, decorated);
			}
		}
		if (player.getItemInHand(hand).isEmpty() && !player.isShiftKeyDown()) {
			CageHabitat habitat = CageHabitat.discover(level, pos);
			List<Entity> passengers = List.copyOf(player.getPassengers());
			boolean ridingRats = passengers.stream().anyMatch(entity -> entity instanceof TamedRat rat && rat.isOwnedBy(player));
			int ratCount = 0;
			if (ridingRats) {
				for (Entity entity : passengers) {
					if (entity instanceof TamedRat rat && rat.isOwnedBy(player) && habitat.admit(rat, pos)) {
						ratCount++;
					}
				}
				player.displayClientMessage(Component.translatable(RatsLangConstants.CAGE_DEPOSIT, ratCount), true);
			}
			if (!ridingRats || ratCount == 0) {
				if (player instanceof ServerPlayer server) NetworkHooks.openScreen(server, new SimpleMenuProvider((id, inventory, viewer) -> new RatCageMenu(id, inventory, pos), Component.translatable("cage.rats.title")), pos);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	private void setupCage(Level level, BlockPos pos, BlockState state, Direction dir, Player player, InteractionHand hand) {
		level.setBlockAndUpdate(pos, state.setValue(RatCageDecoratedBlock.FACING, dir));
		if (level.getBlockEntity(pos) instanceof DecoratedRatCageBlockEntity cage) {
			cage.setContainedItem(player.getItemInHand(hand).copyWithCount(1));
		}
		if (!player.isCreative()) {
			player.getItemInHand(hand).shrink(1);
		}
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	public void onRatsRemoved(BlockState state, Level level, BlockPos pos, Player player) {

	}

	public ItemStack getContainedItem(Level level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof DecoratedRatCageBlockEntity decorated) {
			return decorated.getContainedItem();
		}
		return ItemStack.EMPTY;
	}

	public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor accessor, BlockPos currentPos, BlockPos facingPos) {
		accessor.scheduleTick(currentPos, this, 1);
		state = state.setValue(PROPERTY_BY_DIRECTION.get(facing), runConnectionLogic(facingState));
		if (accessor.getBlockEntity(currentPos) instanceof DecoratedRatCageBlockEntity decorated && !decorated.getContainedItem().isEmpty()) {
			if (decorated.getContainedItem().getItem() instanceof RatCageDecoration decoration && !supportsDecoration(state, accessor, currentPos, decoration.getSupportedFace(state.getValue(RatCageDecoratedBlock.FACING)))) {
				BlockState pre = state;
				BlockState emptyCage = RatsBlockRegistry.RAT_CAGE.get().defaultBlockState();
				state = emptyCage.getBlock().withPropertiesOf(pre);
				accessor.setBlock(currentPos, state, 3);
			}
		}
		return state;
	}
	private static boolean supportsDecoration(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
		if (state.getValue(PROPERTY_BY_DIRECTION.get(face)) == 0) return true;
		if (face == Direction.DOWN && state.getValue(SHELF)) return true;
		var above = level.getBlockState(pos.above());
		if (face == Direction.UP && above.getBlock() instanceof RatCageBlock && above.getValue(SHELF)) return true;
		if (level instanceof Level world && (face == Direction.DOWN || face == Direction.UP)) {
			var habitat = CageHabitat.discover(world, pos);
			// Topology changes arrive before the deferred shelf refresh.
			return habitat.complete() && com.github.alexthe666.rats.server.misc.CageLayout.capacityLevel(habitat, face == Direction.DOWN ? pos : pos.above());
		}
		return false;
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return this.mapDirections(state, rotation::rotate);
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return this.mapDirections(state, mirror::mirror);
	}

	private BlockState mapDirections(BlockState state, Function<Direction, Direction> rotation) {
		BlockState blockstate = state;

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			blockstate = blockstate.setValue(PROPERTY_BY_DIRECTION.get(rotation.apply(direction)), state.getValue(PROPERTY_BY_DIRECTION.get(direction)));
		}

		return blockstate;
	}
}
