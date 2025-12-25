package com.yucareux.terrariumplus.client.screen;

import com.yucareux.terrariumplus.client.widget.map.PlaceSearchWidget;
import com.yucareux.terrariumplus.client.widget.map.SlippyMapPoint;
import com.yucareux.terrariumplus.client.widget.map.SlippyMapWidget;
import com.yucareux.terrariumplus.client.widget.map.component.MarkerMapComponent;
import com.yucareux.terrariumplus.world.data.source.Geocoder;
import com.yucareux.terrariumplus.world.data.source.NominatimGeocoder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public class EarthSpawnpointScreen extends Screen {
	private final EarthCustomizeScreen parent;

	private SlippyMapWidget mapWidget;
	private MarkerMapComponent markerComponent;
	private PlaceSearchWidget searchWidget;

	public EarthSpawnpointScreen(EarthCustomizeScreen parent) {
		super(Component.translatable("gui.earth.spawnpoint"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (this.mapWidget != null) {
			this.mapWidget.close();
		}

		int mapX = 20;
		int mapY = 20;
		int mapWidth = this.width - 40;
		int mapHeight = this.height - 60;
		this.mapWidget = new SlippyMapWidget(mapX, mapY, mapWidth, mapHeight);

		double latitude = this.parent.getSpawnLatitude();
		double longitude = this.parent.getSpawnLongitude();
		this.markerComponent = new MarkerMapComponent(new SlippyMapPoint(latitude, longitude)).allowMovement();
		this.mapWidget.addComponent(this.markerComponent);
		this.mapWidget.getMap().focus(latitude, longitude, 4);

		Geocoder geocoder = new NominatimGeocoder();
		this.searchWidget = new PlaceSearchWidget(mapX + 5, mapY + 5, 200, 20, geocoder, this::handleSearch);

		this.addRenderableOnly(this.mapWidget);
		this.addRenderableWidget(this.searchWidget);
		this.addWidget(this.mapWidget);

		int buttonY = this.height - 28;
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> {
			SlippyMapPoint marker = this.markerComponent.getMarker();
			if (marker != null) {
				this.parent.applySpawnpoint(marker.getLatitude(), marker.getLongitude());
			}
			this.minecraft.setScreen(this.parent);
		}).bounds(this.width / 2 - 154, buttonY, 150, 20).build());

		this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> {
			this.minecraft.setScreen(this.parent);
		}).bounds(this.width / 2 + 4, buttonY, 150, 20).build());
	}

	@Override
	protected void setInitialFocus() {
		if (this.searchWidget != null) {
			this.setInitialFocus(this.searchWidget);
		}
	}

	private void handleSearch(double latitude, double longitude) {
		this.markerComponent.moveMarker(latitude, longitude);
		this.mapWidget.getMap().focus(latitude, longitude, 12);
	}

	@Override
	public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, this.width, this.height, 0xC0101010);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 4, 0xFFFFFF);
		super.render(graphics, mouseX, mouseY, delta);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.searchWidget != null) {
			this.searchWidget.tick();
		}
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.setScreen(this.parent);
		}
	}

	@Override
	public void removed() {
		if (this.mapWidget != null) {
			this.mapWidget.close();
		}
		if (this.searchWidget != null) {
			this.searchWidget.close();
		}
	}
}
