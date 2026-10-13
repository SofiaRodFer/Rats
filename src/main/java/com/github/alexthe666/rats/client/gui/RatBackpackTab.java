package com.github.alexthe666.rats.client.gui;
import com.github.alexthe666.rats.server.compat.backpacks.RatBackpackContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.p3pp3rf1y.sophisticatedcore.client.gui.*;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.WidgetBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.*;
public final class RatBackpackTab extends UpgradeSettingsTab<RatBackpackContainer>{
    public RatBackpackTab(RatBackpackContainer container,Position position,StorageScreenBase<?> screen){super(container,position,screen,Component.translatable("storage.rats.backpack.title"),Component.translatable("storage.rats.backpack.tooltip"));addHideableChild(new WidgetBase(new Position(x+3,y+24),new Dimension(54,54)){
        @Override protected void renderBg(GuiGraphics g,Minecraft minecraft,int mx,int my){for(int i=0;i<9;i++)g.blit(new net.minecraft.resources.ResourceLocation("minecraft","textures/gui/container/inventory.png"),getX()+i%3*18,getY()+i/3*18,7,83,18,18);}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){ }
    });}
    @Override protected void moveSlotsToTab(){for(int i=0;i<getContainer().getSlots().size();i++){var slot=getContainer().getSlots().get(i);slot.x=x+4+i%3*18;slot.y=y+25+i/3*18;}}
}
