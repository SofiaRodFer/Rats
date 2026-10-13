package com.github.alexthe666.rats.server.test;

import com.github.alexthe666.rats.RatConfig;
import com.github.alexthe666.rats.registry.RatlantisEntityRegistry;
import com.github.alexthe666.rats.registry.RatsEntityRegistry;
import com.github.alexthe666.rats.registry.RatsItemRegistry;
import com.github.alexthe666.rats.server.capability.SelectedRatCapability;
import com.github.alexthe666.rats.server.entity.ai.goal.PiratWanderGoal;
import com.github.alexthe666.rats.server.entity.ai.goal.harvest.RatPickpocketGoal;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.items.upgrades.MobFilterUpgradeItem;
import com.github.alexthe666.rats.server.message.UpdateSelectedRatPacket;
import com.github.alexthe666.rats.server.misc.RatUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Independent regression cases for the Astra audit. No client or launcher state. */
@GameTestHolder("rats")
@PrefixGameTestTemplate(false)
public class AstraUtilityGameTests {

	private static TamedRat rat(GameTestHelper h) {
		var rat = RatsEntityRegistry.TAMED_RAT.get().create(h.getLevel());
		rat.setPos(h.absolutePos(new BlockPos(8, 4, 8)).getCenter());
		return rat;
	}

	private static final class LastIndexRandom extends LegacyRandomSource {
		private LastIndexRandom() { super(0); }
		@Override public int nextInt(int bound) {
			if (bound <= 0) throw new IllegalArgumentException("Random bound must be positive");
			return bound - 1;
		}
	}

	@GameTest(template = "quarry_test_empty")
	public static void radiusBoundsApplyToSetterAndLegacyNbt(GameTestHelper h) {
		var rat = rat(h);
		var center = GlobalPos.of(h.getLevel().dimension(), rat.blockPosition());
		rat.setRadiusCenter(center);
		rat.setRadius(Integer.MAX_VALUE);
		h.assertTrue(rat.getRadius() == RatConfig.maxRatRadius, "Huge packet/model radius is clamped before any cubic search");
		rat.setRadius(Integer.MIN_VALUE);
		h.assertTrue(rat.getRadius() == 0, "Negative radius cannot invert the search cube");
		int valid = Math.min(3, RatConfig.maxRatRadius);
		rat.setRadius(valid);
		var saved = new CompoundTag(); rat.addAdditionalSaveData(saved);
		rat.readAdditionalSaveData(saved);
		h.assertTrue(rat.getRadius() == valid, "Valid persisted radius is unchanged");
		saved.putInt("SearchRadius", Integer.MAX_VALUE);
		rat.readAdditionalSaveData(saved);
		h.assertTrue(rat.getRadius() == RatConfig.maxRatRadius, "NBT cannot bypass the same bound");
		saved.remove("SearchRadius");
		rat.readAdditionalSaveData(saved);
		h.assertTrue(rat.getRadius() == Math.min(RatConfig.defaultRatRadius, RatConfig.maxRatRadius), "Missing old radius uses the bounded default, not zero");
		h.succeed();
	}

	@GameTest(template = "quarry_test_empty")
	public static void applyingNbtTwiceDoesNotDuplicatePatrolAndAbsentFieldsClear(GameTestHelper h) {
		var rat = rat(h);
		var first = GlobalPos.of(h.getLevel().dimension(), rat.blockPosition());
		var second = GlobalPos.of(h.getLevel().dimension(), rat.blockPosition().east(3));
		rat.getPatrolNodes().add(first); rat.getPatrolNodes().add(second);
		rat.setHomePoint(first); rat.setRadiusCenter(second);
		var saved = new CompoundTag(); rat.addAdditionalSaveData(saved);
		rat.readAdditionalSaveData(saved); rat.readAdditionalSaveData(saved);
		h.assertTrue(rat.getPatrolNodes().equals(List.of(first, second)), "Reload is replacement, preserving order without duplicated nodes");
		saved.remove("PatrolNodesTag"); saved.remove("HomePos"); saved.remove("RadiusPos");
		rat.readAdditionalSaveData(saved);
		h.assertTrue(rat.getPatrolNodes().isEmpty() && rat.getHomePoint().isEmpty() && rat.getRadiusCenter().isEmpty(), "Absent optional fields clear prior instance state");
		h.succeed();
	}

	@GameTest(template = "quarry_test_empty")
	public static void pickpocketHandlesSingleOfferAndCanSelectLastOffer(GameTestHelper h) {
		var owner = h.makeMockPlayer();
		var source = new LastIndexRandom();
		var rat = new TamedRat(RatsEntityRegistry.TAMED_RAT.get(), h.getLevel()) {
			@Override public LivingEntity getOwner() { return owner; }
			@Override public RandomSource getRandom() { return source; }
		};
		rat.setPos(h.absolutePos(new BlockPos(8, 4, 8)).getCenter());
		var villager = EntityType.VILLAGER.create(h.getLevel());
		villager.setPos(rat.position());
		var offers = villager.getOffers(); offers.clear();
		offers.add(new MerchantOffer(new ItemStack(Items.EMERALD), new ItemStack(Items.APPLE), 10, 0, 0.0F));
		var goal = new RatPickpocketGoal(rat);
		goal.setTargetEntity(villager); goal.tick();
		h.assertTrue(rat.getMainHandItem().is(Items.APPLE), "Actual pickpocket tick handles exactly one offer without nextInt(0)");
		rat.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); rat.pickpocketCooldown = 0;
		offers.add(new MerchantOffer(new ItemStack(Items.EMERALD), new ItemStack(Items.DIAMOND), 10, 0, 0.0F));
		goal.setTargetEntity(villager); goal.tick();
		h.assertTrue(rat.getMainHandItem().is(Items.DIAMOND), "Deterministic last random index reaches the last real offer");
		h.succeed();
	}

	@GameTest(template = "quarry_test_empty")
	public static void foodAndContaminationIncludeLastEligibleSlot(GameTestHelper h) {
		var random = new LastIndexRandom();
		var food = new SimpleContainer(new ItemStack(Items.APPLE), new ItemStack(Items.CARROT));
		h.assertTrue(RatUtils.getFoodFromInventory(food, random).is(Items.CARROT), "Last of two eligible foods can be selected");
		var empty = new SimpleContainer(3);
		h.assertTrue(RatUtils.getContaminatedSlot(empty, random) == 2, "Last of three eligible contamination slots can be selected");
		h.assertTrue(RatUtils.getContaminatedSlot(new SimpleContainer(1), random) == 0, "Single eligible slot is also safe");
		h.succeed();
	}

	@GameTest(template = "quarry_test_empty")
	public static void waterSearchDescendsAndReturnsOriginWhenDry(GameTestHelper h) {
		// Run only after R14 is fixed: the audited old helper does not terminate here.
		var rat = rat(h);
		var top = h.absolutePos(new BlockPos(8, 8, 8));
		h.getLevel().setBlockAndUpdate(top, Blocks.AIR.defaultBlockState());
		for (int i = 1; i <= 3; i++) h.getLevel().setBlockAndUpdate(top.below(i), Blocks.WATER.defaultBlockState());
		h.getLevel().setBlockAndUpdate(top.below(4), Blocks.STONE.defaultBlockState());
		h.assertTrue(RatUtils.findLowestWater(top, rat).equals(top.below(3)), "Air above water descends to the last water cell");
		h.assertTrue(RatUtils.findLowestWater(top.below(), rat).equals(top.below(3)), "Starting in water has the same lowest valid destination");
		var dry = top.east(3);
		h.getLevel().setBlockAndUpdate(dry, Blocks.AIR.defaultBlockState());
		h.getLevel().setBlockAndUpdate(dry.below(), Blocks.STONE.defaultBlockState());
		h.assertTrue(RatUtils.findLowestWater(dry, rat).equals(dry), "Dry column preserves origin rather than returning a solid destination");
		h.succeed();
	}

	@GameTest(template = "quarry_test_empty")
	public static void piratCanChooseWaterAgain(GameTestHelper h) {
		for (int x = 6; x <= 26; x++) for (int z = 6; z <= 26; z++) {
			h.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
			for (int y = 3; y <= 5; y++) h.setBlock(new BlockPos(x, y, z), Blocks.WATER);
		}
		var pirat = RatlantisEntityRegistry.PIRAT.get().create(h.getLevel());
		pirat.setPos(h.absolutePos(new BlockPos(16, 4, 16)).getCenter());
		pirat.getRandom().setSeed(7);
		var goal = new PiratWanderGoal(pirat, 1, 1);
		boolean found = false;
		for (int i = 0; i < 32 && !found; i++) found = goal.canUse();
		h.assertTrue(found, "Registered aquatic wandering can select work in an actual pool; no permanently-null target");
		h.succeed();
	}

	@GameTest(template = "quarry_test_empty")
	public static void nullableSelectionUsesEmptySentinelAndRoundTrips(GameTestHelper h) {
		var cap = new SelectedRatCapability();
		cap.setSelectedRat(null); cap.clearSelectedRat();
		h.assertTrue(cap.getSelectedRat() == null, "Nullable selection is safe before attaching a host");
		var rat = rat(h);
		var selected = new UpdateSelectedRatPacket(rat, cap);
		h.assertTrue(selected.ratId() == -1, "Empty selection has an explicit wire sentinel");
		var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
		try {
			UpdateSelectedRatPacket.encode(selected, buffer);
			h.assertTrue(UpdateSelectedRatPacket.decode(buffer).equals(selected), "Empty selection survives wire round-trip");
		} finally { buffer.release(); }
		cap.setSelectedRat(rat);
		h.assertTrue(new UpdateSelectedRatPacket(rat, cap).ratId() == rat.getId(), "A real selection retains its entity ID");
		cap.clearSelectedRat();
		h.assertTrue(cap.getSelectedRat() == null, "Clear removes a previous real selection");
		h.succeed();
	}

	@GameTest(template = "quarry_test_empty")
	public static void mobFilterRejectsMalformedIdsAndPreservesValidNbt(GameTestHelper h) {
		var stack = new ItemStack(RatsItemRegistry.RAT_UPGRADE_MOB_FILTER.get());
		MobFilterUpgradeItem.setWhitelist(stack, true);
		MobFilterUpgradeItem.setMobs(stack, List.of("INVALID ID", "minecraft:cow", "missing:unregistered", "minecraft:cow", "minecraft:pig"));
		h.assertTrue(MobFilterUpgradeItem.getSelectedMobs(stack).equals(List.of("minecraft:cow", "minecraft:pig")), "Malformed/unregistered/duplicate IDs do not enter the filter or crash");
		var restored = ItemStack.of(stack.save(new CompoundTag()));
		h.assertTrue(MobFilterUpgradeItem.isWhitelist(restored) && MobFilterUpgradeItem.getSelectedMobs(restored).equals(List.of("minecraft:cow", "minecraft:pig")), "Valid filter and whitelist survive real item NBT round-trip");
		h.succeed();
	}
}
