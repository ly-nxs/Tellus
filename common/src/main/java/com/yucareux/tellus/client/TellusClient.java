package com.yucareux.tellus.client;

import com.yucareux.tellus.client.client.screen.EarthTeleportScreen;
import com.yucareux.tellus.network.GeoTpOpenMapPayload;
import com.yucareux.tellus.network.TellusWeatherPayload;
import com.yucareux.tellus.world.realtime.SnowGrid;
import com.yucareux.tellus.world.realtime.TellusRealtimeState;
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
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                TellusWeatherPayload.TYPE,
                TellusWeatherPayload.CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        SnowGrid grid = payload.historicalSnowEnabled() && payload.spacingBlocks() > 0
                                ? new SnowGrid(
                                payload.centerX(),
                                payload.centerZ(),
                                payload.spacingBlocks(),
                                payload.snowIndex()
                        )
                                : SnowGrid.empty();
                        TellusRealtimeState.updateWeatherState(
                                payload.weatherEnabled(),
                                payload.precipitationMode(),
                                payload.historicalSnowEnabled(),
                                grid
                        );
                    });
                }
        );
	}
}
