package com.github.alexthe666.rats.server.message;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record FarmRosterPacket(int menu,CompoundTag data){
	public static void encode(FarmRosterPacket p,FriendlyByteBuf b){b.writeInt(p.menu);b.writeNbt(p.data);}
	public static FarmRosterPacket decode(FriendlyByteBuf b){return new FarmRosterPacket(b.readInt(),b.readNbt());}
	public static void handle(FarmRosterPacket p,Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->Client.accept(p)));c.get().setPacketHandled(true);}
	private static class Client{static void accept(FarmRosterPacket p){var player=net.minecraft.client.Minecraft.getInstance().player;if(player!=null&&player.containerMenu instanceof com.github.alexthe666.rats.server.inventory.RatFarmingMenu m&&m.containerId==p.menu&&p.data!=null)m.snapshot=p.data;}}
}
