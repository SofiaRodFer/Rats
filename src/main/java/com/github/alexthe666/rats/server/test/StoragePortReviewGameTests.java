package com.github.alexthe666.rats.server.test;

import com.github.alexthe666.rats.registry.*;
import com.github.alexthe666.rats.server.block.entity.RatStorageBlockEntity;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.misc.TubeTravel;
import com.github.alexthe666.rats.server.storage.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

/** Regression scenarios identified by independent transport/storage review. */
@GameTestHolder("rats")
@PrefixGameTestTemplate(false)
public class StoragePortReviewGameTests {
    @GameTest(template = "quarry_test_empty", timeoutTicks = 1400)
    public static void loadedWorkerUsesMovedChestPort(GameTestHelper h) {
        movedPort(h, false);
    }

    @GameTest(template = "quarry_test_empty", timeoutTicks = 1400)
    public static void oldTaskFaceSurvivesActualRatAndControllerReload(GameTestHelper h) {
        movedPort(h, true);
    }

    private static void movedPort(GameTestHelper h, boolean reload) {
        for (int x = 9; x <= 17; x++) for (int z = 9; z <= 12; z++) {
            h.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
            h.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
        }
        BlockPos controlPos = h.absolutePos(new BlockPos(10, 4, 10));
        h.setBlock(new BlockPos(10, 4, 10), RatsBlockRegistry.RAT_STORAGE_CONTROLLER.get());
        RatStorageBlockEntity initial = (RatStorageBlockEntity) h.getLevel().getBlockEntity(controlPos);
        for (int x = 11; x <= 14; x++) h.setBlock(new BlockPos(x, 4, 10), RatsBlockRegistry.RAT_TUBE_COLOR.get());
        h.setBlock(new BlockPos(15, 4, 10), Blocks.CHEST);
        BlockPos chest = h.absolutePos(new BlockPos(15, 4, 10));
        h.assertTrue(initial.connect(chest), "Original west chest port linked");
        h.setBlock(new BlockPos(10, 4, 9), RatsBlockRegistry.RAT_STORAGE_INPUT.get());
        RatStorageBlockEntity input = (RatStorageBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(10, 4, 9)));
        h.assertTrue(initial.connect(input.getBlockPos()), "Input linked");
        input.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        TamedRat[] worker = { h.spawn(RatsEntityRegistry.TAMED_RAT.get(), new BlockPos(10, 4, 10)) };
        worker[0].setTame(true);
        worker[0].setItemSlot(EquipmentSlot.CHEST, new ItemStack(RatsItemRegistry.RAT_UPGRADE_STORAGE_KEEPER.get()));
        worker[0].setPos(TubeTravel.point(h.getLevel(), controlPos));
        h.assertTrue(initial.assign(worker[0]), "Physical keeper assigned");
        boolean[] moved = { false };
        h.onEachTick(() -> {
            RatStorageBlockEntity current = (RatStorageBlockEntity) h.getLevel().getBlockEntity(controlPos);
            StorageTask task = current.task(worker[0]);
            if (!moved[0] && task != null && task.collected && worker[0].cargo.get(0).getCount() == 64) {
                h.assertTrue(task.destinationFace == Direction.WEST, "Reserved task really contains now-obsolete west face");
                moved[0] = true;
                // Old dock becomes solid; reaching it cannot accidentally make
                // the test pass through the chest's permissive all-face handler.
                h.setBlock(new BlockPos(14, 4, 10), Blocks.STONE);
                for (int x = 13; x <= 15; x++) h.setBlock(new BlockPos(x, 3, 10), RatsBlockRegistry.RAT_TUBE_COLOR.get());
                if (reload) {
                    CompoundTag controllerTag = current.saveWithFullMetadata();
                    CompoundTag ratTag = worker[0].saveWithoutId(new CompoundTag());
                    worker[0].discard();
                    RatStorageBlockEntity restored = new RatStorageBlockEntity(controlPos, current.getBlockState());
                    restored.load(controllerTag);
                    h.getLevel().setBlockEntity(restored);
                    TamedRat copy = RatsEntityRegistry.TAMED_RAT.get().create(h.getLevel());
                    copy.load(ratTag);
                    h.assertTrue(h.getLevel().addFreshEntity(copy), "Actual saved worker recreated");
                    worker[0] = copy;
                    StorageTask oldTask = restored.task(copy);
                    h.assertTrue(oldTask != null && oldTask.collected && oldTask.loaded == 64 && oldTask.destinationFace == Direction.WEST,
                            "Old task face and collected cargo state retained without recollecting");
                }
            }
        });
        h.succeedWhen(() -> {
            RatStorageBlockEntity current = (RatStorageBlockEntity) h.getLevel().getBlockEntity(controlPos);
            h.assertTrue(moved[0] && worker[0].isAlive() && worker[0].cargo.isEmpty() && current.task(worker[0]) == null
                            && input.isEmpty() && StorageInventory.count(StorageRoutes.handler(h.getLevel(), chest, Direction.DOWN), new ItemStack(Items.COBBLESTONE)) == 64,
                    "Exactly one physical parcel delivered through moved bottom port; reload=" + reload
                            + " rat=" + worker[0].position() + " status=" + worker[0].getPersistentData().getString("RatStorageStatus"));
            long loose = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(controlPos).inflate(10)).stream()
                    .filter(e -> e.getItem().is(Items.COBBLESTONE)).mapToLong(e -> e.getItem().getCount()).sum();
            h.assertTrue(loose == 0, "Port migration does not spill or duplicate cargo");
        });
    }

    @GameTest(template = "quarry_test_empty")
    public static void physicallyBlockedCoarseRouteHasBackoff(GameTestHelper h) {
        h.setBlock(new BlockPos(5, 3, 5), Blocks.STONE);
        h.setBlock(new BlockPos(6, 3, 5), Blocks.OAK_FENCE);
        h.setBlock(new BlockPos(7, 3, 5), Blocks.STONE);
        BlockPos origin = h.absolutePos(new BlockPos(5, 4, 5));
        BlockPos target = h.absolutePos(new BlockPos(7, 4, 5));
        h.assertTrue(!TubeTravel.route(h.getLevel(), origin, origin, target, 20).isEmpty(),
                "Fixture has a coarse route over support whose fence extends into rat space");
        TamedRat rat = h.spawn(RatsEntityRegistry.TAMED_RAT.get(), new BlockPos(5, 4, 5));
        rat.setCommand(com.github.alexthe666.rats.server.entity.rat.RatCommand.SIT);
        rat.setPos(TubeTravel.point(h.getLevel(), origin));
        TubeTravel travel = new TubeTravel();
        int blockedAt = -1;
        for (int tick = 0; tick < 30; tick++) {
            rat.tickCount = tick;
            travel.move(rat, origin, target, 20);
            if (travel.blocked()) { blockedAt = tick; break; }
        }
        h.assertTrue(blockedAt >= 0, "Real AABB rejects the fence even though coarse graph found a route");
        int searches = travel.searches();
        for (int tick = blockedAt + 1; tick < blockedAt + 35; tick++) {
            rat.tickCount = tick;
            travel.move(rat, origin, target, 20);
        }
        h.assertTrue(travel.searches() == searches, "Physical obstruction does not restart path searches every tick");
        h.setBlock(new BlockPos(6, 3, 5), Blocks.STONE);
        rat.tickCount = blockedAt + 40;
        travel.move(rat, origin, target, 20);
        h.assertTrue(travel.searches() == searches + 1 && !travel.blocked(), "Changing support restores route after bounded backoff");
        h.succeed();
    }
}
