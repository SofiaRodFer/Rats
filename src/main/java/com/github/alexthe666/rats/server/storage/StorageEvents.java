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
		var c = RatStorageGoal.controller(rat); if (c == null) return;
		c.tasks.remove(rat.getUUID()); c.workers.remove(rat.getUUID()); c.recalling.remove(rat.getUUID()); c.setChanged();
	}
}
