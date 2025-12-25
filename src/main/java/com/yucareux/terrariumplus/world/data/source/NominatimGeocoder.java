package com.yucareux.terrariumplus.world.data.source;

import com.yucareux.terrariumplus.Terrarium;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class NominatimGeocoder implements Geocoder {
	private static final String SEARCH_URL = "https://nominatim.openstreetmap.org/search?format=json&q=%s";

	@Override
	public double[] get(String place) {
		try {
			JsonElement result = this.query(place);
			if (result.isJsonArray()) {
				JsonArray array = result.getAsJsonArray();
				if (!array.isEmpty()) {
					JsonObject first = array.get(0).getAsJsonObject();
					double lat = first.get("lat").getAsDouble();
					double lon = first.get("lon").getAsDouble();
					return new double[] { lat, lon };
				}
			}
		} catch (IOException e) {
			Terrarium.LOGGER.error("Failed to geocode place: {}", place, e);
		}
		return null;
	}

	@Override
	public String[] suggest(String place) {
		try {
			JsonElement result = this.query(place);
			if (result.isJsonArray()) {
				JsonArray array = result.getAsJsonArray();
				List<String> suggestions = new ArrayList<>();
				for (JsonElement element : array) {
					if (element.isJsonObject()) {
						suggestions.add(element.getAsJsonObject().get("display_name").getAsString());
					}
				}
				return suggestions.toArray(new String[0]);
			}
		} catch (IOException e) {
			Terrarium.LOGGER.error("Failed to suggest places for: {}", place, e);
		}
		return new String[0];
	}

	private JsonElement query(String place) throws IOException {
		String encodedPlace = URLEncoder.encode(place, StandardCharsets.UTF_8);
		URI uri = URI.create(String.format(SEARCH_URL, encodedPlace));
		HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
		connection.setRequestProperty("User-Agent", "Terrarium/2.0.0 (Minecraft Mod)");
		connection.setConnectTimeout(5000);
		connection.setReadTimeout(5000);

		try (InputStream input = connection.getInputStream();
				InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader);
		}
	}
}
