package com.github.alexthe666.rats.server.items.upgrades;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
/** Logistics cargo is never food, even when the owner's command is Wander. */
public class StorageKeeperRatUpgradeItem extends PlatterRatUpgradeItem {
	public StorageKeeperRatUpgradeItem(Properties properties) { super(properties, 2); }
	@Override public boolean isRatHoldingFood(TamedRat rat) { return false; }
	@Override public boolean shouldCollectItem(TamedRat rat, net.minecraft.world.item.ItemStack stack) { return !rat.getPersistentData().hasUUID("RatStorageNetwork"); }
}
