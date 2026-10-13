package com.github.alexthe666.rats.server.storage;
import com.github.alexthe666.rats.RatsMod;
import com.github.alexthe666.rats.server.entity.ai.goal.RatStorageGoal;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid = RatsMod.MODID)
public final class StorageEvents {
	private StorageEvents() { }
	@SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void died(LivingDeathEvent event) {
		if (!(event.getEntity() instanceof TamedRat rat) || rat.level().isClientSide()) return;
		if(rat.getPersistentData().hasUUID("RatStorageNetwork") && rat.level() instanceof net.minecraft.server.level.ServerLevel current) {
			var dimension=net.minecraft.resources.ResourceLocation.tryParse(rat.getPersistentData().getString("RatStorageDimension")); var previous=dimension==null ? null : current.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,dimension)); StorageLedger.get(previous==null ? current : previous).markDead(rat.getPersistentData().getUUID("RatStorageNetwork"),rat.getUUID());
		}
		var c = RatStorageGoal.controller(rat); if (c == null) return;
		c.pruneDeadWorkers();
	}
}
