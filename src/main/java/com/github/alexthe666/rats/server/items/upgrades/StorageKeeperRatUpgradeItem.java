package com.github.alexthe666.rats.server.items.upgrades;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
/** Logistics cargo is never food, even when the owner's command is Wander. */
public class StorageKeeperRatUpgradeItem extends PlatterRatUpgradeItem {
	public StorageKeeperRatUpgradeItem(Properties properties) { super(properties, 1); }
	@Override public boolean isRatHoldingFood(TamedRat rat) { return false; }
}
