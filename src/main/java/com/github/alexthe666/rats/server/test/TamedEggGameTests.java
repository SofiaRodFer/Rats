package com.github.alexthe666.rats.server.test;

import com.github.alexthe666.rats.registry.RatVariantRegistry;
import com.github.alexthe666.rats.registry.RatsEntityRegistry;
import com.github.alexthe666.rats.server.entity.rat.Rat;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.items.VariantRatSpawnEggItem;
import com.github.alexthe666.rats.server.misc.RatVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

@GameTestHolder("rats")
@PrefixGameTestTemplate(false)
public class TamedEggGameTests {
	@GameTest(template="quarry_test_empty") public static void randomEggHasOwnerAndLegacyEggRemainsValid(GameTestHelper h){Player player=h.makeMockPlayer();ItemStack random=egg().randomEgg();h.setBlock(new BlockPos(8,3,8),Blocks.STONE);h.assertTrue(use(h,player,random,new BlockPos(8,3,8))==InteractionResult.CONSUME,"Random owned egg spawns");var rat=spawned(h,new BlockPos(8,4,8));h.assertTrue(player.getUUID().equals(rat.getOwnerUUID())&&RatVariantRegistry.RAT_VARIANT_REGISTRY.get().getValues().contains(rat.getColorVariant()),"Valid random color and spawning owner");var old=blue();h.setBlock(new BlockPos(12,3,8),Blocks.STONE);h.assertTrue(use(h,player,old,new BlockPos(12,3,8))==InteractionResult.CONSUME,"Specific old egg still usable without registry changes");h.succeed();}
	private static VariantRatSpawnEggItem egg() {
		return (VariantRatSpawnEggItem) ForgeRegistries.ITEMS.getValue(new ResourceLocation("rats", "rat_spawn_egg"));
	}
	private static ItemStack blue() { return egg().forVariant(RatVariantRegistry.BLUE.getId()); }
	private static InteractionResult use(GameTestHelper helper, Player player, ItemStack stack, BlockPos relative) {
		BlockPos pos = helper.absolutePos(relative);
		if (player != null) {
			player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 1.5);
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		}
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
		UseOnContext context = player == null ? new UseOnContext(helper.getLevel(), null, InteractionHand.MAIN_HAND, stack, hit)
				: new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
		return egg().useOn(context);
	}
	private static TamedRat spawned(GameTestHelper helper, BlockPos relative) {
		return helper.getLevel().getEntitiesOfClass(TamedRat.class, new net.minecraft.world.phys.AABB(helper.absolutePos(relative)).inflate(1)).stream().findFirst().orElseThrow();
	}

	@GameTest(template = "quarry_test_empty")
	public static void allVariantsOwnedAndPersisted(GameTestHelper helper) {
		Player owner = helper.makeMockPlayer();
		int x = 2;
		for (ResourceLocation id : RatVariantRegistry.RAT_VARIANT_REGISTRY.get().getKeys().stream().sorted().toList()) {
			BlockPos floor = new BlockPos(x++, 3, 4); helper.setBlock(floor, Blocks.STONE);
			ItemStack stack = egg().forVariant(id);
			CompoundTag eggNbt = stack.save(new CompoundTag());
			helper.assertTrue(eggNbt.getString("id").equals("rats:rat_spawn_egg"), "No new item registry IDs");
			stack = ItemStack.of(eggNbt);
			helper.assertTrue(use(helper, owner, stack, floor) == InteractionResult.CONSUME && stack.isEmpty(), "Survival egg must spawn and consume once");
			TamedRat rat = spawned(helper, floor.above());
			helper.assertTrue(rat.isTame() && owner.getUUID().equals(rat.getOwnerUUID()) && RatVariant.getVariantId(rat.getColorVariant()).equals(id.toString()), "Every variant must belong to the actual user");
			CompoundTag saved = new CompoundTag(); rat.save(saved);
			helper.assertTrue(saved.getString("id").equals("rats:tamed_rat") && saved.getUUID("Owner").equals(owner.getUUID()) && saved.getString("ColorVariant").equals(id.toString()), "Use original entity/owner/color save fields");
			TamedRat loaded = (TamedRat) EntityType.loadEntityRecursive(saved, helper.getLevel(), entity -> entity);
			helper.assertTrue(loaded != null && loaded.isTame() && owner.getUUID().equals(loaded.getOwnerUUID()) && RatVariant.getVariantId(loaded.getColorVariant()).equals(id.toString()), "Owner and variant must survive a save/load");
			rat.discard();
		}
		helper.assertTrue(x == 15, "All thirteen built-in variants tested");
		helper.succeed();
	}

	@GameTest(template = "quarry_test_empty")
	public static void entityTagCannotSpoofOwnerOrType(GameTestHelper helper) {
		BlockPos floor = new BlockPos(4, 3, 4); helper.setBlock(floor, Blocks.STONE);
		Player player = helper.makeMockPlayer(); ItemStack stack = blue();
		CompoundTag forged = new CompoundTag(); forged.putString("id", "rats:demon_rat"); forged.putUUID("Owner", UUID.randomUUID()); forged.putString("ColorVariant", "rats:green"); forged.putInt("Age", -24000);
		stack.getOrCreateTag().put("EntityTag", forged);
		helper.assertTrue(use(helper, player, stack, floor) == InteractionResult.CONSUME, "Spawn must succeed with ignored hostile EntityTag");
		TamedRat rat = spawned(helper, floor.above());
		helper.assertTrue(player.getUUID().equals(rat.getOwnerUUID()) && rat.getColorVariant() == RatVariantRegistry.BLUE.get() && !rat.isBaby(), "User, variant and adulthood cannot be forged");
		helper.succeed();
	}

	@GameTest(template = "quarry_test_empty")
	public static void invalidOwnerlessAndSpawnerRejected(GameTestHelper helper) {
		BlockPos floor = new BlockPos(4, 3, 4); helper.setBlock(floor, Blocks.STONE);
		Player player = helper.makeMockPlayer(); ItemStack invalid = egg().forVariant(new ResourceLocation("rats", "missing"));
		helper.assertTrue(use(helper, player, invalid, floor) == InteractionResult.FAIL && invalid.getCount() == 1, "Unknown variant must fail without consuming");
		ItemStack noPlayer = blue();
		helper.assertTrue(use(helper, null, noPlayer, floor) == InteractionResult.FAIL && noPlayer.getCount() == 1, "Player is mandatory");
		helper.setBlock(floor, Blocks.SPAWNER); ItemStack spawnerEgg = blue();
		helper.assertTrue(use(helper, player, spawnerEgg, floor) == InteractionResult.FAIL && spawnerEgg.getCount() == 1, "Spawner must not produce ownerless pets");
		SpawnerBlockEntity spawner = (SpawnerBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(floor));
		helper.assertTrue(!spawner.getSpawner().save(new CompoundTag()).toString().contains("rats:tamed_rat"), "Spawner state must remain unchanged");
		helper.succeed();
	}

	@GameTest(template = "quarry_test_empty")
	public static void creativeAndNamedEgg(GameTestHelper helper) {
		BlockPos floor = new BlockPos(4, 3, 4); helper.setBlock(floor, Blocks.STONE);
		Player player = helper.makeMockPlayer(); player.getAbilities().instabuild = true;
		ItemStack stack = blue(); stack.setHoverName(net.minecraft.network.chat.Component.literal("Luna"));
		helper.assertTrue(use(helper, player, stack, floor) == InteractionResult.CONSUME && stack.getCount() == 1, "Creative must retain egg");
		helper.assertTrue(spawned(helper, floor.above()).getCustomName().getString().equals("Luna"), "Renamed egg must name the pet");
		helper.succeed();
	}

	@GameTest(template = "quarry_test_empty", timeoutTicks = 40)
	public static void dispenserKeepsTamedEgg(GameTestHelper helper) {
		BlockPos pos = new BlockPos(4, 4, 4); helper.setBlock(pos, Blocks.DISPENSER);
		DispenserBlockEntity dispenser = (DispenserBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
		dispenser.setItem(0, blue()); helper.setBlock(pos.east(), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(dispenser.getItem(0).getCount() == 1 && VariantRatSpawnEggItem.isTamedEgg(dispenser.getItem(0)), "Dispenser must retain marked egg");
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(TamedRat.class, new net.minecraft.world.phys.AABB(helper.absolutePos(pos)).inflate(3)).isEmpty(), "No orphan rat may spawn");
			helper.succeed();
		});
	}

	@GameTest(template = "quarry_test_empty")
	public static void wildEggAndOffspringUnchanged(GameTestHelper helper) {
		BlockPos floor = new BlockPos(4, 3, 4); helper.setBlock(floor, Blocks.STONE);
		Player player = helper.makeMockPlayer(); ItemStack wild = new ItemStack(egg());
		helper.assertTrue(use(helper, player, wild, floor) == InteractionResult.CONSUME && wild.isEmpty(), "Ordinary egg must retain original behavior");
		Rat rat = helper.getLevel().getEntitiesOfClass(Rat.class, new net.minecraft.world.phys.AABB(helper.absolutePos(floor.above())).inflate(1)).get(0);
		helper.assertTrue(!rat.isTame(), "Original egg still creates a wild rat");
		ItemStack marked = blue();
		helper.assertTrue(egg().spawnOffspringFromSpawnEgg(player, rat, RatsEntityRegistry.RAT.get(), helper.getLevel(), rat.position(), marked).isEmpty() && marked.getCount() == 1, "Marked eggs must not be mistaken for wild offspring eggs");
		helper.succeed();
	}

	@GameTest(template = "quarry_test_empty")
	public static void waterUseAssignsOwner(GameTestHelper helper) {
		BlockPos water = new BlockPos(4, 4, 4); helper.setBlock(water, Blocks.WATER);
		Player player = helper.makeMockPlayer(); BlockPos absolute = helper.absolutePos(water);
		player.setPos(absolute.getX() + 0.5, absolute.getY() + 1, absolute.getZ() + 0.5); player.setXRot(90);
		ItemStack stack = blue(); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		helper.assertTrue(egg().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.CONSUME && stack.isEmpty(), "Water use must spawn exactly once");
		helper.assertTrue(player.getUUID().equals(spawned(helper, water).getOwnerUUID()), "Water-spawned rat must have actual owner");
		helper.succeed();
	}
}
