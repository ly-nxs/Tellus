package com.yucareux.tellus.world.data.source;

import java.io.IOException;

public interface Geocoder {
	record Suggestion(String displayName, double latitude, double longitude) {
	}

	double[] get(String place) throws IOException;

	Suggestion[] suggest(String place) throws IOException;
}
