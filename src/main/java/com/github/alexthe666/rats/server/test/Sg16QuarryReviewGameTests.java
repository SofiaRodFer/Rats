package com.github.alexthe666.rats.server.test;

import com.github.alexthe666.rats.registry.RatsBlockRegistry;
import com.github.alexthe666.rats.registry.RatsEntityRegistry;
import com.github.alexthe666.rats.server.block.entity.RatQuarryBlockEntity;
import com.github.alexthe666.rats.server.misc.QuarrySafety;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("rats") @PrefixGameTestTemplate(false)
public class Sg16QuarryReviewGameTests {
    @GameTest(template="quarry_test_empty")
    public static void historicalOreAuditFindsOldWallsAndSharesClaims(GameTestHelper h) {
        var pos=new BlockPos(16,4,20); h.setBlock(pos,RatsBlockRegistry.RAT_QUARRY.get());
        var q=(RatQuarryBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pos)); q.configure(1);
        var saved=q.saveWithoutMetadata(); saved.putIntArray("QuarryProgress",new int[]{0,12,0,0,0}); q.load(saved);
        var a=h.spawn(RatsEntityRegistry.TAMED_RAT.get(),pos.above()); a.setTame(true); a.setNoAi(true);
        var b=h.spawn(RatsEntityRegistry.TAMED_RAT.get(),pos.above()); b.setTame(true); b.setNoAi(true);
        var ore=q.cell(0,-1,1); h.getLevel().setBlockAndUpdate(ore,Blocks.IRON_ORE.defaultBlockState());
        q.discoverHistoricalOre(a); q.discoverHistoricalOre(b);
        h.assertTrue(a.oreVein.targets(q.getBlockPos()).contains(ore),"Audit discovers ore on historical slice, not only current frontier");
        h.assertTrue(!q.canClaimOre(ore,b.getUUID()),"Historical veins use existing shared leases"); h.succeed();
    }
    @GameTest(template="quarry_test_empty")
    public static void torchUsesSingleSupportedWallWithoutRoof(GameTestHelper h) {
        var pos=new BlockPos(16,4,20); h.setBlock(pos,RatsBlockRegistry.RAT_QUARRY.get());
        var q=(RatQuarryBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pos)); q.configure(1);
        var r=h.spawn(RatsEntityRegistry.TAMED_RAT.get(),pos.above()); r.setTame(true); r.setNoAi(true);
        h.getLevel().setBlockAndUpdate(q.cell(4,-1,1),Blocks.STONE.defaultBlockState());
        QuarrySafety.placeQuarryLights(q,r,4);
        h.assertTrue(h.getLevel().getBlockState(q.cell(4,0,1)).is(RatsBlockRegistry.RAT_QUARRY_TORCH.get()),"Available wall receives light without opposite wall or roof");
        h.assertTrue(h.getLevel().isEmptyBlock(q.cell(4,q.getWidth()-1,1)),"Unsupported opposite side stays empty"); h.succeed();
    }
}
