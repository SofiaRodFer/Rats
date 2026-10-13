package com.github.alexthe666.rats.client.gui;

import com.github.alexthe666.rats.server.inventory.RatStorageMenu;
import com.github.alexthe666.rats.server.message.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Vanilla materials and cached catalog: no NBT decoding/sorting per frame. */
public class RatStorageScreen extends AbstractContainerScreen<RatStorageMenu> {
	private static final ResourceLocation INVENTORY=new ResourceLocation("minecraft","textures/gui/container/inventory.png"), CRAFTING=new ResourceLocation("minecraft","textures/gui/container/crafting_table.png");
	private EditBox search,amount;
	private int offset,selected=-1;
	private boolean queueView;
	private CompoundTag cachedSnapshot;
	private String cachedQuery="";
	private List<Icon> icons=List.of(),filtered=List.of();
	private java.text.Collator nameOrder=java.text.Collator.getInstance(Locale.ROOT);
	private final List<AbstractWidget> mainWidgets=new ArrayList<>(),ratWidgets=new ArrayList<>();
	private int columns(){return this.menu.compact?9:12;}
	private int side(){return this.menu.compact?174:242;}
	private record Icon(int id,ItemStack item,String name,String registry,long available,long reserved,long transit,long input){}
	public RatStorageScreen(RatStorageMenu menu,Inventory inventory,Component title){super(menu,inventory,title);this.imageWidth=menu.controller?(menu.compact?310:394):176;this.imageHeight=menu.controller?240:236;}
	private void action(int action,int index,int count){RatsNetworkHandler.CHANNEL.sendToServer(new StorageActionPacket(this.menu.containerId,action,index,count));}
	private Button button(String key,int x,int y,int width,Runnable callback){return this.addRenderableWidget(Button.builder(Component.translatable("storage.rats."+key),b->callback.run()).bounds(this.leftPos+x,this.topPos+y,width,18).build());}
	@Override protected void init(){
		super.init();this.mainWidgets.clear();this.ratWidgets.clear();if(!this.menu.controller)return;
		this.nameOrder=java.text.Collator.getInstance(Locale.forLanguageTag(this.minecraft.options.languageCode.replace('_','-')));this.nameOrder.setStrength(java.text.Collator.PRIMARY);this.cachedSnapshot=null;
		this.button("storage_crafting",8,17,this.menu.compact?148:180,()->this.tab(0));this.button("rats",this.menu.compact?160:192,17,this.menu.compact?60:88,()->this.tab(1));this.button("system",this.menu.compact?224:284,17,this.menu.compact?78:102,()->this.tab(2));
		this.search=new EditBox(this.font,this.leftPos+8,this.topPos+37,this.columns()*18-2,16,Component.translatable("storage.rats.search"));this.search.setHint(Component.translatable("storage.rats.search"));this.search.setMaxLength(80);this.search.setResponder(s->this.offset=0);this.addRenderableWidget(this.search);this.mainWidgets.add(this.search);
		this.amount=new EditBox(this.font,this.leftPos+8,this.topPos+146,55,16,Component.translatable("storage.rats.amount"));this.amount.setMaxLength(6);this.amount.setFilter(s->s.matches("[0-9]*"));this.amount.setValue("64");this.addRenderableWidget(this.amount);this.mainWidgets.add(this.amount);
		this.mainWidgets.add(this.button("request",68,145,this.columns()*18-60,()->{try{this.action(0,this.selected,Integer.parseInt(this.amount.getValue()));}catch(NumberFormatException ignored){}}));
		this.mainWidgets.add(this.button("output",this.menu.compact?174:242,145,this.menu.compact?62:144,()->this.action(5,0,0)));
		for(int row=0;row<4;row++){final int cell=row;this.ratWidgets.add(this.button("remove",this.imageWidth-88,47+row*27,80,()->{var rats=this.menu.snapshot.getList("Workers",Tag.TAG_COMPOUND);int i=this.offset+cell;if(i<rats.size())this.action(1,rats.getCompound(i).getInt("Index"),0);}));}
		this.tab(this.menu.page);
	}
	private void tab(int page){this.menu.page=page;this.offset=0;this.mainWidgets.forEach(w->w.visible=page==0);this.ratWidgets.forEach(w->w.visible=page==1);this.action(2,page,0);}
	private List<Icon> stock(){
		String query=this.search==null?"":this.search.getValue().toLowerCase(Locale.ROOT);boolean changed=this.cachedSnapshot!=this.menu.snapshot;
		if(changed){var list=new ArrayList<Icon>();for(Tag value:this.menu.snapshot.getList("Stock",Tag.TAG_COMPOUND)){var t=(CompoundTag)value;var item=ItemStack.of(t.getCompound("Item"));list.add(new Icon(t.getInt("Index"),item,item.getHoverName().getString(),net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem()).toString(),t.getLong("Available"),t.getLong("Reserved"),t.getLong("Transit"),t.getLong("Input")));}list.sort(Comparator.comparing(Icon::name,this.nameOrder).thenComparingInt(Icon::id));this.icons=list;this.cachedSnapshot=this.menu.snapshot;}
		if(changed||!query.equals(this.cachedQuery)){this.filtered=this.icons.stream().filter(i->query.startsWith("@")?i.registry.startsWith(query.substring(1)+":"):i.name.toLowerCase(Locale.ROOT).contains(query)).toList();this.cachedQuery=query;}return this.filtered;
	}
	private void slot(GuiGraphics g,int x,int y){g.blit(INVENTORY,x-1,y-1,7,83,18,18);}
	@Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
		int x=this.leftPos,y=this.topPos,w=this.imageWidth,h=this.imageHeight;g.fill(x,y,x+w,y+h,0xFFC6C6C6);g.fill(x,y,x+w,y+2,0xFFFFFFFF);g.fill(x,y,x+2,y+h,0xFFFFFFFF);g.fill(x+2,y+h-2,x+w,y+h,0xFF555555);g.fill(x+w-2,y+2,x+w,y+h,0xFF555555);
		for(var s:this.menu.slots)if(s.isActive())this.slot(g,x+s.x,y+s.y);if(!this.menu.controller)return;
		if(this.menu.page==0){g.blit(CRAFTING,x+this.side()+59,y+81,89,34,24,17);g.blit(CRAFTING,x+this.side()+89,y+77,119,30,26,26);var list=this.stock();this.offset=Math.min(this.offset,Math.max(0,(list.size()-1)/this.columns()-4));for(int i=0;i<this.columns()*5;i++){int sx=x+8+i%this.columns()*18,sy=y+55+i/this.columns()*18,index=this.offset*this.columns()+i;this.slot(g,sx,sy);if(index>=list.size())continue;var icon=list.get(index);if(icon.id==this.selected)g.fill(sx,sy,sx+16,sy+16,0x806CB9E8);g.renderItem(icon.item,sx,sy);g.pose().pushPose();g.pose().translate(sx+16,sy+11,200);g.pose().scale(.6F,.6F,1);String count=compact(icon.available);g.drawString(this.font,count,-this.font.width(count),0,0xFFFFFFFF,true);g.pose().popPose();}}
		else if(this.menu.page==1){var rats=this.menu.snapshot.getList("Workers",Tag.TAG_COMPOUND);for(int i=0;i<4;i++){var button=this.ratWidgets.get(i);button.visible=this.offset+i<rats.size();if(!button.visible)continue;var t=rats.getCompound(this.offset+i);int sy=y+45+i*27;g.fill(x+8,sy,x+w-94,sy+25,0xFFB0B0B0);var entity=this.minecraft.level.getEntity(t.getInt("Entity"));if(entity instanceof LivingEntity living)InventoryScreen.renderEntityInInventoryFollowsMouse(g,x+24,sy+23,24,0,0,living);g.drawString(this.font,this.font.plainSubstrByWidth(t.getString("Name"),w-140),x+45,sy+3,0xFF333333,false);g.drawString(this.font,this.font.plainSubstrByWidth(Component.translatable("storage.rats.state."+t.getString("Status")).getString(),w-140),x+45,sy+14,0xFF444444,false);}}
		else{g.drawString(this.font,Component.translatable("storage.rats.nodes_queue"),x+190,y+43,0xFF333333,false);var nodes=this.menu.snapshot.getList(this.queueView?"Queue":"Endpoints",Tag.TAG_COMPOUND);for(int i=0;i<Math.min(7,nodes.size()-this.offset);i++){var t=nodes.getCompound(this.offset+i);var p=BlockPos.of(t.getLong("Pos"));String line=this.queueView?t.getString("Name")+" "+t.getInt("Delivered")+"/"+t.getInt("Amount"):p.getX()+","+p.getY()+","+p.getZ()+" "+Component.translatable("storage.rats.category."+t.getString("Category")).getString();g.drawString(this.font,this.font.plainSubstrByWidth(line,w-198),x+190,y+59+i*13,this.queueView||t.getBoolean("Route")?0xFF334433:0xFFAA3333,false);}g.drawString(this.font,this.font.plainSubstrByWidth(Component.translatable("storage.rats.organize").getString(),w-198),x+190,y+151,0xFF333333,false);}
	}
	private static String compact(long n){return n>=1000000?String.format(Locale.ROOT,"%.1fM",n/1000000D):n>=1000?String.format(Locale.ROOT,"%.1fk",n/1000D):Long.toString(n);}
	@Override protected void renderLabels(GuiGraphics g,int mx,int my){
		g.drawString(this.font,this.title,8,4,0x404040,false);if(!this.menu.controller)return;
		if(this.menu.page==0){g.drawString(this.font,Component.translatable("container.crafting"),this.side(),40,0x404040,false);if(this.menu.hasLegacyReceipt())g.drawString(this.font,Component.translatable(this.menu.compact?"storage.rats.receiving_short":"storage.rats.receiving"),this.menu.compact?246:222,this.menu.compact?106:118,0x404040,false);else if(this.menu.snapshot.contains("OutputPos")){var pos=BlockPos.of(this.menu.snapshot.getLong("OutputPos"));g.drawString(this.font,pos.getX()+", "+pos.getY()+", "+pos.getZ(),this.side(),129,0x404040,false);}}
		else if(this.menu.page==2)g.drawString(this.font,Component.translatable("storage.rats.recovery"),8,43,0x404040,false);
		g.drawString(this.font,Component.translatable("container.inventory"),this.menu.compact?74:112,154,0x404040,false);String message=this.menu.snapshot.getString("Message");if(!message.isEmpty())g.drawString(this.font,this.font.plainSubstrByWidth(Component.translatable("storage.rats."+message).getString(),66),4,166,0x404040,false);
	}
	private int hovered(int x,int y){return x>=8&&x<8+this.columns()*18&&y>=55&&y<145?this.offset*this.columns()+(y-55)/18*this.columns()+(x-8)/18:-1;}
	@Override public boolean mouseClicked(double mx,double my,int button){int x=(int)mx-this.leftPos,y=(int)my-this.topPos;if(this.menu.controller&&button==0){if(this.menu.page==0){int i=this.hovered(x,y);var list=this.stock();if(i>=0){if(i<list.size())this.selected=list.get(i).id;return true;}}if(this.menu.page==2&&x>=190&&y>=39&&y<56){this.queueView=!this.queueView;this.offset=0;return true;}if(this.menu.page==2&&!this.queueView&&hasShiftDown()&&x>=190&&y>=59&&y<150){var nodes=this.menu.snapshot.getList("Endpoints",Tag.TAG_COMPOUND);int i=this.offset+(y-59)/13;if(i<nodes.size())this.action(6,nodes.getCompound(i).getInt("Index"),0);return true;}if(this.menu.page==2&&x>=190&&y>=151&&y<164){this.action(4,0,0);return true;}}return super.mouseClicked(mx,my,button);}
	@Override public boolean mouseScrolled(double mx,double my,double delta){if(this.menu.controller){int max=this.menu.page==0?Math.max(0,(this.stock().size()-1)/this.columns()-4):this.menu.snapshot.getList(this.menu.page==1?"Workers":this.queueView?"Queue":"Endpoints",Tag.TAG_COMPOUND).size()-(this.menu.page==1?4:7);this.offset=Math.max(0,Math.min(Math.max(0,max),this.offset-(int)Math.signum(delta)));return true;}return super.mouseScrolled(mx,my,delta);}
	@Override public boolean keyPressed(int key,int scan,int modifiers){if(this.search!=null&&this.search.isFocused()||this.amount!=null&&this.amount.isFocused()){if(key==256)this.onClose();else(this.search.isFocused()?this.search:this.amount).keyPressed(key,scan,modifiers);return true;}return super.keyPressed(key,scan,modifiers);}
	@Override public void render(GuiGraphics g,int mx,int my,float partial){this.renderBackground(g);super.render(g,mx,my,partial);this.renderTooltip(g,mx,my);if(this.menu.controller&&this.menu.page==0){int i=this.hovered(mx-this.leftPos,my-this.topPos);var list=this.stock();if(i>=0&&i<list.size()){var t=list.get(i);g.renderComponentTooltip(this.font,List.of(t.item.getHoverName(),Component.translatable("storage.rats.counts",t.available,t.reserved,t.transit,t.input)),mx,my);}}}
}
