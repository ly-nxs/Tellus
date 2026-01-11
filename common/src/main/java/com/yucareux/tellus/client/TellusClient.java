package com.yucareux.tellus.client;

import com.yucareux.tellus.client.client.screen.EarthTeleportScreen;
import com.yucareux.tellus.network.GeoTpOpenMapPayload;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public class TellusClient {

	public static void onInitializeClient() {
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                GeoTpOpenMapPayload.TYPE,
                GeoTpOpenMapPayload.CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        Minecraft minecraft = Minecraft.getInstance();
                        Screen parent = minecraft.screen;
                        minecraft.setScreen(new EarthTeleportScreen(parent, payload.latitude(), payload.longitude()));
                    });
                }
        );
	}
}
