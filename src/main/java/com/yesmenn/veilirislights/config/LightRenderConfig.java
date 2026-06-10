package com.yesmenn.veilirislights.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yesmenn.veilirislights.VeilIrisLights;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LightRenderConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("veil_iris_lights.json");

    public static final double DEFAULT_EXPOSURE = 0.24;
    public static final double DEFAULT_COLOR_STRENGTH = 2.45;
    public static final double DEFAULT_COLOR_SATURATION = 1.15;
    public static final double DEFAULT_NEUTRAL_LIFT = 0.28;
    public static final double DEFAULT_LUMINANCE_BOOST_LIMIT = 1.6;
    public static final double MAX_EXPOSURE = 2.0;
    public static final double MAX_COLOR_STRENGTH = 12.0;
    public static final double MAX_LUMINANCE_BOOST_LIMIT = 1.6;

    private static LightRenderConfig instance = defaults();

    public RenderQuality quality = RenderQuality.HIGH;
    public double exposure = DEFAULT_EXPOSURE;
    public double colorStrength = DEFAULT_COLOR_STRENGTH;
    public double colorSaturation = DEFAULT_COLOR_SATURATION;
    public double neutralLift = DEFAULT_NEUTRAL_LIFT;
    public double luminanceBoostLimit = DEFAULT_LUMINANCE_BOOST_LIMIT;

    private LightRenderConfig() {
    }

    public static LightRenderConfig get() {
        return instance;
    }

    public static void load() {
        if (!Files.isRegularFile(PATH)) {
            instance = defaults();
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            LightRenderConfig loaded = GSON.fromJson(reader, LightRenderConfig.class);
            instance = loaded != null ? loaded : defaults();
            instance.clamp();
        } catch (Exception e) {
            VeilIrisLights.LOGGER.warn("Failed to load {}, using defaults", PATH, e);
            instance = defaults();
        }
    }

    public static void save() {
        instance.clamp();
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            VeilIrisLights.LOGGER.error("Failed to save {}", PATH, e);
        }
    }

    public void reset() {
        this.quality = RenderQuality.HIGH;
        this.exposure = DEFAULT_EXPOSURE;
        this.colorStrength = DEFAULT_COLOR_STRENGTH;
        this.colorSaturation = DEFAULT_COLOR_SATURATION;
        this.neutralLift = DEFAULT_NEUTRAL_LIFT;
        this.luminanceBoostLimit = DEFAULT_LUMINANCE_BOOST_LIMIT;
    }

    private void clamp() {
        if (this.quality == null) {
            this.quality = RenderQuality.HIGH;
        }
        this.exposure = clamp(this.exposure, 0.01, MAX_EXPOSURE);
        this.colorStrength = clamp(this.colorStrength, 0.0, MAX_COLOR_STRENGTH);
        if (this.colorSaturation <= 0.0) {
            this.colorSaturation = DEFAULT_COLOR_SATURATION;
        }
        this.colorSaturation = clamp(this.colorSaturation, 0.5, 2.0);
        this.neutralLift = clamp(this.neutralLift, 0.0, 2.0);
        this.luminanceBoostLimit = clamp(
                this.luminanceBoostLimit,
                1.0,
                MAX_LUMINANCE_BOOST_LIMIT);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static LightRenderConfig defaults() {
        return new LightRenderConfig();
    }

    public enum RenderQuality {
        PERFORMANCE("option.veil_iris_lights.quality.performance", false, false),
        BALANCED("option.veil_iris_lights.quality.balanced", false, true),
        HIGH("option.veil_iris_lights.quality.high", true, true);

        private final String translationKey;
        private final boolean detailedNormals;
        private final boolean voxelShadows;

        RenderQuality(String translationKey, boolean detailedNormals, boolean voxelShadows) {
            this.translationKey = translationKey;
            this.detailedNormals = detailedNormals;
            this.voxelShadows = voxelShadows;
        }

        public String translationKey() {
            return this.translationKey;
        }

        public boolean detailedNormals() {
            return this.detailedNormals;
        }

        public boolean voxelShadows() {
            return this.voxelShadows;
        }

        public RenderQuality next() {
            RenderQuality[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }
}
