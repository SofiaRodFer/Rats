package com.github.alexthe666.rats.server.misc;

import com.github.alexthe666.rats.RatsMod;
import com.github.alexthe666.rats.server.entity.rat.RatCommand;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Sliding ticking windows, owned by workers, not by players or global scans. */
@Mod.EventBusSubscriber(modid=RatsMod.MODID)
public final class RatWorkChunks {
    private static final Map<ServerLevel,Map<UUID,Set<Long>>> WINDOWS=new WeakHashMap<>();
    private static final Map<ServerLevel,Long> RECOVER_AT=new WeakHashMap<>();
    private RatWorkChunks() { }
    public static void init() {
        ForgeChunkManager.setForcedChunkLoadingCallback(RatsMod.MODID,(level,helper)->{
            Map<UUID,Set<Long>> restored=new HashMap<>();
            helper.getEntityTickets().forEach((id,tickets)->{
                Set<Long> chunks=new HashSet<>();
                tickets.getSecond().forEach((long chunk)->chunks.add(chunk));
                if(!chunks.isEmpty())restored.put(id,chunks);
            });
            WINDOWS.put(level,restored); RECOVER_AT.put(level,level.getGameTime()+200);
        });
    }
    public static boolean active(TamedRat rat) {
        if(!rat.isAlive()||rat.isBaby()||rat.isPassenger()||rat.isInCage())return false;
        if(rat.getPersistentData().hasUUID("RatStorageNetwork")){
            var tag=rat.getPersistentData();
            if(!tag.getString("RatStorageDimension").equals(rat.level().dimension().location().toString()))return false;
            BlockPos pos=BlockPos.of(tag.getLong("RatStorageController"));
            if(!rat.level().hasChunkAt(pos))return true;
            var controller=com.github.alexthe666.rats.server.entity.ai.goal.RatStorageGoal.controller(rat);
            return controller!=null&&controller.workers.contains(rat.getUUID());
        }
        if(rat.getCommand()==RatCommand.TRANSPORT)return endpoint(rat,rat.getDepositPos())&&(endpoint(rat,rat.getPickupPos())||!rat.cargo.isEmpty());
        if(rat.getCommand()!=RatCommand.HARVEST)return false;
        if(RatUpgradeUtils.hasUpgrade(rat,com.github.alexthe666.rats.registry.RatsItemRegistry.RAT_UPGRADE_QUARRY.get()))return workstation(rat,com.github.alexthe666.rats.server.block.entity.RatQuarryBlockEntity.class);
        if(RatUpgradeUtils.hasUpgrade(rat,com.github.alexthe666.rats.registry.RatsItemRegistry.RAT_UPGRADE_FARMER.get()))return endpoint(rat,rat.getDepositPos())||rat.getDepositPos().isEmpty();
        return rat.isCurrentlyWorking||!rat.cargo.isEmpty()&&endpoint(rat,rat.getDepositPos())
            ||RatUpgradeUtils.forEachUpgradeBool(rat,u->u instanceof com.github.alexthe666.rats.server.items.upgrades.interfaces.ChangesAIUpgrade,false);
    }
    private static boolean endpoint(TamedRat rat,Optional<net.minecraft.core.GlobalPos> endpoint){
        if(endpoint.isEmpty()||!endpoint.get().dimension().equals(rat.level().dimension()))return false;
        BlockPos pos=endpoint.get().pos();
        if(!rat.level().hasChunkAt(pos))return true;
        var entity=rat.level().getBlockEntity(pos);
        return entity!=null&&(entity.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).isPresent()
            ||entity.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY).isPresent()
            ||entity.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER).isPresent());
    }
    private static boolean workstation(TamedRat rat,Class<?> type){
        if(!endpoint(rat,rat.getDepositPos()))return false;
        BlockPos pos=rat.getDepositPos().orElseThrow().pos();
        return !rat.level().hasChunkAt(pos)||type.isInstance(rat.level().getBlockEntity(pos));
    }
    private static void window(Set<Long> chunks,BlockPos center,int radius) {
        ChunkPos chunk=new ChunkPos(center);
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)chunks.add(ChunkPos.asLong(chunk.x+x,chunk.z+z));
    }
    public static Set<Long> desired(TamedRat rat) {
        Set<Long> chunks=new HashSet<>(); if(!active(rat))return chunks;
        window(chunks,rat.blockPosition(),1);
        if(endpoint(rat,rat.getDepositPos()))rat.getDepositPos().ifPresent(p->window(chunks,p.pos(),1));
        if(endpoint(rat,rat.getPickupPos()))rat.getPickupPos().ifPresent(p->window(chunks,p.pos(),1));
        if(rat.getPersistentData().hasUUID("RatStorageNetwork"))window(chunks,BlockPos.of(rat.getPersistentData().getLong("RatStorageController")),1);
        var controller=com.github.alexthe666.rats.server.entity.ai.goal.RatStorageGoal.controller(rat);
        if(controller!=null){ChunkPos min=new ChunkPos(controller.getBlockPos().offset(-20,0,-20)),max=new ChunkPos(controller.getBlockPos().offset(20,0,20));for(int x=min.x;x<=max.x;x++)for(int z=min.z;z<=max.z;z++)chunks.add(ChunkPos.asLong(x,z));}
        return chunks;
    }
    public static void update(TamedRat rat) {
        if(!(rat.level() instanceof ServerLevel level))return;
        Map<UUID,Set<Long>> workers=WINDOWS.computeIfAbsent(level,l->new HashMap<>());
        Set<Long> before=workers.getOrDefault(rat.getUUID(),Set.of()),after=desired(rat),confirmed=new HashSet<>(before);
        for(long key:after)if(!before.contains(key)&&force(level,rat.getUUID(),key,true))confirmed.add(key);
        for(long key:before)if(!after.contains(key)){force(level,rat.getUUID(),key,false);confirmed.remove(key);}
        if(confirmed.isEmpty())workers.remove(rat.getUUID());else workers.put(rat.getUUID(),confirmed);
    }
    private static boolean force(ServerLevel level,UUID id,long key,boolean add){ChunkPos p=new ChunkPos(key);return ForgeChunkManager.forceChunk(level,RatsMod.MODID,id,p.x,p.z,add,true);}
    public static void release(TamedRat rat){if(rat.level() instanceof ServerLevel level)release(level,rat.getUUID());}
    private static void release(ServerLevel level,UUID id){Map<UUID,Set<Long>> workers=WINDOWS.get(level);if(workers==null)return;var chunks=workers.remove(id);if(chunks!=null)for(long key:chunks)force(level,id,key,false);}
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level)||level.getGameTime()%100!=0||level.getGameTime()<RECOVER_AT.getOrDefault(level,0L))return;
        var workers=WINDOWS.get(level);if(workers==null)return;
        for(UUID id:List.copyOf(workers.keySet()))if(!(level.getEntity(id) instanceof TamedRat rat)||!active(rat))release(level,id);
    }
}
