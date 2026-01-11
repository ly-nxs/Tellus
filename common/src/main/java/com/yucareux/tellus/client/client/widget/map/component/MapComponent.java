package com.yucareux.tellus.client.client.widget.map.component;

import com.yucareux.tellus.client.client.widget.map.SlippyMap;
import com.yucareux.tellus.client.client.widget.map.SlippyMapPoint;
import net.minecraft.client.gui.GuiGraphics;

public interface MapComponent {
	void onDrawMap(SlippyMap map, GuiGraphics graphics, int mouseX, int mouseY, SlippyMapPoint mouse);

	default boolean onMouseClicked(SlippyMap map, SlippyMapPoint mouse, int button) {
		return false;
	}

	default boolean onMouseReleased(SlippyMap map, SlippyMapPoint mouse, int button) {
		return false;
	}
}
