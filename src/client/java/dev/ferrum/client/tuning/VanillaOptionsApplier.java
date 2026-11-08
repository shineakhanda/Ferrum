package dev.ferrum.client.tuning;

import dev.ferrum.client.FerrumClient;
import dev.ferrum.client.config.FerrumConfig;
import dev.ferrum.client.config.Preset;
import dev.ferrum.client.hardware.HardwareProfile;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;

public final class VanillaOptionsApplier {
    private VanillaOptionsApplier() {
    }

    /**
     * Settings that cost far more on phones and GL translation layers (MobileGlues, GL4ES, VirGL,
     * Zink, ANGLE) than on desktop drivers:
     * <ul>
     *   <li>Improved transparency (OIT) needs extra full-screen targets and passes; tile-based mobile
     *       GPUs pay for every extra render target in bandwidth.</li>
     *   <li>Mipmaps and anisotropy multiply texture memory on devices with 2-4 GB shared RAM.</li>
     *   <li>Biome blend radius is CPU work in every chunk rebuild; big phones still have slow cores.</li>
     *   <li>Chunk fade-in keeps sections in the translucent path longer.</li>
     *   <li>Vignette is a full-screen blended quad: pure fill-rate cost.</li>
     * </ul>
     * Only lowers values; never raises a setting the user turned down.
     */
    private static boolean applyMobileAndTranslationTuning(Minecraft minecraft, HardwareProfile profile, Preset preset) {
        boolean mobilePath = profile != null && (profile.android() || profile.mobileGpu() || profile.glTranslationLayer());
        boolean lowEnd = preset == Preset.POTATO || preset == Preset.MOBILE || preset == Preset.LOW_END;
        if (!mobilePath && !lowEnd) {
            return false;
        }
        var options = minecraft.options;
        boolean changed = false;

        if (options.improvedTransparency().get()) {
            options.improvedTransparency().set(false);
            changed = true;
        }
        if (options.vignette().get() && (mobilePath || preset == Preset.POTATO)) {
            options.vignette().set(false);
            changed = true;
        }
        int maxMip = mobilePath ? 2 : 3;
        if (options.mipmapLevels().get() > maxMip) {
            options.mipmapLevels().set(maxMip);
            changed = true;
        }
        int maxBlend = preset == Preset.POTATO ? 0 : (mobilePath ? 1 : 2);
        if (options.biomeBlendRadius().get() > maxBlend) {
            options.biomeBlendRadius().set(maxBlend);
            changed = true;
        }
        if (options.chunkSectionFadeInTime().get() > 0.0 && mobilePath) {
            options.chunkSectionFadeInTime().set(0.0);
            changed = true;
        }
        if (mobilePath && options.simulationDistance().get() > 6) {
            options.simulationDistance().set(6);
            changed = true;
        }
        // Cutout leaves force alpha-tested fragments, which disable the hidden-surface removal that
        // tile-based phone GPUs (Adreno, Mali, PowerVR, Xclipse) rely on; forests become pure overdraw.
        if (options.cutoutLeaves().get() && (mobilePath || preset == Preset.POTATO)) {
            options.cutoutLeaves().set(false);
            changed = true;
        }
        if (mobilePath && options.maxAnisotropyBit().get() > 0) {
            options.maxAnisotropyBit().set(0);
            changed = true;
        }
        int maxWeather = preset == Preset.POTATO ? 3 : (mobilePath ? 5 : 10);
        if (options.weatherRadius().get() > maxWeather) {
            options.weatherRadius().set(maxWeather);
            changed = true;
        }
        if (mobilePath && options.entityDistanceScaling().get() > 0.75) {
            options.entityDistanceScaling().set(0.75);
            changed = true;
        }
        // Menu blur is a multi-pass full-screen blur every frame a menu is open (inventory, chat on some layers).
        if (mobilePath && options.menuBackgroundBlurriness().get() > 0) {
            options.menuBackgroundBlurriness().set(0);
            changed = true;
        }
        if (changed) {
            FerrumClient.LOGGER.info("Ferrum applied mobile/low-end tuning (OIT off, mipmaps <= {}, biome blend <= {})",
                maxMip, maxBlend);
        }
        return changed;
    }

    /** One-time upgrade for installs created before this tuning existed (it normally runs on first launch). */
    public static void applyTuningUpgrade(Minecraft minecraft, HardwareProfile profile, Preset requestedPreset) {
        if (!FerrumClient.CONFIG.autoApplyVanillaOptions || minecraft.options == null) {
            return;
        }
        Preset preset = requestedPreset == Preset.AUTO
            ? (profile == null ? Preset.BALANCED : profile.recommendedPreset())
            : requestedPreset;
        if (applyMobileAndTranslationTuning(minecraft, profile, preset)) {
            minecraft.options.save();
        }
    }

    public static void apply(Minecraft minecraft, HardwareProfile profile, Preset requestedPreset) {
        FerrumConfig config = FerrumClient.CONFIG;
        if (!config.autoApplyVanillaOptions || minecraft.options == null) {
            return;
        }

        Preset preset = requestedPreset == Preset.AUTO
            ? (profile == null ? Preset.BALANCED : profile.recommendedPreset())
            : requestedPreset;

        int recommendedDistance = switch (preset) {
            case POTATO -> 6;
            case MOBILE -> 8;
            case LOW_END -> 10;
            case BALANCED -> 0;
            case QUALITY -> 0;
            case AUTO -> 0;
        };

        boolean changed = false;
        if (recommendedDistance > 0) {
            int current = minecraft.options.renderDistance().get();
            if (current > recommendedDistance) {
                minecraft.options.renderDistance().set(recommendedDistance);
                changed = true;
            }
        }

        ParticleStatus particleStatus = switch (preset) {
            case POTATO, MOBILE -> ParticleStatus.MINIMAL;
            case LOW_END, BALANCED -> ParticleStatus.DECREASED;
            case QUALITY, AUTO -> ParticleStatus.ALL;
        };
        if (minecraft.options.particles().get() != particleStatus) {
            minecraft.options.particles().set(particleStatus);
            changed = true;
        }

        if (preset == Preset.POTATO || preset == Preset.MOBILE || preset == Preset.LOW_END) {
            if (minecraft.options.entityShadows().get()) {
                minecraft.options.entityShadows().set(false);
                changed = true;
            }
        }

        CloudStatus cloudStatus = switch (preset) {
            case POTATO, MOBILE -> CloudStatus.OFF;
            case LOW_END -> CloudStatus.FAST;
            case BALANCED, QUALITY, AUTO -> null;
        };
        if (cloudStatus != null && minecraft.options.cloudStatus().get() != cloudStatus) {
            minecraft.options.cloudStatus().set(cloudStatus);
            changed = true;
        }

        // No frame-rate limit here: presets never cap FPS (v1 did, which hid every gain).

        changed |= applyMobileAndTranslationTuning(minecraft, profile, preset);

        if (changed) {
            minecraft.options.save();
            FerrumClient.LOGGER.info(
                "Applied vanilla performance options for preset {} (render distance {}, particles {}, fps cap {})",
                preset.id(),
                minecraft.options.renderDistance().get(),
                minecraft.options.particles().get(),
                minecraft.options.framerateLimit().get()
            );
        }
    }
}
