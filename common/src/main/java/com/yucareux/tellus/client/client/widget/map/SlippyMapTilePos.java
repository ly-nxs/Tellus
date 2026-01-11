package com.yucareux.tellus.client.client.widget.map;

import org.jetbrains.annotations.NotNull;

public record SlippyMapTilePos(int x, int y, int zoom) {


    public String getCacheName() {
        return this.zoom + "_" + this.x + "_" + this.y + ".png";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SlippyMapTilePos(int x1, int y1, int zoom1)) {
            return x1 == this.x && y1 == this.y && zoom1 == this.zoom;
        }
        return false;
    }

    @Override
    public @NotNull String toString() {
        return this.x + "_" + this.y + "_" + this.zoom;
    }
}
