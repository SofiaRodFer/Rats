package com.github.alexthe666.rats.server.message;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Small selection-only payload, never inventory contents or private sessions. */
public record StorageOutlinePacket(CompoundTag data) {
	public static void encode(StorageOutlinePacket p,FriendlyByteBuf b){b.writeNbt(p.data);}
	public static StorageOutlinePacket decode(FriendlyByteBuf b){var data=b.readNbt();return new StorageOutlinePacket(data==null?new CompoundTag():data);}
	public static void handle(StorageOutlinePacket p,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->Client.accept(p)));ctx.get().setPacketHandled(true);}
	private static class Client { static void accept(StorageOutlinePacket p){com.github.alexthe666.rats.client.render.StorageOutlineRenderer.accept(p.data);} }
}
