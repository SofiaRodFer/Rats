package com.github.alexthe666.rats.server.items;

import com.github.alexthe666.rats.registry.RatVariantRegistry;
import com.github.alexthe666.rats.registry.RatsEntityRegistry;
import com.github.alexthe666.rats.server.entity.rat.RatCommand;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.misc.RatVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.ForgeSpawnEggItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** NBT variants of the ORIGINAL egg ID, not additional registered items. */
public class VariantRatSpawnEggItem extends ForgeSpawnEggItem {

	public static final String VARIANT_TAG = "RatsTamedEggVariant";

	public VariantRatSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type, int base, int overlay, Properties properties) {
		super(type, base, overlay, properties);
	}

	public ItemStack forVariant(ResourceLocation variant) {
		ItemStack stack = new ItemStack(this);
		stack.getOrCreateTag().putString(VARIANT_TAG, variant.toString());
		return stack;
	}

	public static boolean isTamedEgg(ItemStack stack) {
		return stack.hasTag() && stack.getTag().contains(VARIANT_TAG);
	}

	@Nullable
	private static ResourceLocation variantId(ItemStack stack) {
		return isTamedEgg(stack) ? ResourceLocation.tryParse(stack.getTag().getString(VARIANT_TAG)) : null;
	}

	@Override
	public Component getName(ItemStack stack) {
		ResourceLocation variant = variantId(stack);
		return variant == null ? super.getName(stack) : Component.translatable("item.rats.tamed_rat_spawn_egg",
				Component.translatable("rat_variant." + variant.getNamespace() + "." + variant.getPath()));
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, level, tooltip, flag);
		if (isTamedEgg(stack)) tooltip.add(Component.translatable("item.rats.tamed_rat_spawn_egg.desc"));
	}

	@Override
	protected DispenseItemBehavior createDispenseBehavior() {
		DispenseItemBehavior wild = super.createDispenseBehavior();
		// A dispenser has no player owner: keep the egg instead of creating an orphan.
		return (source, stack) -> isTamedEgg(stack) ? stack : wild.dispense(source, stack);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		ItemStack stack = context.getItemInHand();
		if (!isTamedEgg(stack)) return super.useOn(context);
		Player player = context.getPlayer();
		Level level = context.getLevel();
		BlockPos clicked = context.getClickedPos();
		if (player == null || level.getBlockState(clicked).is(Blocks.SPAWNER)) return InteractionResult.FAIL;
		BlockPos pos = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty()
				? clicked : clicked.relative(context.getClickedFace());
		if (!level.mayInteract(player, clicked) || !player.mayUseItemAt(pos, context.getClickedFace(), stack)) return InteractionResult.FAIL;
		if (level.isClientSide) return InteractionResult.SUCCESS;
		return spawnOwned((ServerLevel) level, player, stack, pos, true, !pos.equals(clicked) && context.getClickedFace() == net.minecraft.core.Direction.UP)
				? InteractionResult.CONSUME : InteractionResult.FAIL;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!isTamedEgg(stack)) return super.use(level, player, hand);
		BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
		if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) return InteractionResultHolder.pass(stack);
		if (!level.mayInteract(player, hit.getBlockPos()) || !player.mayUseItemAt(hit.getBlockPos(), hit.getDirection(), stack)) return InteractionResultHolder.fail(stack);
		if (level.isClientSide) return InteractionResultHolder.success(stack);
		return spawnOwned((ServerLevel) level, player, stack, hit.getBlockPos(), false, false)
				? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
	}

	@Override
	public Optional<Mob> spawnOffspringFromSpawnEgg(Player player, Mob mob, EntityType<? extends Mob> type, ServerLevel level, net.minecraft.world.phys.Vec3 pos, ItemStack stack) {
		return isTamedEgg(stack) ? Optional.empty() : super.spawnOffspringFromSpawnEgg(player, mob, type, level, pos, stack);
	}

	private boolean spawnOwned(ServerLevel level, Player player, ItemStack stack, BlockPos pos, boolean align, boolean offset) {
		ResourceLocation variant = variantId(stack);
		if (variant == null || !RatVariantRegistry.RAT_VARIANT_REGISTRY.get().containsKey(variant) || stack.isEmpty()) return false;
		// Do NOT apply EntityTag: supplied IDs/UUIDs/owners must never override the user.
		TamedRat rat = RatsEntityRegistry.TAMED_RAT.get().create(level, null, null, pos, MobSpawnType.SPAWN_EGG, align, offset);
		if (rat == null) return false;
		rat.setTame(true);
		rat.setOwnerUUID(player.getUUID());
		rat.setColorVariant(RatVariantRegistry.RAT_VARIANT_REGISTRY.get().getValue(variant));
		rat.setAge(0);
		rat.setCommand(RatCommand.WANDER);
		if (stack.hasCustomHoverName()) rat.setCustomName(stack.getHoverName());
		if (!level.addFreshEntity(rat)) return false;
		if (!player.getAbilities().instabuild) stack.shrink(1);
		player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
		level.gameEvent(player, GameEvent.ENTITY_PLACE, pos);
		return true;
	}

	public int getVariantColor(ItemStack stack, int layer) {
		ResourceLocation variant = variantId(stack);
		if (variant == null) return getColor(layer);
		if (layer != 0) return 0xDAABA1;
		return switch (variant.getPath()) {
			case "black" -> 0x30333E;
			case "blue" -> 0x7D8FBA;
			case "brown", "brown_undercoat", "brown_hooded" -> 0x936341;
			case "green" -> 0x79934A;
			case "albino" -> 0xFFF1EB;
			case "hooded" -> 0x4F4545;
			case "gray_hooded" -> 0x8A8890;
			case "yellow_hooded" -> 0xCFAD65;
			case "siamese" -> 0xB8A48A;
			case "white" -> 0xE8E9EF;
			case "hairless" -> 0xD39493;
			default -> getColor(layer);
		};
	}
}
