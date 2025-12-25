package com.yucareux.terrariumplus.world.data.source;

import java.io.IOException;

public interface Geocoder {
	double[] get(String place) throws IOException;

	String[] suggest(String place) throws IOException;
}
