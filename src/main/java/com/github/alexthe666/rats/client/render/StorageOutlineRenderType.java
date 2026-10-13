package com.github.alexthe666.rats.client.render;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderType;
import java.util.OptionalDouble;

/** Explicit render state: selection stays visible through blocks, then restores depth. */
final class StorageOutlineRenderType extends RenderType {
	private StorageOutlineRenderType(){super("rats_storage_outline",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,256,false,false,()->{},()->{});}
	static final RenderType TYPE=create("rats_storage_outline",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,256,false,false,CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER).setLineState(new LineStateShard(OptionalDouble.of(2))).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).createCompositeState(false));
}
