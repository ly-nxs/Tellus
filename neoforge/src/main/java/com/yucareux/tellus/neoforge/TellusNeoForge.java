package com.yucareux.tellus.neoforge;

import com.mojang.serialization.MapCodec;
import com.yucareux.tellus.Tellus;
import com.yucareux.tellus.client.TellusClient;
import com.yucareux.tellus.worldgen.EarthBiomeSource;
import com.yucareux.tellus.worldgen.EarthChunkGenerator;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.NotNull;

@Mod(Tellus.MOD_ID)
public final class TellusNeoForge {

    private static final DeferredRegister<@NotNull MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(BuiltInRegistries.CHUNK_GENERATOR, Tellus.MOD_ID);

    private static final DeferredRegister<@NotNull MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(BuiltInRegistries.BIOME_SOURCE, Tellus.MOD_ID);

    static {
        CHUNK_GENERATORS.register("earth", () -> EarthChunkGenerator.CODEC);
        BIOME_SOURCES.register("earth", () -> EarthBiomeSource.CODEC);
    }

    public TellusNeoForge(IEventBus modBus) {
        CHUNK_GENERATORS.register(modBus);
        BIOME_SOURCES.register(modBus);

        Tellus.init();

        // Register client setup only on client
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modBus.addListener(this::onClientSetup);
        }
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(TellusClient::onInitializeClient);
    }
}