package com.github.alexthe666.rats.client.gui;
import net.minecraft.client.gui.GuiGraphics;
public final class VanillaRatGui{
	private VanillaRatGui(){}
	public static void panel(GuiGraphics g,int x,int y,int w,int h){g.fill(x,y,x+w,y+h,0xFFC6C6C6);g.fill(x,y,x+w,y+2,0xFFFFFFFF);g.fill(x,y,x+2,y+h,0xFFFFFFFF);g.fill(x+2,y+h-2,x+w,y+h,0xFF555555);g.fill(x+w-2,y+2,x+w,y+h,0xFF555555);}
}
