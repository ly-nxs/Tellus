package com.yucareux.terrariumplus;

import com.yucareux.terrariumplus.client.screen.EarthTeleportScreen;
import com.yucareux.terrariumplus.network.GeoTpOpenMapPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public class TerrariumClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(GeoTpOpenMapPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				Minecraft minecraft = context.client();
				Screen parent = minecraft.screen;
				minecraft.setScreen(new EarthTeleportScreen(parent, payload.latitude(), payload.longitude()));
			});
		});
	}
}
