package com.yucareux.terrariumplus.client.widget.map;

import com.yucareux.terrariumplus.Terrarium;
import com.yucareux.terrariumplus.world.data.source.Geocoder;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

public class PlaceSearchWidget extends EditBox {
	private static final int SUGGESTION_COUNT = 3;
	private static final int SUGGESTION_HEIGHT = 20;
	private static final long MIN_SUGGEST_INTERVAL_MS = 500;

	private final Geocoder geocoder;
	private final SearchHandler searchHandler;
	private final ExecutorService executor = Executors.newFixedThreadPool(1,
			new ThreadFactoryBuilder().setNameFormat("terrarium-geocoder-%d").setDaemon(true).build());

	private final List<String> suggestions = new ArrayList<>(SUGGESTION_COUNT);
	private CompletableFuture<String[]> queriedSuggestions;
	private State state = State.OK;
	private boolean pause;
	private String lastSearchText;
	private long lastSearchTextTime;

	public PlaceSearchWidget(int x, int y, int width, int height, Geocoder geocoder, SearchHandler searchHandler) {
		super(Minecraft.getInstance().font, x, y, width, height, Component.translatable("gui.earth.search"));
		this.geocoder = geocoder;
		this.searchHandler = searchHandler;
		this.setBordered(false);
		this.setMaxLength(256);
		this.setHint(Component.translatable("gui.earth.search"));
	}

	public void tick() {
		if (this.queriedSuggestions != null && this.queriedSuggestions.isDone()) {
			this.suggestions.clear();
			try {
				String[] result = this.queriedSuggestions.get();
				if (result != null) {
					this.suggestions.addAll(Arrays.asList(result).subList(0, Math.min(result.length, SUGGESTION_COUNT)));
					if (this.suggestions.isEmpty()) {
						this.state = State.NOT_FOUND;
					}
				} else {
					this.state = State.OK;
				}
				this.queriedSuggestions = null;
			} catch (Exception e) {
				Terrarium.LOGGER.error("Failed to get queried suggestions", e);
			}
		}

		String text = this.getValue().trim();
		if (!this.pause && !text.isEmpty()) {
			if (!text.equals(this.lastSearchText) && this.queriedSuggestions == null) {
				long time = System.currentTimeMillis();
				if (time - this.lastSearchTextTime > MIN_SUGGEST_INTERVAL_MS) {
					this.suggestions.clear();
					this.queriedSuggestions = CompletableFuture.supplyAsync(() -> {
						try {
							return this.geocoder.suggest(text);
						} catch (Exception e) {
							Terrarium.LOGGER.error("Failed to get geocoder suggestions", e);
							return new String[0];
						}
					}, this.executor);
					this.lastSearchText = text;
					this.lastSearchTextTime = time;
				}
			}
		} else {
			this.suggestions.clear();
		}
	}

	@Override
	public void renderWidget(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		int x = this.getX();
		int y = this.getY();
		int width = this.getWidth();
		int height = this.getHeight();

		graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFFA0A0A0);
		graphics.fill(x, y, x + width, y + height, this.state.getBackgroundColor());

		graphics.pose().pushMatrix();
		graphics.pose().translate(0.0F, (height - 8) / 2.0F - 1.0F);
		super.renderWidget(graphics, mouseX, mouseY, delta);
		graphics.pose().popMatrix();

		if (!this.suggestions.isEmpty() && this.isFocused()) {
			int suggestionBoxHeight = SUGGESTION_HEIGHT * this.suggestions.size() + 2;
			int suggestionOriginY = y + height;
			graphics.fill(x - 1, suggestionOriginY, x + width + 1, suggestionOriginY + suggestionBoxHeight,
					0xFFA0A0A0);
			graphics.fill(x, suggestionOriginY, x + width, suggestionOriginY + suggestionBoxHeight, 0xFF000000);

			for (int i = 0; i < this.suggestions.size(); i++) {
				String suggestionValue = Objects.requireNonNull(this.suggestions.get(i), "suggestionValue");
				String suggestion = Objects.requireNonNull(
						Minecraft.getInstance().font.plainSubstrByWidth(suggestionValue, width - 8),
						"suggestion"
				);
				int suggestionX = x;
				int suggestionY = suggestionOriginY + i * SUGGESTION_HEIGHT + 1;

				if (mouseX >= suggestionX && mouseY >= suggestionY && mouseX <= suggestionX + width
						&& mouseY <= suggestionY + SUGGESTION_HEIGHT) {
					graphics.fill(suggestionX, suggestionY, suggestionX + width, suggestionY + SUGGESTION_HEIGHT,
							0xFF5078A0);
				}

				graphics.drawString(Minecraft.getInstance().font, suggestion, suggestionX + 4,
						suggestionY + (SUGGESTION_HEIGHT - Minecraft.getInstance().font.lineHeight) / 2,
						0xFFFFFFFF);
			}
		}
	}

	@Override
	public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean isPrimary) {
		double mouseX = event.x();
		double mouseY = event.y();

		if (this.isVisible() && this.isFocused() && event.button() == 0) {
			if (!this.suggestions.isEmpty()) {
				int suggestionOriginY = this.getY() + this.getHeight();
				int width = this.getWidth();

				for (int i = 0; i < this.suggestions.size(); i++) {
					int suggestionX = this.getX();
					int suggestionY = suggestionOriginY + i * SUGGESTION_HEIGHT + 1;

					if (mouseX >= suggestionX && mouseY >= suggestionY && mouseX <= suggestionX + width
							&& mouseY <= suggestionY + SUGGESTION_HEIGHT) {
						String suggestionValue = Objects.requireNonNull(this.suggestions.get(i), "suggestionValue");
						this.setValue(suggestionValue);
						this.state = State.FOUND;
						this.handleAccept();
						return true;
					}
				}
			}
		}

		return super.mouseClicked(event, isPrimary);
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		if (super.isMouseOver(mouseX, mouseY)) {
			return true;
		}

		if (this.isFocused() && !this.suggestions.isEmpty()) {
			int suggestionOriginY = this.getY() + this.getHeight();
			int suggestionHeight = SUGGESTION_HEIGHT * this.suggestions.size() + 2;
			int suggestionX = this.getX() - 1;
			int suggestionY = suggestionOriginY;
			int suggestionWidth = this.getWidth() + 2;
			return mouseX >= suggestionX && mouseX <= suggestionX + suggestionWidth
					&& mouseY >= suggestionY && mouseY <= suggestionY + suggestionHeight;
		}

		return false;
	}

	@Override
	public boolean keyPressed(@NonNull KeyEvent event) {
		if (!this.isFocused()) {
			return false;
		}

		this.pause = false;

		int key = event.key();
		if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			this.handleAccept();
			this.suggestions.clear();
			return true;
		}

		if (super.keyPressed(event)) {
			this.state = State.OK;
			return true;
		}

		return false;
	}

	public void close() {
		this.executor.shutdownNow();
	}

	private void handleAccept() {
		String text = this.getValue();
		CompletableFuture.runAsync(() -> {
			try {
				double[] coordinate = this.geocoder.get(text);
				if (coordinate != null) {
					Minecraft.getInstance().execute(() -> {
						this.searchHandler.handle(coordinate[0], coordinate[1]);
						this.state = State.FOUND;
					});
				} else {
					Minecraft.getInstance().execute(() -> this.state = State.NOT_FOUND);
				}
				this.pause = true;
			} catch (IOException e) {
				Terrarium.LOGGER.error("Failed to find searched place {}", text, e);
			}
		}, this.executor);
	}

	public interface SearchHandler {
		void handle(double latitude, double longitude);
	}

	public enum State {
		OK(0xFF000000),
		FOUND(0xFF004600),
		NOT_FOUND(0xFF460000);

		private final int backgroundColor;

		State(int backgroundColor) {
			this.backgroundColor = backgroundColor;
		}

		public int getBackgroundColor() {
			return this.backgroundColor;
		}
	}
}
