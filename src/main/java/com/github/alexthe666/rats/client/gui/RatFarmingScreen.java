package com.github.alexthe666.rats.client.gui;
import com.github.alexthe666.rats.server.inventory.RatFarmingMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.nbt.Tag;
import java.util.*;

public class RatFarmingScreen extends AbstractContainerScreen<RatFarmingMenu>{
	private static final ResourceLocation INV=new ResourceLocation("minecraft","textures/gui/container/inventory.png");
	private int ox,oz,lastCell=-1;private final List<AbstractWidget> planControls=new ArrayList<>();
	public RatFarmingScreen(RatFarmingMenu m,Inventory i,Component t){super(m,i,t);this.imageWidth=310;this.imageHeight=240;}
	private void send(int action){this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,action);}
	private Button button(String key,int x,int y,int w,Runnable action){return this.addRenderableWidget(Button.builder(Component.translatable(key),b->action.run()).bounds(this.leftPos+x,this.topPos+y,w,18).build());}
	@Override protected void init(){super.init();this.planControls.clear();this.button("gui.rats.farm.storage",8,17,80,()->tab(0));this.button("gui.rats.farm.plan",92,17,102,()->tab(1));this.button("quarry.rats.workers",198,17,104,()->tab(2));
		for(int i=0;i<2;i++){final int a=i;this.planControls.add(this.button(i==0?"−":"+",184+i*60,53,56,()->send(a)));}
		this.planControls.add(this.button("gui.rats.farm.clear_plan",184,73,118,()->send(15)));
		for(int i=0;i<4;i++){final int a=i;this.planControls.add(this.button("gui.rats.farm.mode."+i,184+i%2*60,95+i/2*20,56,()->send(10+a)));}
		this.planControls.add(this.button("gui.rats.farm.rotate",184,161,118,()->send(4)));this.tab(this.menu.page);
	}
	private void tab(int page){this.menu.page=page;this.menu.layoutView=page!=0;this.planControls.forEach(w->w.visible=page!=2);this.send(20+page);}
	@Override protected void renderBg(GuiGraphics g,float p,int mx,int my){VanillaRatGui.panel(g,this.leftPos,this.topPos,this.imageWidth,this.imageHeight);for(var s:this.menu.slots)if(s.isActive())g.blit(INV,this.leftPos+s.x-1,this.topPos+s.y-1,7,83,18,18);
		this.ox=Math.max(0,Math.min(Math.max(0,this.menu.layout().width-9),this.ox));this.oz=Math.max(0,Math.min(Math.max(0,this.menu.layout().height-6),this.oz));
		if(this.menu.page==1){var l=this.menu.layout();for(int z=0;z<6;z++)for(int x=0;x<9;x++){int xx=x+this.ox,zz=z+this.oz;if(xx>=l.width||zz>=l.height)continue;int cell=zz*16+xx,px=this.leftPos+8+x*18,py=this.topPos+38+z*18;g.blit(INV,px-1,py-1,7,83,18,18);if(l.water.get(cell)){g.renderItem(new ItemStack(com.github.alexthe666.rats.registry.RatsBlockRegistry.RAT_FARM_LIGHT.get()),px,py);g.fill(px+12,py+12,px+16,py+16,0xFFF9CD58);}else{var seed=this.menu.ghosts.getItem(cell);if(!seed.isEmpty())g.renderItem(seed,px,py);}}var seeds=this.menu.snapshot.getList("Templates",Tag.TAG_COMPOUND);int count=this.menu.data.get(4)==1?1:this.menu.data.get(4)==2?2:this.menu.data.get(4)==3?4:0;for(int i=0;i<count;i++){int x=this.leftPos+184+i*26,y=this.topPos+139;g.blit(INV,x-1,y-1,7,83,18,18);if(i<seeds.size())g.renderItem(ItemStack.of(seeds.getCompound(i)),x,y);}}
		if(this.menu.page==2){var rats=this.menu.snapshot.getList("Workers",Tag.TAG_COMPOUND);for(int i=0;i<rats.size();i++){var r=rats.getCompound(i);int y=this.topPos+45+i*45;var e=this.minecraft.level.getEntity(r.getInt("Entity"));if(e instanceof net.minecraft.world.entity.LivingEntity living)InventoryScreen.renderEntityInInventoryFollowsMouse(g,this.leftPos+25,y+32,24,0,0,living);g.drawString(this.font,this.font.plainSubstrByWidth(r.getString("Name"),230),this.leftPos+50,y+8,0x404040,false);g.drawString(this.font,Component.translatable(r.getBoolean("Loaded")?"quarry.rats.assigned_worker":"quarry.rats.unloaded"),this.leftPos+50,y+22,0x404040,false);}}
	}
	@Override protected void renderLabels(GuiGraphics g,int mx,int my){g.drawString(this.font,this.font.plainSubstrByWidth(this.title.getString(),294),8,5,0x404040,false);g.drawString(this.font,this.playerInventoryTitle,8,154,0x404040,false);if(this.menu.page!=2){g.drawString(this.font,Component.translatable("gui.rats.farm.size",this.menu.data.get(0),this.menu.data.get(0)),184,40,0x404040,false);if(this.menu.snapshot.getBoolean("MissingDirt"))g.drawString(this.font,Component.translatable("gui.rats.farm.missing_dirt"),184,207,0x9B702F,false);if(this.menu.page==1)g.drawString(this.font,(this.ox+1)+","+(this.oz+1),8,147,0x404040,false);}else g.drawString(this.font,Component.translatable("gui.rats.farm.workers",this.menu.data.get(3)),8,137,0x404040,false);}
	private boolean paint(double x,double y,int button){if(this.menu.page!=1)return false;int col=(int)Math.floor((x-this.leftPos-8)/18),row=(int)Math.floor((y-this.topPos-38)/18);if(col<0||col>=9||row<0||row>=6)return false;int cell=(row+this.oz)*16+col+this.ox;if(!this.menu.layout().cropCell(cell))return true;if(cell!=this.lastCell){this.lastCell=cell;this.minecraft.gameMode.handleInventoryMouseClick(this.menu.containerId,90+cell,button,ClickType.PICKUP,this.minecraft.player);}return true;}
	@Override public boolean mouseClicked(double x,double y,int b){this.lastCell=-1;if(this.menu.page==1&&y>=this.topPos+139&&y<this.topPos+155&&x>=this.leftPos+184&&x<this.leftPos+288&&(b==0||b==1)){int slot=(int)(x-this.leftPos-184)/26;int count=this.menu.data.get(4)==1?1:this.menu.data.get(4)==2?2:this.menu.data.get(4)==3?4:0;if(slot<count){this.minecraft.gameMode.handleInventoryMouseClick(this.menu.containerId,346+slot,b,ClickType.PICKUP,this.minecraft.player);return true;}}if((b==0||b==1)&&paint(x,y,b))return true;return super.mouseClicked(x,y,b);}
	@Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){if((b==0||b==1)&&paint(x,y,b))return true;return super.mouseDragged(x,y,b,dx,dy);}
	@Override public boolean mouseScrolled(double x,double y,double delta){if(this.menu.page==1){var l=this.menu.layout();if(hasShiftDown())this.ox=Math.max(0,Math.min(Math.max(0,l.width-9),this.ox-(int)Math.signum(delta)));else this.oz=Math.max(0,Math.min(Math.max(0,l.height-6),this.oz-(int)Math.signum(delta)));return true;}return super.mouseScrolled(x,y,delta);}
	@Override public void render(GuiGraphics g,int x,int y,float p){this.renderBackground(g);super.render(g,x,y,p);this.renderTooltip(g,x,y);if(this.menu.page==1)g.drawString(this.font,Component.translatable("gui.rats.farm.direction",net.minecraft.core.Direction.from2DDataValue(this.menu.data.get(2)).getName()),this.leftPos+184,this.topPos+182,0x404040,false);}
}
