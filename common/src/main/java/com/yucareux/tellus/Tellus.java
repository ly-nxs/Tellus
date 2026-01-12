package com.yucareux.tellus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.yucareux.tellus.integration.distant_horizons.DistantHorizonsIntegration;
import com.yucareux.tellus.network.GeoTpOpenMapPayload;
import com.yucareux.tellus.network.GeoTpTeleportPayload;
import com.yucareux.tellus.worldgen.EarthBiomeSource;
import com.yucareux.tellus.worldgen.EarthChunkGenerator;
import com.yucareux.tellus.worldgen.EarthGeneratorSettings;
import com.yucareux.tellus.network.TellusWeatherPayload;
import com.yucareux.tellus.world.realtime.TellusRealtimeState;

import java.util.*;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.server.packs.PackType;
import net.minecraft.SharedConstants;
import net.minecraft.util.Mth;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Tellus {
    public static final @NonNull String MOD_ID = "tellus";
    private static final String DYNAMIC_DIMENSION_PACK_NAME = "tellus_dynamic_dimension";
    private static final String DYNAMIC_DIMENSION_PACK_ID = "file/" + DYNAMIC_DIMENSION_PACK_NAME;
    private static final @NonNull Identifier EARTH_DIMENSION_ID = Objects.requireNonNull(
            Identifier.fromNamespaceAndPath(MOD_ID, "earth"),
            "earthDimensionId"
    );
    private static final @NonNull Identifier DYNAMIC_DIMENSION_ID = Objects.requireNonNull(
            Identifier.fromNamespaceAndPath(MOD_ID, "earth_dynamic"),
            "dynamicDimensionId"
    );
    private static final @NonNull ResourceKey<DimensionType> EARTH_DIMENSION_KEY = Objects.requireNonNull(
            ResourceKey.create(Registries.DIMENSION_TYPE, EARTH_DIMENSION_ID),
            "earthDimensionKey"
    );
    private static final @NonNull ResourceKey<DimensionType> DYNAMIC_DIMENSION_KEY = Objects.requireNonNull(
            ResourceKey.create(Registries.DIMENSION_TYPE, DYNAMIC_DIMENSION_ID),
            "dynamicDimensionKey"
    );
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final TellusRealtimeManager REALTIME_MANAGER = new TellusRealtimeManager();
    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static @NonNull Identifier id(@NonNull String path) {
        return Objects.requireNonNull(Identifier.fromNamespaceAndPath(MOD_ID, path), "identifier");
    }


    public static void init() {
        if (Platform.isFabric()) {
            Registry.register(BuiltInRegistries.BIOME_SOURCE, id("earth"), EarthBiomeSource.CODEC);
            Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("earth"), EarthChunkGenerator.CODEC);
        }
        // Register payload types using Architectury

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                GeoTpTeleportPayload.TYPE,
                GeoTpTeleportPayload.CODEC,
                Tellus::handleGeoTeleport
        );

        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("geotp")
                    .then(Commands.literal("map")
                            .requires(source -> source.permissions()
                                    .hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                            .executes(context -> openGeoTpMap(context.getSource()))));
            dispatcher.register(Commands.literal("tellus")
                    .then(Commands.literal("weather")
                            .executes(context -> showTellusWeather(context.getSource()))));
        });

        LifecycleEvent.SERVER_STARTED.register(server -> {
            server.execute(() -> {
                var world = server.getLevel(Level.OVERWORLD);
                if (world == null) {
                    return;
                }
                ChunkGenerator generator = world.getChunkSource().getGenerator();
                logOverworldSettings(server, world, generator);
                if (generator instanceof EarthChunkGenerator earthGenerator) {
                    BlockPos spawn = Objects.requireNonNull(earthGenerator.getSpawnPosition(world), "spawnPosition");
                    world.setRespawnData(LevelData.RespawnData.of(world.dimension(), spawn, 0.0F, 0.0F));
                    ensureDynamicDimensionPack(server, world.dimensionTypeRegistration(), world.dimensionType(), earthGenerator);
                }
            });
        });

        LifecycleEvent.SERVER_STOPPING.register(server -> REALTIME_MANAGER.shutdown());
        // Server tick event
        TickEvent.SERVER_POST.register(server -> REALTIME_MANAGER.onServerTick(server));
        //Player join event
        PlayerEvent.PLAYER_JOIN.register(player ->
                REALTIME_MANAGER.onPlayerJoin(player.level().getServer(), player)
        );
        if (Platform.isModLoaded("distanthorizons")) {
            DistantHorizonsIntegration.bootstrap();
        }

        LOGGER.info("Tellus worldgen initialized");
    }

    private static int openGeoTpMap(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Tellus: GeoTP map can only be used by a player."));
            return 0;
        }
        ServerLevel level = player.level();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof EarthChunkGenerator earthGenerator)) {
            source.sendFailure(Component.literal("Tellus: GeoTP map is only available in Tellus worlds."));
            return 0;
        }
        double latitude = clampLatitude(earthGenerator.latitudeFromBlock(player.getZ()));
        double longitude = clampLongitude(earthGenerator.longitudeFromBlock(player.getX()));
        NetworkManager.sendToPlayer(player, new GeoTpOpenMapPayload(latitude, longitude));
        return 1;
    }

    private static int showTellusWeather(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Tellus: /tellus weather can only be used by a player."));
            return 0;
        }
        ServerLevel level = player.level();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof EarthChunkGenerator earthGenerator)) {
            source.sendFailure(Component.literal("Tellus: /tellus weather is only available in Tellus worlds."));
            return 0;
        }

        BlockPos pos = player.blockPosition();
        double latitude = clampLatitude(earthGenerator.latitudeFromBlock(pos.getZ()));
        double longitude = clampLongitude(earthGenerator.longitudeFromBlock(pos.getX()));

        boolean realtimeTime = earthGenerator.settings().realtimeTime();
        boolean realtimeWeather = earthGenerator.settings().realtimeWeather();
        boolean realtimeWeatherActive = realtimeWeather && TellusRealtimeState.isWeatherEnabled();

        TellusRealtimeState.PrecipitationMode mode = TellusRealtimeState.precipitationMode();
        WeatherDisplay weather = realtimeWeatherActive
                ? weatherFromRealtime(mode)
                : weatherFromVanilla(level, pos);

        int offsetSeconds;
        boolean approximateTime;
        if (realtimeTime && REALTIME_MANAGER.hasTimeOffset()) {
            offsetSeconds = REALTIME_MANAGER.currentUtcOffsetSeconds();
            approximateTime = false;
        } else {
            offsetSeconds = approximateUtcOffsetSeconds(longitude);
            approximateTime = true;
        }
        String timeLabel = formatLocalTime(offsetSeconds);
        String utcOffsetLabel = formatUtcOffset(offsetSeconds);

        source.sendSuccess(() -> Component.literal("Tellus Weather")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        String locationText = Objects.requireNonNull(
                String.format(Locale.ROOT, "%.4f, %.4f", latitude, longitude),
                "locationText"
        );
        source.sendSuccess(() -> Component.literal("Location: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(locationText).withStyle(ChatFormatting.AQUA)), false);
        MutableComponent timeLine = Component.literal("Local time: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(timeLabel + " " + utcOffsetLabel).withStyle(ChatFormatting.YELLOW));
        if (approximateTime) {
            timeLine.append(Component.literal(" (approx)").withStyle(ChatFormatting.DARK_GRAY));
        } else if (!realtimeTime) {
            timeLine.append(Component.literal(" (world)").withStyle(ChatFormatting.DARK_GRAY));
        }
        source.sendSuccess(() -> timeLine, false);

        String weatherLabel = Objects.requireNonNull(weather.label(), "weatherLabel");
        ChatFormatting weatherColor = Objects.requireNonNull(weather.color(), "weatherColor");
        MutableComponent weatherLine = Component.literal("Weather: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(weatherLabel).withStyle(weatherColor));
        if (!realtimeWeather) {
            weatherLine.append(Component.literal(" (vanilla)").withStyle(ChatFormatting.DARK_GRAY));
        } else if (!realtimeWeatherActive) {
            weatherLine.append(Component.literal(" (real-time pending)").withStyle(ChatFormatting.DARK_GRAY));
        }
        source.sendSuccess(() -> weatherLine, false);
        return 1;
    }

    private static WeatherDisplay weatherFromRealtime(TellusRealtimeState.PrecipitationMode mode) {
        return switch (mode) {
            case THUNDER -> new WeatherDisplay("Thunder", ChatFormatting.DARK_PURPLE);
            case SNOW -> new WeatherDisplay("Snow", ChatFormatting.AQUA);
            case RAIN -> new WeatherDisplay("Rain", ChatFormatting.BLUE);
            case CLEAR -> new WeatherDisplay("Clear", ChatFormatting.GREEN);
        };
    }

    private static WeatherDisplay weatherFromVanilla(ServerLevel level, @NonNull BlockPos pos) {
        if (level.isThundering()) {
            return new WeatherDisplay("Thunder", ChatFormatting.DARK_PURPLE);
        }
        if (level.isRainingAt(pos)) {
            var biome = level.getBiome(pos).value();
            boolean snow = biome.getPrecipitationAt(pos, level.getSeaLevel()) == net.minecraft.world.level.biome.Biome.Precipitation.SNOW;
            return new WeatherDisplay(snow ? "Snow" : "Rain", snow ? ChatFormatting.AQUA : ChatFormatting.BLUE);
        }
        return new WeatherDisplay("Clear", ChatFormatting.GREEN);
    }

    private static int approximateUtcOffsetSeconds(double longitude) {
        double hours = longitude / 15.0;
        return (int) Math.round(hours * 3600.0);
    }

    private static String formatLocalTime(int offsetSeconds) {
        long localSeconds = System.currentTimeMillis() / 1000L + offsetSeconds;
        long daySeconds = Math.floorMod(localSeconds, 86_400L);
        int hour = (int) (daySeconds / 3600L);
        int minute = (int) ((daySeconds % 3600L) / 60L);
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    private static String formatUtcOffset(int offsetSeconds) {
        int totalMinutes = offsetSeconds / 60;
        int hours = totalMinutes / 60;
        int minutes = Math.abs(totalMinutes % 60);
        return String.format(Locale.ROOT, "UTC%+03d:%02d", hours, minutes);
    }

    private record WeatherDisplay(String label, ChatFormatting color) {}


        private static void handleGeoTeleport(GeoTpTeleportPayload payload, NetworkManager.PacketContext context) {
        if (!Double.isFinite(payload.latitude()) || !Double.isFinite(payload.longitude())) {
            return;
        }
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            ServerLevel level = player.level();
            ChunkGenerator generator = level.getChunkSource().getGenerator();
            if (!(generator instanceof EarthChunkGenerator earthGenerator)) {
                player.sendSystemMessage(Component.literal("Tellus: GeoTP is only available in Tellus worlds."));
                return;
            }
            double latitude = clampLatitude(payload.latitude());
            double longitude = clampLongitude(payload.longitude());
            BlockPos target = earthGenerator.getSurfacePosition(level, latitude, longitude);
            player.teleportTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        });
    }

    private static double clampLatitude(double latitude) {
        return Mth.clamp(latitude, -85.05112878, 85.05112878);
    }

    private static double clampLongitude(double longitude) {
        return Mth.clamp(longitude, -180.0, 180.0);
    }

    private static void logOverworldSettings(MinecraftServer server, Level world, ChunkGenerator generator) {
        DimensionType worldType = world.dimensionType();
        LOGGER.info(
                "Overworld dimension type: {}",
                describeDimensionType(worldType)
        );
        LOGGER.info(
                "Overworld generator: type={}, minY={}, height={}",
                generator.getClass().getSimpleName(),
                generator.getMinY(),
                generator.getGenDepth()
        );
        Registry<LevelStem> stems = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
        Optional<Holder.Reference<LevelStem>> stemRef = stems.get(LevelStem.OVERWORLD);
        if (stemRef.isEmpty()) {
            LOGGER.warn("Overworld level stem missing from registry");
            return;
        }
        LevelStem stem = stemRef.get().value();
        DimensionType stemType = stem.type().value();
        LOGGER.info(
                "Overworld level stem: dimensionType={}, generatorType={}",
                describeDimensionType(stemType),
                stem.generator().getClass().getSimpleName()
        );
    }

    private static String describeDimensionType(DimensionType type) {
        return "minY=" + type.minY() + ",height=" + type.height() + ",logicalHeight=" + type.logicalHeight();
    }

    private static void ensureDynamicDimensionPack(
            MinecraftServer server,
            Holder<DimensionType> dimensionTypeHolder,
            DimensionType currentDimensionType,
            EarthChunkGenerator earthGenerator
    ) {
        ResourceKey<DimensionType> dimensionKey = resolveTellusDimensionKey(dimensionTypeHolder).orElse(null);
        if (dimensionKey == null) {
            return;
        }
        EarthGeneratorSettings settings = earthGenerator.settings();
        EarthGeneratorSettings.HeightLimits limits = EarthGeneratorSettings.resolveHeightLimits(settings);
        DimensionType updatedType = EarthGeneratorSettings.applyHeightLimits(currentDimensionType, limits);

        DynamicOps<JsonElement> jsonOps = Objects.requireNonNull(JsonOps.INSTANCE, "jsonOps");
        RegistryOps<JsonElement> registryOps = RegistryOps.create(jsonOps, server.registryAccess());
        JsonElement dimensionJson = DimensionType.DIRECT_CODEC.encodeStart(registryOps, updatedType)
                .resultOrPartial(message -> LOGGER.error("Failed to encode dynamic dimension type: {}", message))
                .orElse(null);
        if (dimensionJson == null) {
            return;
        }

        Path packDir = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(DYNAMIC_DIMENSION_PACK_NAME);
        Path packMetaPath = packDir.resolve("pack.mcmeta");
        Identifier dimensionId = dimensionKey.identifier();
        Path dimensionPath = packDir.resolve(
                "data/" + dimensionId.getNamespace() + "/dimension_type/" + dimensionId.getPath() + ".json"
        );
        try {
            Files.createDirectories(dimensionPath.getParent());
            writeJson(packMetaPath, createPackMeta());
            writeJson(dimensionPath, dimensionJson);
        } catch (IOException e) {
            LOGGER.warn("Failed to persist dynamic dimension type pack", e);
            return;
        }

        enableDynamicPack(server.getWorldData());
    }

    private static Optional<ResourceKey<DimensionType>> resolveTellusDimensionKey(
            Holder<DimensionType> dimensionTypeHolder
    ) {
        return dimensionTypeHolder.unwrapKey().filter(Tellus::isTellusDimensionKey);
    }

    private static boolean isTellusDimensionKey(ResourceKey<DimensionType> key) {
        return key.equals(DYNAMIC_DIMENSION_KEY) || key.equals(EARTH_DIMENSION_KEY);
    }

    private static JsonObject createPackMeta() {
        JsonObject pack = new JsonObject();
        int packFormat = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA).major();
        pack.addProperty("pack_format", packFormat);
        pack.addProperty("description", "Tellus dynamic dimension settings");
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root;
    }

    private static void writeJson(Path path, JsonElement payload) throws IOException {
        try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(payload, writer);
        }
    }

    private static void enableDynamicPack(WorldData worldData) {
        WorldDataConfiguration configuration = worldData.getDataConfiguration();
        DataPackConfig dataPacks = configuration.dataPacks();
        List<String> enabled = new ArrayList<>(dataPacks.getEnabled());
        List<String> disabled = new ArrayList<>(dataPacks.getDisabled());
        if (!enabled.contains(DYNAMIC_DIMENSION_PACK_ID)) {
            enabled.add(DYNAMIC_DIMENSION_PACK_ID);
        }
        disabled.remove(DYNAMIC_DIMENSION_PACK_ID);
        if (enabled.equals(dataPacks.getEnabled()) && disabled.equals(dataPacks.getDisabled())) {
            return;
        }
        WorldDataConfiguration updated = new WorldDataConfiguration(
                new DataPackConfig(enabled, disabled),
                configuration.enabledFeatures()
        );
        worldData.setDataConfiguration(updated);
    }
}

