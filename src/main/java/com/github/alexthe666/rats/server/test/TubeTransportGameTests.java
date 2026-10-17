package com.github.alexthe666.rats.server.test;
import com.github.alexthe666.rats.registry.*;
import com.github.alexthe666.rats.server.misc.*;
import com.github.alexthe666.rats.server.entity.rat.*;
import com.github.alexthe666.rats.server.storage.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("rats") @PrefixGameTestTemplate(false)
public class TubeTransportGameTests{
	@GameTest(template="quarry_test_empty",timeoutTicks=1800) public static void transporterLeavesBrokenTubeAndReentersWithCargo(GameTestHelper h){
		for(int x=4;x<=15;x++)for(int z=6;z<=10;z++)h.setBlock(new BlockPos(x,3,z),Blocks.STONE);for(int x=5;x<=12;x++)h.setBlock(new BlockPos(x,4,8),RatsBlockRegistry.RAT_TUBE_COLOR.get());for(int y=5;y<=8;y++)h.setBlock(new BlockPos(12,y,8),RatsBlockRegistry.RAT_TUBE_COLOR.get());h.setBlock(new BlockPos(13,8,8),RatsBlockRegistry.RAT_STORAGE_INPUT.get());var target=h.absolutePos(new BlockPos(13,8,8));var rat=h.spawn(RatsEntityRegistry.TAMED_RAT.get(),new BlockPos(5,4,6));rat.setTame(true);rat.setItemSlot(EquipmentSlot.CHEST,new ItemStack(RatsItemRegistry.RAT_UPGRADE_PLATTER.get()));rat.cargo.set(0,new ItemStack(Items.DIAMOND,64));rat.setDepositPos(GlobalPos.of(h.getLevel().dimension(),target));rat.setCommand(RatCommand.TRANSPORT);boolean[] broken={false},outside={false},reentered={false};
		h.onEachTick(()->{if(!broken[0]&&rat.isInTube()&&rat.getX()>h.absolutePos(new BlockPos(6,4,8)).getX()){broken[0]=true;h.setBlock(new BlockPos(8,4,8),Blocks.AIR);h.setBlock(new BlockPos(9,4,8),Blocks.AIR);}if(broken[0]&&!rat.isInTube()&&!rat.cargo.isEmpty()&&rat.getX()>=h.absolutePos(new BlockPos(8,4,8)).getX())outside[0]=true;if(outside[0]&&rat.isInTube()&&rat.getX()>=h.absolutePos(new BlockPos(10,4,8)).getX())reentered[0]=true;});
		h.succeedWhen(()->h.assertTrue(broken[0]&&outside[0]&&reentered[0]&&StorageInventory.count(StorageRoutes.handler(h.getLevel(),target,Direction.WEST),new ItemStack(Items.DIAMOND))==64&&rat.cargo.isEmpty(),"TRANSPORT actor exits two-cell gap, reenters, climbs and delivers exact cargo: "+rat.position()+" "+rat.getWorkDebugState()));
	}
	@GameTest(template="quarry_test_empty") public static void impossibleRoutesHaveNegativeCacheAndRecover(GameTestHelper h){
		var origin=h.absolutePos(new BlockPos(5,4,5));h.setBlock(new BlockPos(5,4,5),RatsBlockRegistry.RAT_TUBE_COLOR.get());var target=h.absolutePos(new BlockPos(7,4,5));h.setBlock(new BlockPos(7,4,5),RatsBlockRegistry.RAT_TUBE_COLOR.get());var rat=h.spawn(RatsEntityRegistry.TAMED_RAT.get(),new BlockPos(5,4,5));rat.setCommand(RatCommand.SIT);var travel=new TubeTravel();for(int tick=0;tick<35;tick++){rat.tickCount=tick;travel.move(rat,origin,target,20);}h.assertTrue(travel.searches()==1,"Impossible path not searched every tick");h.setBlock(new BlockPos(6,4,5),RatsBlockRegistry.RAT_TUBE_COLOR.get());rat.tickCount=40;travel.move(rat,origin,target,20);h.assertTrue(travel.searches()==2&&!travel.blocked(),"Cached miss retries and sees repaired route");h.succeed();
	}
	@GameTest(template="quarry_test_empty") public static void collisionResetsOutsideAndDisconnectedNetworkIsWalkable(GameTestHelper h){
		for(int x=5;x<=14;x++){h.setBlock(new BlockPos(x,3,8),Blocks.STONE);h.setBlock(new BlockPos(x,4,8),RatsBlockRegistry.RAT_TUBE_COLOR.get());}var rat=h.spawn(RatsEntityRegistry.TAMED_RAT.get(),new BlockPos(5,4,8));rat.setTame(true);rat.setCommand(RatCommand.SIT);rat.aiStep();h.assertTrue(rat.noPhysics,"Rat has no collisions inside tube");rat.move(MoverType.SELF,new Vec3(.4,0,0));h.assertTrue(rat.getX()>h.absolutePos(new BlockPos(5,4,8)).getX()+.6,"Movement is not blocked by tube shell");
		h.setBlock(new BlockPos(9,4,8),Blocks.AIR);var origin=h.absolutePos(new BlockPos(5,4,8));var path=TubeTravel.route(h.getLevel(),origin,origin,h.absolutePos(new BlockPos(14,4,8)),20);h.assertTrue(path.contains(h.absolutePos(new BlockPos(9,4,8)))&&path.contains(h.absolutePos(new BlockPos(10,4,8))),"Broken sequence can be exited and re-entered over safe ground");
		rat.setPos(h.absolutePos(new BlockPos(9,4,8)).getCenter());rat.aiStep();h.assertTrue(!rat.noPhysics,"Collision immediately restored outside tube");for(int y=4;y<=8;y++)h.setBlock(new BlockPos(9,y,8),Blocks.STONE);h.assertTrue(TubeTravel.route(h.getLevel(),origin,origin,h.absolutePos(new BlockPos(14,4,8)),20).isEmpty(),"Cannot cross a solid obstruction when no safe detour exists");h.succeed();
	}
	@GameTest(template="quarry_test_empty",timeoutTicks=1600) public static void ordinaryTransportUsesVerticalTubesAndCanDeliverIntoInput(GameTestHelper h){
		for(int x=4;x<=17;x++)for(int z=6;z<=10;z++)h.setBlock(new BlockPos(x,3,z),Blocks.STONE);
		h.setBlock(new BlockPos(4,4,8),Blocks.CHEST);for(int x=5;x<=12;x++)h.setBlock(new BlockPos(x,4,8),RatsBlockRegistry.RAT_TUBE_COLOR.get());for(int y=5;y<=8;y++)h.setBlock(new BlockPos(12,y,8),RatsBlockRegistry.RAT_TUBE_COLOR.get());h.setBlock(new BlockPos(13,8,8),RatsBlockRegistry.RAT_STORAGE_INPUT.get());var from=h.absolutePos(new BlockPos(4,4,8));var to=h.absolutePos(new BlockPos(13,8,8));StorageInventory.insert(StorageRoutes.handler(h.getLevel(),from,Direction.EAST),new ItemStack(Items.DIAMOND,64),false);
		var rat=h.spawn(RatsEntityRegistry.TAMED_RAT.get(),new BlockPos(5,4,6));rat.setTame(true);rat.setItemSlot(EquipmentSlot.CHEST,new ItemStack(RatsItemRegistry.RAT_UPGRADE_PLATTER.get()));rat.setPickupPos(GlobalPos.of(h.getLevel().dimension(),from));rat.setDepositPos(GlobalPos.of(h.getLevel().dimension(),to));rat.setCommand(RatCommand.TRANSPORT);boolean[] used={false};h.onEachTick(()->{if(rat.isInTube()&&rat.getY()>from.getY()+2)used[0]=true;});h.succeedWhen(()->h.assertTrue(used[0]&&StorageInventory.count(StorageRoutes.handler(h.getLevel(),to,Direction.WEST),new ItemStack(Items.DIAMOND))==64&&rat.cargo.isEmpty(),"Ordinary rat physically delivers full stack up tube into storage input: "+rat.position()+" "+rat.getWorkDebugState()));
	}
}
