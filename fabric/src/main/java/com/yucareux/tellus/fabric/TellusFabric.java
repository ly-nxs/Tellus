package com.yucareux.tellus.fabric;

import com.yucareux.tellus.Tellus;
import com.yucareux.tellus.client.TellusClient;
import net.fabricmc.api.ModInitializer;

public final class TellusFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        // Run our common setup.
        Tellus.init();
    }
}
