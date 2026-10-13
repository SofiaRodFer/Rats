package com.github.alexthe666.rats.server.inventory;

import com.github.alexthe666.rats.registry.RatsMenuRegistry;
import com.github.alexthe666.rats.server.block.entity.RatFarmingBlockEntity;
import com.github.alexthe666.rats.server.misc.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public class RatFarmingMenu extends AbstractContainerMenu {
	public final Container storage, ghosts;
	public final ContainerData data;
	public boolean layoutView;
	public int page;
	public net.minecraft.nbt.CompoundTag snapshot=new net.minecraft.nbt.CompoundTag();
	private int refreshAt;
	private final Player viewer;
	private FarmLayout cachedLayout;
	public RatFarmingMenu(int id, Inventory inventory) { this(id, inventory, new SimpleContainer(54), new SimpleContainer(256), new SimpleContainerData(6)); }
	public RatFarmingMenu(int id, Inventory inventory, RatFarmingBlockEntity farm) { this(id, inventory, farm, farm.ghost, farm.data); }
	private RatFarmingMenu(int id, Inventory inventory, Container storage, Container ghosts, ContainerData data) {
		super(RatsMenuRegistry.RAT_FARMING_CONTAINER.get(), id); this.viewer=inventory.player;this.storage = storage; this.ghosts = ghosts; this.data = data;
		checkContainerSize(storage, 54); checkContainerSize(ghosts, 256); checkContainerDataCount(data, 6);
		for (int i = 0; i < 54; i++) this.addSlot(new Slot(storage, i, 8 + i % 9 * 18, 38 + i / 9 * 18) { @Override public boolean isActive() { return !layoutView; } });
		for (int i = 0; i < 27; i++) this.addSlot(new Slot(inventory, i + 9, 8 + i % 9 * 18, 164 + i / 9 * 18));
		for (int i = 0; i < 9; i++) this.addSlot(new Slot(inventory, i, 8 + i * 18, 222));
		for (int i = 0; i < 256; i++) this.addSlot(new Slot(ghosts, i, -1000, -1000) { @Override public boolean isActive() { return false; } @Override public boolean mayPlace(ItemStack stack) { return false; } @Override public boolean mayPickup(Player player) { return false; } });
		var patterns=new SimpleContainer(4);for(int i=0;i<4;i++)this.addSlot(new Slot(patterns,i,-1000,-1000){@Override public boolean isActive(){return false;}@Override public boolean mayPlace(ItemStack s){return false;}@Override public boolean mayPickup(Player p){return false;}});
		this.addDataSlots(data);
	}
	public FarmLayout layout() { int width = Math.max(1, Math.min(16, this.data.get(0))), height = Math.max(1, Math.min(16, this.data.get(1))); boolean legacy=(this.data.get(5)&2)!=0; if (this.cachedLayout == null || this.cachedLayout.width != width || this.cachedLayout.height != height||this.cachedLayout.legacy!=legacy) this.cachedLayout = new FarmLayout(width, height,legacy); return this.cachedLayout; }
	@Override public boolean stillValid(Player player) { return this.storage.stillValid(player); }
	@Override public void clicked(int slot, int button, ClickType type, Player player) {
		if (player!=this.viewer||!this.stillValid(player)) return;
		if(slot>=346&&slot<350){if(type==ClickType.PICKUP&&(button==0||button==1)&&this.storage instanceof RatFarmingBlockEntity farm)farm.template(slot-346,button==1?ItemStack.EMPTY:this.getCarried());this.broadcastChanges();return;}
		if (slot >= 90) {
			int cell = slot - 90;
			if (type != ClickType.PICKUP || (button != 0 && button != 1) || !this.layout().cropCell(cell)) return;
			ItemStack seed = button == 1 ? ItemStack.EMPTY : this.getCarried();
			if (!seed.isEmpty() && !FarmCrops.seed(seed)) return;
			if (this.storage instanceof RatFarmingBlockEntity farm) farm.paint(cell, seed); else this.ghosts.setItem(cell, seed.isEmpty() ? ItemStack.EMPTY : seed.copyWithCount(1));
			this.broadcastChanges(); return;
		}
		super.clicked(slot, button, type, player);
	}
	@Override public boolean clickMenuButton(Player player, int button) {
		if (player!=this.viewer||!this.stillValid(player) || !(this.storage instanceof RatFarmingBlockEntity farm)) return false;
		int width = farm.layout().width, height = farm.layout().height;
		if(button>=10&&button<=13){boolean result=farm.mode(button-10);this.broadcastChanges();return result;}
		if(button==15){farm.clearPlan();this.refreshAt=0;this.broadcastChanges();return true;}
		if(button==14){farm.splitAxis();this.broadcastChanges();return true;}
		if(button>=20&&button<=22){this.page=button-20;this.layoutView=this.page!=0;return true;}
		switch(button) { case 0 -> width--; case 1 -> width++; case 2 -> width--; case 3 -> width++; case 4 -> { farm.rotate(); this.broadcastChanges(); return true; } default -> { return false; } }
		farm.configure(width, height); this.broadcastChanges(); return true;
	}
	@Override public void broadcastChanges(){
		if(this.viewer instanceof net.minecraft.server.level.ServerPlayer player&&this.storage instanceof RatFarmingBlockEntity farm&&this.stillValid(player)&&player.tickCount>=this.refreshAt){this.refreshAt=player.tickCount+20;var root=new net.minecraft.nbt.CompoundTag();var crew=new net.minecraft.nbt.ListTag();for(var id:farm.workerIds()){var t=new net.minecraft.nbt.CompoundTag();var e=player.serverLevel().getEntity(id);t.putString("Name",e==null?"—":e.getName().getString());t.putInt("Entity",e==null?-1:e.getId());t.putBoolean("Loaded",e!=null);crew.add(t);}root.putBoolean("MissingDirt",farm.missingDirt());root.put("Workers",crew);var seeds=new net.minecraft.nbt.ListTag();for(int i=0;i<4;i++)seeds.add(farm.template(i).save(new net.minecraft.nbt.CompoundTag()));root.put("Templates",seeds);com.github.alexthe666.rats.server.message.RatsNetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),new com.github.alexthe666.rats.server.message.FarmRosterPacket(this.containerId,root));}
		super.broadcastChanges();
	}
	@Override public ItemStack quickMoveStack(Player player, int index) {
		if(this.layoutView)return ItemStack.EMPTY;
		if (player != this.viewer || !this.stillValid(player) || index < 0 || index >= 90) return ItemStack.EMPTY;
		Slot slot = this.slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
		var stack = slot.getItem(); var copy = stack.copy();
		if (!this.moveItemStackTo(stack, index < 54 ? 54 : 0, index < 54 ? 90 : 54, index < 54)) return ItemStack.EMPTY;
		if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged(); slot.onTake(player, stack); return copy;
	}
}
