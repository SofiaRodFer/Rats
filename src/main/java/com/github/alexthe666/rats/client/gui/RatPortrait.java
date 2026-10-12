package com.github.alexthe666.rats.client.gui;

import com.github.alexthe666.rats.client.util.EntityRenderingUtil;
import com.github.alexthe666.rats.registry.RatsEntityRegistry;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.misc.RatVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Screen-local preview entity, never spawned or ticked in a player world. */
public final class RatPortrait {
	private TamedRat preview;
	public void draw(GuiGraphics graphics, int x, int y, int scale, String variant, boolean baby) {
		var level = Minecraft.getInstance().level;
		if (level == null) return;
		if (this.preview == null || this.preview.level() != level) this.preview = RatsEntityRegistry.TAMED_RAT.get().create(level);
		if (this.preview == null) return;
		this.preview.setColorVariant(RatVariant.getVariant(variant)); this.preview.setAge(baby ? -24000 : 0);
		EntityRenderingUtil.drawEntityOnScreen(graphics, x, y, scale, 12, 0, this.preview, false);
	}
}
