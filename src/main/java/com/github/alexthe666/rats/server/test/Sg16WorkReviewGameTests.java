package com.github.alexthe666.rats.server.test;

import com.github.alexthe666.rats.registry.*;
import com.github.alexthe666.rats.server.block.entity.RatFarmingBlockEntity;
import com.github.alexthe666.rats.server.entity.ai.goal.harvest.RatFishermanGoal;
import com.github.alexthe666.rats.server.entity.rat.*;
import com.github.alexthe666.rats.server.inventory.RatFarmingMenu;
import com.github.alexthe666.rats.server.misc.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("rats") @PrefixGameTestTemplate(false)
public class Sg16WorkReviewGameTests {
    @GameTest(template="quarry_test_empty")
    public static void legacyLayoutSurvivesRepeatedSaveAndMatchesMenu(GameTestHelper h){
        var pos=h.absolutePos(new BlockPos(8,4,8));
        h.getLevel().setBlockAndUpdate(pos,RatsBlockRegistry.RAT_FARMING.get().defaultBlockState());
        var farm=(RatFarmingBlockEntity)h.getLevel().getBlockEntity(pos);
        var tag=farm.saveWithoutMetadata();tag.putInt("FarmWidth",5);tag.putInt("FarmHeight",7);tag.putInt("FarmLayoutVersion",2);farm.load(tag);
        var original=(java.util.BitSet)farm.layout().water.clone();
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"LegacyFarm"));
        for(int i=0;i<3;i++){
            farm.load(farm.saveWithoutMetadata());
            h.assertTrue(farm.layout().legacy&&farm.layout().water.equals(original),"Legacy source map survives repeated save "+i);
            var menu=new RatFarmingMenu(900+i,p.getInventory(),farm);
            h.assertTrue(menu.layout().legacy&&menu.layout().water.equals(original),"Menu uses the same legacy source map");
        }
        farm.configure(6,7);
        h.assertTrue(!farm.layout().legacy&&farm.layout().width==6&&farm.layout().height==6,"Explicit configuration migrates to square");
        h.succeed();
    }
    @GameTest(template="quarry_test_empty",timeoutTicks=80)
    public static void partialSourceGroupsIlluminateDarkFarmCorners(GameTestHelper h){
        int size=14;
        for(int x=3;x<=size+4;x++)for(int z=3;z<=size+4;z++)for(int y=3;y<=7;y++)
            if(y==3||y==7||x==3||x==size+4||z==3||z==size+4)h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        var layout=new FarmLayout(size,size);
        for(int cell=layout.water.nextSetBit(0);cell>=0;cell=layout.water.nextSetBit(cell+1))
            h.setBlock(new BlockPos(4+cell%16,3,4+cell/16),RatsBlockRegistry.RAT_FARM_LIGHT.get());
        h.runAfterDelay(20,()->{
            for(int cell=0;cell<256;cell++)if(layout.cropCell(cell)){
                BlockPos crop=h.absolutePos(new BlockPos(4+cell%16,4,4+cell/16));
                h.assertTrue(h.getLevel().getBrightness(LightLayer.BLOCK,crop)>=9,"Dark farm corner has growth light at "+cell);
            }
            h.succeed();
        });
    }
    @GameTest(template="quarry_test_empty",timeoutTicks=100)
    public static void fishermenWithPartialCargoCanBeginAnotherCast(GameTestHelper h){
        h.setBlock(new BlockPos(8,3,8),Blocks.STONE);h.setBlock(new BlockPos(9,3,8),Blocks.WATER);
        var r=h.spawn(RatsEntityRegistry.TAMED_RAT.get(),new BlockPos(8,4,8));r.setTame(true);r.setNoAi(true);
        r.setItemSlot(EquipmentSlot.CHEST,new ItemStack(RatsItemRegistry.RAT_UPGRADE_FISHERMAN.get()));
        r.setItemSlot(EquipmentSlot.LEGS,new ItemStack(RatsItemRegistry.RAT_UPGRADE_SILVER_TRAY.get()));
        r.cargo.insert(new ItemStack(Items.COD),false);r.setCommand(RatCommand.HARVEST);
        var goal=new RatFishermanGoal(r);
        h.assertTrue(goal.canUse(),"Partial cargo allows another cast without a deposit");
        goal.start();
        for(int i=0;i<1000&&goal.canContinueToUse();i++)goal.tick();
        int count=java.util.stream.IntStream.range(0,r.cargo.slots()).map(i->r.cargo.get(i).getCount()).sum();
        h.assertTrue(count>1,"Another cast actually produces additional loot with cargo already held");
        var fake=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        h.assertTrue(fake.fishing==null,"Temporary hook does not remain on shared fake player");
        r.setCommand(RatCommand.SIT);h.succeed();
    }
    @GameTest(template="quarry_test_empty")
    public static void brokenWorkstationReleasesDesiredWorkerChunks(GameTestHelper h){
        var pos=h.absolutePos(new BlockPos(8,4,8));
        h.getLevel().setBlockAndUpdate(pos,RatsBlockRegistry.RAT_QUARRY.get().defaultBlockState());
        var r=h.spawn(RatsEntityRegistry.TAMED_RAT.get(),new BlockPos(9,4,8));r.setTame(true);r.setNoAi(true);
        r.setItemSlot(EquipmentSlot.CHEST,new ItemStack(RatsItemRegistry.RAT_UPGRADE_QUARRY.get()));r.setCommand(RatCommand.HARVEST);
        r.setDepositPos(GlobalPos.of(h.getLevel().dimension(),pos));
        h.assertTrue(!RatWorkChunks.desired(r).isEmpty(),"Valid quarry worker keeps chunks");
        h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        h.assertTrue(RatWorkChunks.desired(r).isEmpty(),"Broken workstation does not retain ticking chunks");
        h.succeed();
    }
}
