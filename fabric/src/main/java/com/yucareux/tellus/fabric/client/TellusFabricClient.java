package com.yucareux.tellus.fabric.client;

import com.yucareux.tellus.client.TellusClient;
import net.fabricmc.api.ClientModInitializer;

public final class TellusFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.
        TellusClient.onInitializeClient();
    }
}
