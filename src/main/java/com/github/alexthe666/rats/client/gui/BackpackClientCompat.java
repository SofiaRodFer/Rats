package com.github.alexthe666.rats.client.gui;

import com.github.alexthe666.rats.server.compat.backpacks.RatBackpackContainer;
import net.p3pp3rf1y.sophisticatedcore.client.gui.UpgradeGuiManager;

public final class BackpackClientCompat {
    private BackpackClientCompat() { }
    public static void registerTab() { UpgradeGuiManager.registerTab(RatBackpackContainer.TYPE,RatBackpackTab::new); }
}
