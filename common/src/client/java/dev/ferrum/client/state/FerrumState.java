package dev.ferrum.client.state;

import dev.ferrum.client.config.FerrumConfig;

public final class FerrumState {
    public static volatile boolean entityCulling = true;
    public static volatile double entityCullDistance = 64.0;
    public static volatile boolean cullPlayers = false;
    public static volatile boolean blockEntityCulling = true;
    public static volatile double blockEntityCullDistance = 64.0;
    public static volatile boolean particleLimiting = true;
    public static volatile int maxParticles = 4000;
    public static volatile double particleDistance = 48.0;
    public static volatile boolean keepLimitedParticles = true;
    public static volatile boolean textWidthCache = true;
    public static volatile int dynamicLightRadius = 10;
    public static volatile int dynamicLightMaxSources = 12;
    public static volatile boolean dynamicLightEntities = true;
    public static volatile boolean dynamicLightItems = true;

    public static volatile boolean entityTickCulling = true;
    public static volatile int ambientParticleDensity = 100;
    public static volatile boolean animateOnlyVisible = true;
    public static volatile boolean lightmapCache = true;
    public static volatile boolean renderPassOptimization = true;
    public static volatile boolean faceCulling = true;
    /** Cutout quads moved to the solid pass by the chunk builder (cumulative, approximate: racy across builder threads). */
    public static volatile long quadsPromoted;
    public static volatile boolean replaceVideoSettings = true;
    public static volatile boolean fsr = true;
    public static volatile int fsrSharpness = 80;
    /** Set if the FSR shaders failed to compile or run on this device; render scale then uses bilinear. */
    public static volatile boolean fsrFailed;
    public static volatile boolean occlusionCulling = true;
    public static volatile boolean occlusionBlockEntities = true;
    public static volatile boolean animWater = true;
    public static volatile boolean animLava = true;
    public static volatile boolean animFire = true;
    public static volatile boolean animPortal = true;
    public static volatile boolean animBlocks = true;
    public static volatile boolean particleBlockBreak = true;
    public static volatile boolean particleRain = true;
    public static volatile boolean particleExplosions = true;
    public static volatile boolean particleCampfireSmoke = true;
    public static volatile boolean particlePotion = true;
    public static volatile boolean particleLeaves = true;
    public static volatile boolean particleFlames = true;
    public static volatile boolean renderItemFrames = true;
    public static volatile boolean renderArmorStands = true;
    public static volatile boolean renderPaintings = true;
    public static volatile boolean renderNameTags = true;
    public static volatile boolean renderBeaconBeams = true;
    public static volatile boolean renderEnchantBook = true;
    public static long occlusionHiddenPerSecond;
    public static volatile long occlusionHidden;
    public static volatile int lightmapUpdates;
    public static volatile int lightmapSkipped;
    public static volatile boolean forceMultiDraw = false;
    public static volatile int renderScale = 100;
    public static volatile boolean renderScaleSharp = false;
    public static volatile int renderScaleActiveWidth;
    public static volatile int renderScaleActiveHeight;
    public static volatile boolean multiDrawActive;
    public static volatile boolean multiDrawVanilla;
    public static volatile boolean multiDrawSupported;
    public static volatile int clientTick;
    public static volatile long entityTicksSkipped;
    public static long entityTicksSkippedPerSecond;

    // Sodium Extra style detail toggles
    public static volatile boolean fog = true;
    public static volatile boolean clouds = true;
    public static volatile boolean weather = true;
    public static volatile boolean sky = true;
    public static volatile boolean stars = true;
    public static volatile boolean sun = true;
    public static volatile boolean moon = true;
    public static volatile boolean sunriseSunset = true;

    public static volatile long entityCulled;
    public static volatile long blockEntityCulled;
    public static volatile int particleCount;
    public static volatile int dynamicLightSources;
    public static volatile int dynamicLightCells;

    public static long entityCulledPerSecond;
    public static long blockEntityCulledPerSecond;
    public static String lastTuningAction = "none";

    private FerrumState() {
    }

    public static void apply(FerrumConfig config) {
        entityCulling = config.entityCulling;
        entityCullDistance = config.entityDistance;
        cullPlayers = config.cullPlayers;
        blockEntityCulling = config.blockEntityCulling;
        blockEntityCullDistance = config.blockEntityDistance;
        particleLimiting = config.particleLimiting;
        maxParticles = config.maxParticles;
        particleDistance = config.particleDistance;
        keepLimitedParticles = config.keepLimitedParticles;
        textWidthCache = config.textWidthCache;
        dynamicLightRadius = config.dynamicLightRadius;
        dynamicLightMaxSources = config.dynamicLightMaxSources;
        dynamicLightEntities = config.dynamicLightEntities;
        dynamicLightItems = config.dynamicLightItems;
        // EntityCulling (tr7zw) already skips ticking and drawing hidden entities; never run both.
        entityTickCulling = config.entityTickCulling && !dev.ferrum.client.compat.ModCompat.ENTITY_CULLING;
        ambientParticleDensity = config.ambientParticleDensity;
        animateOnlyVisible = config.animateOnlyVisible;
        lightmapCache = config.lightmapCache;
        renderPassOptimization = config.renderPassOptimization;
        faceCulling = config.faceCulling;
        replaceVideoSettings = config.replaceVideoSettings;
        fsr = config.fsr;
        fsrSharpness = config.fsrSharpness;
        occlusionCulling = config.occlusionCulling && !dev.ferrum.client.compat.ModCompat.ENTITY_CULLING;
        occlusionBlockEntities = config.occlusionBlockEntities;
        animWater = config.animWater;
        animLava = config.animLava;
        animFire = config.animFire;
        animPortal = config.animPortal;
        animBlocks = config.animBlocks;
        particleBlockBreak = config.particleBlockBreak;
        particleRain = config.particleRain;
        particleExplosions = config.particleExplosions;
        particleCampfireSmoke = config.particleCampfireSmoke;
        particlePotion = config.particlePotion;
        particleLeaves = config.particleLeaves;
        particleFlames = config.particleFlames;
        renderItemFrames = config.renderItemFrames;
        renderArmorStands = config.renderArmorStands;
        renderPaintings = config.renderPaintings;
        renderNameTags = config.renderNameTags;
        renderBeaconBeams = config.renderBeaconBeams;
        renderEnchantBook = config.renderEnchantBook;
        dev.ferrum.client.culling.OcclusionCuller.setEnabled(occlusionCulling);
        forceMultiDraw = config.forceMultiDraw;
        renderScale = config.renderScale;
        renderScaleSharp = config.renderScaleSharp;
        fog = config.fog;
        clouds = config.clouds;
        weather = config.weather;
        sky = config.sky;
        stars = config.stars;
        sun = config.sun;
        moon = config.moon;
        sunriseSunset = config.sunriseSunset;
    }

    public static void snapshotCounters() {
        entityCulledPerSecond = entityCulled;
        blockEntityCulledPerSecond = blockEntityCulled;
        entityCulled = 0;
        occlusionHiddenPerSecond = occlusionHidden;
        occlusionHidden = 0;
        entityTicksSkippedPerSecond = entityTicksSkipped;
        entityTicksSkipped = 0;
        blockEntityCulled = 0;
    }
}
