package com.yucareux.terrariumplus.client.widget;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import org.jspecify.annotations.NonNull;

public class CustomizationList extends ContainerObjectSelectionList<CustomizationList.Entry> {
	public CustomizationList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
		super(minecraft, width, height, y, itemHeight);
		this.centerListVertically = false;
	}

	public void clear() {
		this.clearEntries();
	}

	public void addWidget(AbstractWidget widget) {
		this.addEntry(new Entry(widget));
	}

	@Override
	public int getRowWidth() {
		return this.width - 20;
	}

	@Override
	protected int scrollBarX() {
		return this.getX() + this.width - 6;
	}

	@Override
	protected void renderListBackground(@NonNull GuiGraphics graphics) {
		// Intentionally empty to avoid list background artifacts.
	}

	@Override
	protected void renderListSeparators(@NonNull GuiGraphics graphics) {
		// No separators needed for this UI.
	}

	public static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
		private final AbstractWidget widget;

		public Entry(AbstractWidget widget) {
			this.widget = Objects.requireNonNull(widget, "widget");
		}

		@Override
		public @NonNull List<? extends GuiEventListener> children() {
			return Objects.requireNonNull(List.of(this.widget), "children");
		}

		@Override
		public @NonNull List<? extends NarratableEntry> narratables() {
			return Objects.requireNonNull(List.of(this.widget), "narratables");
		}

		@Override
		public void renderContent(@NonNull GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float delta) {
			this.widget.setX(this.getContentX());
			this.widget.setY(this.getContentY());
			this.widget.setWidth(this.getContentWidth());
			this.widget.setHeight(this.getContentHeight());
			this.widget.render(graphics, mouseX, mouseY, delta);
		}
	}
}
