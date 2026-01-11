package com.yucareux.tellus.mixin;

import com.yucareux.tellus.client.client.screen.EarthCustomizeScreen;
import com.yucareux.tellus.worldgen.TellusWorldPresets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PresetEditor.class)
public interface PresetEditorMixin {
	@SuppressWarnings("unchecked")
	@Redirect(
			method = "<clinit>",
			at = @At(
					value = "INVOKE",
					target = "Ljava/util/Map;of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"
			)
	)
	private static Map<Optional<ResourceKey<WorldPreset>>, PresetEditor> tellus$addEarthEditor(
			Object key1,
			Object value1,
			Object key2,
			Object value2
	) {
		Map<Optional<ResourceKey<WorldPreset>>, PresetEditor> editors = new HashMap<>();
		editors.put((Optional<ResourceKey<WorldPreset>>) key1, (PresetEditor) value1);
		editors.put((Optional<ResourceKey<WorldPreset>>) key2, (PresetEditor) value2);
		editors.put(Optional.of(TellusWorldPresets.EARTH), EarthCustomizeScreen::new);
		return Map.copyOf(editors);
	}
}
