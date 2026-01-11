package com.yucareux.tellus.client.client.preview;

import com.yucareux.tellus.worldgen.EarthGeneratorSettings;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;

public final class TerrainPreviewWidget extends AbstractWidget implements AutoCloseable {
	private static final float DEFAULT_ROTATION_X = (float) Math.toRadians(28.0);
	private static final float DEFAULT_ROTATION_Y = (float) Math.toRadians(20.0);
	private static final float DEFAULT_ZOOM = 1.25f;
	private static final float MIN_ROTATION_X = (float) Math.toRadians(-80.0);
	private static final float MAX_ROTATION_X = (float) Math.toRadians(80.0);
	private static final float MIN_ZOOM = 0.5f;
	private static final float MAX_ZOOM = 4.0f;
	private static final float ROTATION_SPEED = 0.01f;
	private static final float ZOOM_SPEED = 0.1f;
	private static final float AUTO_ROTATION_SPEED = 0.0022f;
	private static final long AUTO_RESUME_DELAY_MS = 1200L;

	private final TerrainPreview preview;
	private boolean dragging;
	private float rotationX = DEFAULT_ROTATION_X;
	private float rotationY = DEFAULT_ROTATION_Y;
	private float zoom = DEFAULT_ZOOM;
	private long lastInteractionTime;

	public TerrainPreviewWidget(int x, int y, int width, int height) {
		super(x, y, width, height, Component.empty());
		this.preview = new TerrainPreview();
	}

	public void requestRebuild(EarthGeneratorSettings settings) {
		this.preview.requestRebuild(settings);
	}

	public void tick() {
		this.preview.tick();
		long now = System.currentTimeMillis();
		if (!this.dragging && now - this.lastInteractionTime > AUTO_RESUME_DELAY_MS) {
			this.rotationY += AUTO_ROTATION_SPEED;
		}
	}

	@Override
	protected void renderWidget(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		this.renderBlurredBackground(graphics);

		int inset = 4;
		int contentX = this.getX() + inset;
		int contentY = this.getY() + inset;
		int contentWidth = Math.max(1, this.width - inset * 2);
		int contentHeight = Math.max(1, this.height - inset * 2);

		this.preview.render(graphics, contentX, contentY, contentWidth, contentHeight, this.rotationX, this.rotationY, this.zoom);
	}

	@Override
	public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			this.dragging = true;
			this.lastInteractionTime = System.currentTimeMillis();
		}
	}

	@Override
	protected void onDrag(@NonNull MouseButtonEvent event, double deltaX, double deltaY) {
		if (this.dragging) {
			this.rotationY += (float) deltaX * ROTATION_SPEED;
			this.rotationX = Mth.clamp(this.rotationX + (float) deltaY * ROTATION_SPEED, MIN_ROTATION_X, MAX_ROTATION_X);
			this.lastInteractionTime = System.currentTimeMillis();
		}
	}

	@Override
	public void onRelease(@NonNull MouseButtonEvent event) {
		this.dragging = false;
		this.lastInteractionTime = System.currentTimeMillis();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (this.isMouseOver(mouseX, mouseY)) {
			this.zoom = Mth.clamp(this.zoom + (float) verticalAmount * ZOOM_SPEED, MIN_ZOOM, MAX_ZOOM);
			this.lastInteractionTime = System.currentTimeMillis();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	protected void updateWidgetNarration(@NonNull NarrationElementOutput narration) {
	}

	private void renderBlurredBackground(GuiGraphics graphics) {
		int left = this.getX();
		int top = this.getY();
		int right = this.getX() + this.width;
		int bottom = this.getY() + this.height;

		graphics.fill(left, top, right, bottom, 0xB0000000);
		graphics.fill(left + 1, top + 1, right - 1, bottom - 1, 0x55000000);
		graphics.fillGradient(left, top, right, top + 8, 0x66000000, 0x22000000);
		graphics.fillGradient(left, bottom - 8, right, bottom, 0x22000000, 0x66000000);
		graphics.renderOutline(left, top, this.width, this.height, 0x22000000);
	}

	@Override
	public void close() {
		this.preview.close();
	}
}
