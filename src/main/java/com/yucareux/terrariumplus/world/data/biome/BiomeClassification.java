package com.yucareux.terrariumplus.world.data.biome;

import com.yucareux.terrariumplus.Terrarium;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

public final class BiomeClassification {
	private static final String RESOURCE_PATH = "terrarium/biome/biome_classification_system.csv";

	private static final Map<Integer, Map<String, ResourceKey<Biome>>> BIOME_MAP = new HashMap<>();
	private static final Map<Integer, ResourceKey<Biome>> FALLBACK_MAP = new HashMap<>();
	private static final Set<ResourceKey<Biome>> ALL_BIOMES = new HashSet<>();
	private static boolean loaded;

	private BiomeClassification() {
	}

	public static ResourceKey<Biome> findBiomeKey(int esaCode, String koppenCode) {
		ensureLoaded();
		if (koppenCode == null) {
			return null;
		}
		Map<String, ResourceKey<Biome>> byKoppen = BIOME_MAP.get(esaCode);
		if (byKoppen == null) {
			return null;
		}
		return byKoppen.get(koppenCode.toUpperCase(Locale.ROOT));
	}

	public static ResourceKey<Biome> findFallbackKey(int esaCode) {
		ensureLoaded();
		return FALLBACK_MAP.get(esaCode);
	}

	public static Set<ResourceKey<Biome>> allBiomeKeys() {
		ensureLoaded();
		return Set.copyOf(ALL_BIOMES);
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		synchronized (BiomeClassification.class) {
			if (loaded) {
				return;
			}
			load();
			loaded = true;
		}
	}

	private static void load() {
		InputStream input = BiomeClassification.class.getClassLoader().getResourceAsStream(RESOURCE_PATH);
		if (input == null) {
			Terrarium.LOGGER.warn("Biome classification mapping not found at {}", RESOURCE_PATH);
			return;
		}
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
			String line;
			boolean header = true;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				if (header) {
					header = false;
					continue;
				}
				List<String> fields = parseCsvLine(line);
				if (fields.size() < 5) {
					continue;
				}
				int esaCode;
				try {
					esaCode = Integer.parseInt(fields.get(0).trim());
				} catch (NumberFormatException e) {
					continue;
				}
				String koppenCode = fields.get(2).trim();
				String biomeId = fields.get(4).trim();
				if (biomeId.isEmpty()) {
					continue;
				}
				ResourceKey<Biome> biomeKey = toBiomeKey(biomeId);
				ALL_BIOMES.add(biomeKey);
				if ("NONE".equalsIgnoreCase(koppenCode)) {
					FALLBACK_MAP.put(esaCode, biomeKey);
					continue;
				}
				String normalized = koppenCode.toUpperCase(Locale.ROOT);
				BIOME_MAP.computeIfAbsent(esaCode, unused -> new HashMap<>()).put(normalized, biomeKey);
			}
		} catch (IOException e) {
			Terrarium.LOGGER.warn("Failed to read biome classification mapping", e);
		}
	}

	private static ResourceKey<Biome> toBiomeKey(String biomeId) {
		Identifier id = biomeId.contains(":")
				? Identifier.tryParse(biomeId)
				: Identifier.fromNamespaceAndPath("minecraft", biomeId);
		if (id == null) {
			id = Identifier.fromNamespaceAndPath("minecraft", "plains");
		}
		return ResourceKey.create(Registries.BIOME, id);
	}

	private static List<String> parseCsvLine(String line) {
		List<String> fields = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inQuotes = false;
		for (int i = 0; i < line.length(); i++) {
			char ch = line.charAt(i);
			if (ch == '"') {
				if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					current.append('"');
					i++;
				} else {
					inQuotes = !inQuotes;
				}
				continue;
			}
			if (ch == ',' && !inQuotes) {
				fields.add(current.toString());
				current.setLength(0);
				continue;
			}
			current.append(ch);
		}
		fields.add(current.toString());
		return fields;
	}
}
