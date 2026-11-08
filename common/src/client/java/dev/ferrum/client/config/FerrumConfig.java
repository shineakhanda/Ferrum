package dev.ferrum.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.ferrum.client.FerrumClient;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;

public final class FerrumConfig {
    public static final int CURRENT_VERSION = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public int configVersion = CURRENT_VERSION;
    public String preset = "AUTO";
    public boolean firstRun = true;
    public boolean autoApplyVanillaOptions = true;

    public boolean entityCulling = true;
    public double entityDistance = 64.0;
    public boolean cullPlayers = false;
    public boolean blockEntityCulling = true;
    public double blockEntityDistance = 64.0;

    public boolean particleLimiting = true;
    public int maxParticles = 4000;
    public double particleDistance = 48.0;
    public boolean keepLimitedParticles = true;

    public boolean dynamicLights = true;
    public int dynamicLightRadius = 10;
    public boolean dynamicLightEntities = true;
    public boolean dynamicLightItems = true;
    public int dynamicLightMaxSources = 12;

    public boolean adaptiveQuality = true;
    public int targetFps = 60;
    public int fpsCap = 0;
    public boolean textWidthCache = true;

    public boolean entityTickCulling = true;
    public int ambientParticleDensity = 100;
    public boolean animateOnlyVisible = true;
    public boolean lightmapCache = true;
    public boolean renderPassOptimization = true;
    public boolean faceCulling = true;
    public boolean replaceVideoSettings = true;
    public boolean occlusionCulling = true;
    public boolean occlusionBlockEntities = true;
    public boolean animWater = true;
    public boolean animLava = true;
    public boolean animFire = true;
    public boolean animPortal = true;
    public boolean animBlocks = true;
    public boolean particleBlockBreak = true;
    public boolean particleRain = true;
    public boolean particleExplosions = true;
    public boolean particleCampfireSmoke = true;
    public boolean particlePotion = true;
    public boolean particleLeaves = true;
    public boolean particleFlames = true;
    public boolean renderItemFrames = true;
    public boolean renderArmorStands = true;
    public boolean renderPaintings = true;
    public boolean renderNameTags = true;
    public boolean renderBeaconBeams = true;
    public boolean renderEnchantBook = true;
    public boolean forceMultiDraw = false;
    public int renderScale = 100;
    public boolean renderScaleSharp = false;
    public boolean fsr = true;
    public int fsrSharpness = 80;

    public boolean fog = true;
    public boolean clouds = true;
    public boolean weather = true;
    public boolean sky = true;
    public boolean stars = true;
    public boolean sun = true;
    public boolean moon = true;
    public boolean sunriseSunset = true;

    public boolean irisSafeMode = true;
    public String vulkanMode = "AUTO";
    public boolean vulkanApplied = false;
    public boolean debugHud = false;

    public transient Path configPath;
    /** Set when migrating a v1 config whose preset wrote a 30/60 FPS limit into options.txt. */
    public transient boolean liftLegacyFrameCap;

    public static FerrumConfig load() {
        FerrumConfig config = new FerrumConfig();
        Path path = FabricLoader.getInstance().getConfigDir().resolve("ferrum.json");
        config.configPath = path;

        if (Files.exists(path)) {
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                FerrumConfig loaded = GSON.fromJson(reader, FerrumConfig.class);
                if (loaded != null) {
                    loaded.configPath = path;
                    config = loaded;
                }
            } catch (JsonSyntaxException | IOException e) {
                FerrumClient.LOGGER.error("Could not read Ferrum config, backing it up and using defaults", e);
                try {
                    Path backup = path.resolveSibling("ferrum.json.broken");
                    Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException moveError) {
                    FerrumClient.LOGGER.warn("Could not back up broken Ferrum config", moveError);
                }
            }
        }

        if (Files.exists(path) && config.configVersion < 2) {
            // Version 1 presets capped the frame rate (POTATO 30, MOBILE/LOW_END 60) and wrote it into
            // options.txt, which hid every gain. Drop the cap once; the client lifts the vanilla limit.
            config.fpsCap = 0;
            config.liftLegacyFrameCap = true;
            config.configVersion = CURRENT_VERSION;
        }
        config.sanitize();
        return config;
    }

    public void save() {
        if (this.configPath == null) {
            this.configPath = FabricLoader.getInstance().getConfigDir().resolve("ferrum.json");
        }

        try {
            Files.createDirectories(this.configPath.getParent());
            Path temp = this.configPath.resolveSibling("ferrum.json.tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
            Files.move(temp, this.configPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            FerrumClient.LOGGER.error("Could not save Ferrum config", e);
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public FerrumConfig copy() {
        FerrumConfig copy = GSON.fromJson(this.toJson(), FerrumConfig.class);
        copy.configPath = this.configPath;
        return copy;
    }

    public Preset preset() {
        return Preset.byId(this.preset);
    }

    public void setPreset(Preset preset) {
        this.preset = preset.id();
        this.applyPresetValues(preset);
    }

    public void applyPresetValues(Preset preset) {
        switch (preset) {
            case POTATO -> {
                this.renderScale = 60;
                this.ambientParticleDensity = 33;
                this.entityCulling = true;
                this.entityDistance = 32.0;
                this.blockEntityCulling = true;
                this.blockEntityDistance = 32.0;
                this.particleLimiting = true;
                this.maxParticles = 1200;
                this.particleDistance = 24.0;
                this.dynamicLights = true;
                this.dynamicLightRadius = 6;
                this.dynamicLightMaxSources = 6;
                this.adaptiveQuality = true;
                this.targetFps = 30;
                this.fpsCap = 0;
                this.textWidthCache = true;
            }
            case MOBILE -> {
                this.renderScale = 70;
                this.ambientParticleDensity = 50;
                this.entityCulling = true;
                this.entityDistance = 40.0;
                this.blockEntityCulling = true;
                this.blockEntityDistance = 40.0;
                this.particleLimiting = true;
                this.maxParticles = 2000;
                this.particleDistance = 32.0;
                this.dynamicLights = true;
                this.dynamicLightRadius = 6;
                this.dynamicLightMaxSources = 8;
                this.adaptiveQuality = true;
                this.targetFps = 60;
                this.fpsCap = 0;
                this.textWidthCache = true;
            }
            case LOW_END -> {
                this.renderScale = 85;
                this.ambientParticleDensity = 50;
                this.entityCulling = true;
                this.entityDistance = 48.0;
                this.blockEntityCulling = true;
                this.blockEntityDistance = 48.0;
                this.particleLimiting = true;
                this.maxParticles = 3000;
                this.particleDistance = 40.0;
                this.dynamicLights = true;
                this.dynamicLightRadius = 8;
                this.dynamicLightMaxSources = 10;
                this.adaptiveQuality = true;
                this.targetFps = 60;
                this.fpsCap = 0;
                this.textWidthCache = true;
            }
            case BALANCED -> {
                this.renderScale = 100;
                this.ambientParticleDensity = 100;
                this.entityCulling = true;
                this.entityDistance = 64.0;
                this.blockEntityCulling = true;
                this.blockEntityDistance = 64.0;
                this.particleLimiting = true;
                this.maxParticles = 4500;
                this.particleDistance = 48.0;
                this.dynamicLights = true;
                this.dynamicLightRadius = 10;
                this.dynamicLightMaxSources = 12;
                this.adaptiveQuality = true;
                this.targetFps = 90;
                this.fpsCap = 0;
                this.textWidthCache = true;
            }
            case QUALITY -> {
                this.renderScale = 100;
                this.ambientParticleDensity = 100;
                this.entityCulling = true;
                this.entityDistance = 96.0;
                this.blockEntityCulling = true;
                this.blockEntityDistance = 96.0;
                this.particleLimiting = true;
                this.maxParticles = 8000;
                this.particleDistance = 64.0;
                this.dynamicLights = true;
                this.dynamicLightRadius = 12;
                this.dynamicLightMaxSources = 16;
                this.adaptiveQuality = true;
                this.targetFps = 120;
                this.fpsCap = 0;
                this.textWidthCache = true;
            }
            case AUTO -> {
            }
        }
    }

    public void sanitize() {
        this.configVersion = CURRENT_VERSION;
        this.entityDistance = clamp(this.entityDistance, 16.0, 256.0, 64.0);
        this.blockEntityDistance = clamp(this.blockEntityDistance, 16.0, 256.0, 64.0);
        this.particleDistance = clamp(this.particleDistance, 8.0, 256.0, 48.0);
        this.maxParticles = clamp(this.maxParticles, 100, 50000, 4000);
        this.dynamicLightRadius = clamp(this.dynamicLightRadius, 0, 15, 10);
        this.dynamicLightMaxSources = clamp(this.dynamicLightMaxSources, 1, 64, 12);
        this.targetFps = clamp(this.targetFps, 10, 260, 60);
        this.fpsCap = clamp(this.fpsCap, 0, 260, 0);
        this.ambientParticleDensity = clamp(this.ambientParticleDensity, 10, 100, 100);
        this.renderScale = clamp(this.renderScale, 50, 100, 100);
        this.fsrSharpness = clamp(this.fsrSharpness, 0, 100, 80);
        if (this.preset == null) {
            this.preset = "AUTO";
        }
        if (this.vulkanMode == null) {
            this.vulkanMode = "AUTO";
        }
        this.vulkanMode = this.vulkanMode.toUpperCase(java.util.Locale.ROOT);
        if (!this.vulkanMode.equals("AUTO") && !this.vulkanMode.equals("DEFAULT")
            && !this.vulkanMode.equals("OPENGL") && !this.vulkanMode.equals("VULKAN")) {
            this.vulkanMode = "AUTO";
        }
    }

    public boolean setValue(String key, String value) {
        try {
            switch (key) {
                case "entityCulling" -> this.entityCulling = parseBoolean(value);
                case "entityDistance" -> this.entityDistance = clamp(Double.parseDouble(value), 16.0, 256.0, this.entityDistance);
                case "cullPlayers" -> this.cullPlayers = parseBoolean(value);
                case "blockEntityCulling" -> this.blockEntityCulling = parseBoolean(value);
                case "blockEntityDistance" -> this.blockEntityDistance = clamp(Double.parseDouble(value), 16.0, 256.0, this.blockEntityDistance);
                case "particleLimiting" -> this.particleLimiting = parseBoolean(value);
                case "maxParticles" -> this.maxParticles = clamp(Integer.parseInt(value), 100, 50000, this.maxParticles);
                case "particleDistance" -> this.particleDistance = clamp(Double.parseDouble(value), 8.0, 256.0, this.particleDistance);
                case "keepLimitedParticles" -> this.keepLimitedParticles = parseBoolean(value);
                case "dynamicLights" -> this.dynamicLights = parseBoolean(value);
                case "dynamicLightRadius" -> this.dynamicLightRadius = clamp(Integer.parseInt(value), 0, 15, this.dynamicLightRadius);
                case "dynamicLightEntities" -> this.dynamicLightEntities = parseBoolean(value);
                case "dynamicLightItems" -> this.dynamicLightItems = parseBoolean(value);
                case "dynamicLightMaxSources" -> this.dynamicLightMaxSources = clamp(Integer.parseInt(value), 1, 64, this.dynamicLightMaxSources);
                case "adaptiveQuality" -> this.adaptiveQuality = parseBoolean(value);
                case "targetFps" -> this.targetFps = clamp(Integer.parseInt(value), 10, 260, this.targetFps);
                case "fpsCap" -> this.fpsCap = clamp(Integer.parseInt(value), 0, 260, this.fpsCap);
                case "textWidthCache" -> this.textWidthCache = parseBoolean(value);
                case "irisSafeMode" -> this.irisSafeMode = parseBoolean(value);
                case "debugHud" -> this.debugHud = parseBoolean(value);
                case "renderScale" -> this.renderScale = clamp(Integer.parseInt(value), 50, 100, this.renderScale);
                case "renderScaleSharp" -> this.renderScaleSharp = parseBoolean(value);
                case "forceMultiDraw" -> this.forceMultiDraw = parseBoolean(value);
                case "animateOnlyVisible" -> this.animateOnlyVisible = parseBoolean(value);
                case "lightmapCache" -> this.lightmapCache = parseBoolean(value);
                case "renderPassOptimization" -> this.renderPassOptimization = parseBoolean(value);
                case "faceCulling" -> this.faceCulling = parseBoolean(value);
                case "replaceVideoSettings" -> this.replaceVideoSettings = parseBoolean(value);
                case "fsr" -> this.fsr = parseBoolean(value);
                case "fsrSharpness" -> this.fsrSharpness = clamp(Integer.parseInt(value), 0, 100, this.fsrSharpness);
                case "occlusionCulling" -> this.occlusionCulling = parseBoolean(value);
                case "occlusionBlockEntities" -> this.occlusionBlockEntities = parseBoolean(value);
                case "animWater" -> this.animWater = parseBoolean(value);
                case "animLava" -> this.animLava = parseBoolean(value);
                case "animFire" -> this.animFire = parseBoolean(value);
                case "animPortal" -> this.animPortal = parseBoolean(value);
                case "animBlocks" -> this.animBlocks = parseBoolean(value);
                case "particleBlockBreak" -> this.particleBlockBreak = parseBoolean(value);
                case "particleRain" -> this.particleRain = parseBoolean(value);
                case "particleExplosions" -> this.particleExplosions = parseBoolean(value);
                case "particleCampfireSmoke" -> this.particleCampfireSmoke = parseBoolean(value);
                case "particlePotion" -> this.particlePotion = parseBoolean(value);
                case "particleLeaves" -> this.particleLeaves = parseBoolean(value);
                case "particleFlames" -> this.particleFlames = parseBoolean(value);
                case "renderItemFrames" -> this.renderItemFrames = parseBoolean(value);
                case "renderArmorStands" -> this.renderArmorStands = parseBoolean(value);
                case "renderPaintings" -> this.renderPaintings = parseBoolean(value);
                case "renderNameTags" -> this.renderNameTags = parseBoolean(value);
                case "renderBeaconBeams" -> this.renderBeaconBeams = parseBoolean(value);
                case "renderEnchantBook" -> this.renderEnchantBook = parseBoolean(value);
                case "entityTickCulling" -> this.entityTickCulling = parseBoolean(value);
                case "ambientParticleDensity" -> this.ambientParticleDensity = clamp(Integer.parseInt(value), 10, 100, this.ambientParticleDensity);
                case "fog" -> this.fog = parseBoolean(value);
                case "clouds" -> this.clouds = parseBoolean(value);
                case "weather" -> this.weather = parseBoolean(value);
                case "sky" -> this.sky = parseBoolean(value);
                case "stars" -> this.stars = parseBoolean(value);
                case "sun" -> this.sun = parseBoolean(value);
                case "moon" -> this.moon = parseBoolean(value);
                case "sunriseSunset" -> this.sunriseSunset = parseBoolean(value);
                case "autoApplyVanillaOptions" -> this.autoApplyVanillaOptions = parseBoolean(value);
                case "vulkanMode" -> {
                    String normalized = value.toUpperCase(java.util.Locale.ROOT);
                    if (!normalized.equals("AUTO") && !normalized.equals("DEFAULT")
                        && !normalized.equals("OPENGL") && !normalized.equals("VULKAN")) {
                        return false;
                    }
                    this.vulkanMode = normalized;
                }
                default -> {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean parseBoolean(String value) {
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on")
            || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("1");
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max, int fallback) {
        return Math.max(min, Math.min(max, value));
    }
}
