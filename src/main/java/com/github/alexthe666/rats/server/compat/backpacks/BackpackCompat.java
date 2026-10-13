package com.github.alexthe666.rats.server.compat.backpacks;

import com.github.alexthe666.rats.registry.RatsItemRegistry;
import net.minecraft.world.item.Item;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerRegistry;

/** Keep optional parent classes out of unconditional registry bytecode frames. */
public final class BackpackCompat {
    private BackpackCompat() { }
    public static Item createItem() { return new RatBackpackUpgrade(); }
    public static void registerContainer() {
        UpgradeContainerRegistry.register(RatsItemRegistry.RAT_STORAGE_BACKPACK_UPGRADE.getId(),RatBackpackContainer.TYPE);
    }
}
