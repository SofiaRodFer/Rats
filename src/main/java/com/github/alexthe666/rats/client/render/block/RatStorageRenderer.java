package com.github.alexthe666.rats.client.render.block;
import com.github.alexthe666.rats.server.block.entity.RatStorageBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Attached plaque, no world block replacement and no survival item/drop. */
public class RatStorageRenderer implements BlockEntityRenderer<RatStorageBlockEntity> {
	private static final ResourceLocation CHEESE = new ResourceLocation("rats", "textures/block/block_of_blue_cheese.png");
	public RatStorageRenderer(BlockEntityRendererProvider.Context context) { }
	@Override public boolean shouldRenderOffScreen(RatStorageBlockEntity entity) { return true; }
	@Override public int getViewDistance() { return 64; }
	@Override public void render(RatStorageBlockEntity c, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		if (c.getLevel() == null) return;
		for (var entry : c.categories.entrySet()) {
			var pos = entry.getKey(); if (!c.getLevel().hasChunkAt(pos) || c.getLevel().getBlockEntity(pos) == null) continue;
			var state = c.getLevel().getBlockState(pos); Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
			pose.pushPose(); pose.translate(pos.getX() - c.getBlockPos().getX() + 0.5, pos.getY() - c.getBlockPos().getY() + 0.5, pos.getZ() - c.getBlockPos().getZ() + 0.5); pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
			var matrix = pose.last(); var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(CHEESE)); int localLight = LevelRenderer.getLightColor(c.getLevel(), pos);
			vertices.vertex(matrix.pose(), -0.42F,-0.12F,0.503F).color(255,255,255,255).uv(0,1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(localLight).normal(matrix.normal(),0,0,1).endVertex();
			vertices.vertex(matrix.pose(), 0.42F,-0.12F,0.503F).color(255,255,255,255).uv(1,1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(localLight).normal(matrix.normal(),0,0,1).endVertex();
			vertices.vertex(matrix.pose(), 0.42F,0.12F,0.503F).color(255,255,255,255).uv(1,0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(localLight).normal(matrix.normal(),0,0,1).endVertex();
			vertices.vertex(matrix.pose(), -0.42F,0.12F,0.503F).color(255,255,255,255).uv(0,0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(localLight).normal(matrix.normal(),0,0,1).endVertex();
			var font = Minecraft.getInstance().font; String text = font.plainSubstrByWidth(Component.translatable("storage.rats.category." + entry.getValue()).getString(), 96); pose.translate(0,0,0.507); pose.scale(0.008F,-0.008F,0.008F); font.drawInBatch(text,-font.width(text)/2F,-4,0xFF182A32,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,localLight); pose.popPose();
		}
	}
}
