package com.github.alexthe666.rats.client.render;

import com.github.alexthe666.rats.registry.RatsItemRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="rats",value=Dist.CLIENT)
public final class StorageOutlineRenderer {
	private static CompoundTag selection=new CompoundTag();
	private static Object level;
	public static void accept(CompoundTag tag){selection=tag;level=Minecraft.getInstance().level;}
	private static ItemStack staff(){var p=Minecraft.getInstance().player;if(p==null)return ItemStack.EMPTY;return p.getMainHandItem().is(RatsItemRegistry.CHEESE_STORAGE_STAFF.get())?p.getMainHandItem():p.getOffhandItem().is(RatsItemRegistry.CHEESE_STORAGE_STAFF.get())?p.getOffhandItem():ItemStack.EMPTY;}
	@SubscribeEvent public static void render(RenderLevelStageEvent event){
		if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
		var mc=Minecraft.getInstance();var held=staff();var tag=held.getTag();
		if(mc.level==null||mc.level!=level||tag==null||!tag.hasUUID("Network")||!selection.hasUUID("Network")||!tag.getUUID("Network").equals(selection.getUUID("Network"))||!mc.level.dimension().location().toString().equals(selection.getString("Dimension")))return;
		var pose=event.getPoseStack();var camera=event.getCamera().getPosition();pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
		var buffers=mc.renderBuffers().bufferSource();var lines=buffers.getBuffer(StorageOutlineRenderType.TYPE);
		BlockPos controller=BlockPos.of(selection.getLong("Controller"));LevelRenderer.renderLineBox(pose,lines,new AABB(controller).inflate(.015),.2F,1F,.3F,1F);
		for(long value:selection.getLongArray("Endpoints"))LevelRenderer.renderLineBox(pose,lines,new AABB(BlockPos.of(value)).inflate(.015),.2F,.8F,1F,1F);
		for(int id:selection.getIntArray("Workers")){var rat=mc.level.getEntity(id);if(rat!=null)LevelRenderer.renderLineBox(pose,lines,rat.getBoundingBox().inflate(.035),1F,.85F,.2F,1F);}
		// A single line per tube edge avoids twelve boxes' edges per tube and
		// still makes the entire connected network legible through terrain.
		var edges=selection.getLongArray("TubeEdges");for(int i=0;i+1<edges.length;i+=2){var a=BlockPos.of(edges[i]).getCenter();var b=BlockPos.of(edges[i+1]).getCenter();lines.vertex(pose.last().pose(),(float)a.x,(float)a.y,(float)a.z).color(.3F,.75F,1F,.5F).normal(pose.last().normal(),0,1,0).endVertex();lines.vertex(pose.last().pose(),(float)b.x,(float)b.y,(float)b.z).color(.3F,.75F,1F,.5F).normal(pose.last().normal(),0,1,0).endVertex();}
		LevelRenderer.renderLineBox(pose,lines,new AABB(controller).inflate(20),.5F,1F,.3F,.35F);
		buffers.endBatch(StorageOutlineRenderType.TYPE);pose.popPose();
	}
}
