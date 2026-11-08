package dev.ferrum.client.tuning;

import dev.ferrum.client.FerrumClient;
import dev.ferrum.client.config.FerrumConfig;
import dev.ferrum.client.state.FerrumState;
import net.minecraft.client.Minecraft;

public final class AdaptiveTuner {
    private static int sampleTicks;
    private static int lowStreak;
    private static int highStreak;
    /** Ticks since the current level was joined; FPS during chunk loading is not sampled. */
    private static int worldTicks;
    private static Object lastLevel;
    private static final int WARMUP_TICKS = 200;

    private static double baselineEntityDistance = 64.0;
    private static double baselineBlockEntityDistance = 64.0;
    private static double baselineParticleDistance = 48.0;
    private static int baselineMaxParticles = 4000;
    private static int baselineLightRadius = 10;
    private static int baselineLightSources = 12;

    private AdaptiveTuner() {
    }

    public static void onConfigLoaded(FerrumConfig config) {
        baselineEntityDistance = config.entityDistance;
        baselineBlockEntityDistance = config.blockEntityDistance;
        baselineParticleDistance = config.particleDistance;
        baselineMaxParticles = config.maxParticles;
        baselineLightRadius = config.dynamicLightRadius;
        baselineLightSources = config.dynamicLightMaxSources;
        lowStreak = 0;
        highStreak = 0;
        sampleTicks = 0;
        FerrumState.lastTuningAction = "none";
    }

    public static void onClientTick(Minecraft minecraft) {
        FerrumConfig config = FerrumClient.CONFIG;
        if (!config.adaptiveQuality) {
            return;
        }
        if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
            return;
        }
        if (minecraft.level != lastLevel) {
            lastLevel = minecraft.level;
            worldTicks = 0;
            lowStreak = 0;
            highStreak = 0;
        }
        if (worldTicks < WARMUP_TICKS) {
            worldTicks++;
            return;
        }
        if (++sampleTicks < 40) {
            return;
        }
        sampleTicks = 0;

        int fps = minecraft.getFps();
        if (fps <= 0) {
            return;
        }
        int target = Math.max(20, config.targetFps);

        if (fps < target * 0.75F) {
            lowStreak++;
            highStreak = 0;
        } else if (fps > target * 1.15F) {
            highStreak++;
            lowStreak = 0;
        } else {
            lowStreak = 0;
            highStreak = 0;
        }

        if (lowStreak >= 2) {
            lowStreak = 0;
            degrade();
        } else if (highStreak >= 2) {
            highStreak = 0;
            improve();
        }
    }

    private static void degrade() {
        boolean changed = false;
        double entity = FerrumState.entityCullDistance;
        if (entity > 24.0) {
            FerrumState.entityCullDistance = Math.max(24.0, entity * 0.8);
            changed = true;
        }
        double blockEntity = FerrumState.blockEntityCullDistance;
        if (blockEntity > 24.0) {
            FerrumState.blockEntityCullDistance = Math.max(24.0, blockEntity * 0.8);
            changed = true;
        }
        double particles = FerrumState.particleDistance;
        if (particles > 12.0) {
            FerrumState.particleDistance = Math.max(12.0, particles * 0.8);
            changed = true;
        }
        int particleCap = FerrumState.maxParticles;
        if (particleCap > 600) {
            FerrumState.maxParticles = Math.max(600, (int) (particleCap * 0.8));
            changed = true;
        }
        if (FerrumState.dynamicLightRadius > 4) {
            FerrumState.dynamicLightRadius--;
            changed = true;
        }
        if (FerrumState.dynamicLightMaxSources > 4) {
            FerrumState.dynamicLightMaxSources = Math.max(4, FerrumState.dynamicLightMaxSources - 2);
            changed = true;
        }
        if (changed) {
            FerrumState.lastTuningAction = "degraded";
        }
    }

    private static void improve() {
        boolean changed = false;
        if (FerrumState.entityCullDistance < baselineEntityDistance) {
            FerrumState.entityCullDistance = Math.min(baselineEntityDistance, FerrumState.entityCullDistance * 1.25 + 4.0);
            changed = true;
        }
        if (FerrumState.blockEntityCullDistance < baselineBlockEntityDistance) {
            FerrumState.blockEntityCullDistance = Math.min(baselineBlockEntityDistance, FerrumState.blockEntityCullDistance * 1.25 + 4.0);
            changed = true;
        }
        if (FerrumState.particleDistance < baselineParticleDistance) {
            FerrumState.particleDistance = Math.min(baselineParticleDistance, FerrumState.particleDistance * 1.25 + 4.0);
            changed = true;
        }
        if (FerrumState.maxParticles < baselineMaxParticles) {
            FerrumState.maxParticles = Math.min(baselineMaxParticles, (int) (FerrumState.maxParticles * 1.25) + 128);
            changed = true;
        }
        if (FerrumState.dynamicLightRadius < baselineLightRadius) {
            FerrumState.dynamicLightRadius++;
            changed = true;
        }
        if (FerrumState.dynamicLightMaxSources < baselineLightSources) {
            FerrumState.dynamicLightMaxSources++;
            changed = true;
        }
        boolean atBaseline = FerrumState.entityCullDistance >= baselineEntityDistance
            && FerrumState.blockEntityCullDistance >= baselineBlockEntityDistance
            && FerrumState.particleDistance >= baselineParticleDistance
            && FerrumState.maxParticles >= baselineMaxParticles
            && FerrumState.dynamicLightRadius >= baselineLightRadius
            && FerrumState.dynamicLightMaxSources >= baselineLightSources;
        if (atBaseline) {
            FerrumState.lastTuningAction = "baseline";
        } else if (changed) {
            FerrumState.lastTuningAction = "recovering";
        }
    }
}
