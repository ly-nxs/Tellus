package com.yucareux.terrariumplus.worldgen;

import com.yucareux.terrariumplus.Terrarium;
import java.util.Objects;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

public final class TerrariumWorldPresets {
	public static final ResourceKey<WorldPreset> EARTH = ResourceKey.create(
			Registries.WORLD_PRESET,
			Objects.requireNonNull(Terrarium.id("earth"), "worldPresetId")
	);

	private TerrariumWorldPresets() {
	}
}
