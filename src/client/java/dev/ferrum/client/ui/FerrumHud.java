package dev.ferrum.client.ui;

import dev.ferrum.client.FerrumClient;
import dev.ferrum.client.config.FerrumConfig;
import dev.ferrum.client.config.Preset;
import dev.ferrum.client.hardware.HardwareProfile;
import dev.ferrum.client.hardware.VulkanSupport;
import dev.ferrum.client.state.FerrumState;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class FerrumHud implements HudElement {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("ferrum", "debug_hud");
    public static final FerrumHud INSTANCE = new FerrumHud();

    private FerrumHud() {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        FerrumConfig config = FerrumClient.CONFIG;
        if (config == null || !config.debugHud || minecraft.level == null || minecraft.player == null
            || minecraft.gui.hud.isHidden()) {
            return;
        }

        HardwareProfile profile = FerrumClient.PROFILE;
        Preset preset = config.preset();
        String presetName = preset == Preset.AUTO && profile != null
            ? "AUTO/" + profile.recommendedPreset().displayName()
            : preset.displayName();
        String backend = profile == null ? "unknown" : profile.backend();
        String gpu = profile == null ? "Unknown GPU" : profile.gpuName();

        int x = 4;
        int y = 4;
        int white = 0xFFFFFFFF;
        int green = 0xFF55FF55;
        int yellow = 0xFFFFFF55;
        int gray = 0xFFAAAAAA;

        graphics.text(minecraft.font, "Ferrum 1.0.0  |  " + presetName + "  |  " + backend, x, y, green, true);
        y += 10;
        long frameMicros = minecraft.getFrameTimeNs() / 1000L;
        graphics.text(minecraft.font,
            "FPS " + minecraft.getFps() + "  |  frame " + String.format(java.util.Locale.ROOT, "%.1f", frameMicros / 1000.0) + " ms",
            x, y, white, true);
        y += 10;
        graphics.text(minecraft.font,
            "Culled/s: entities " + FerrumState.entityCulledPerSecond + ", block entities " + FerrumState.blockEntityCulledPerSecond,
            x, y, yellow, true);
        y += 10;
        graphics.text(minecraft.font,
            "Particles " + FerrumState.particleCount + "/" + FerrumState.maxParticles
                + "  |  lights " + FerrumState.dynamicLightSources + "/" + FerrumState.dynamicLightCells,
            x, y, white, true);
        y += 10;
        graphics.text(minecraft.font,
            "Dist E:" + Math.round(FerrumState.entityCullDistance)
                + " B:" + Math.round(FerrumState.blockEntityCullDistance)
                + " P:" + Math.round(FerrumState.particleDistance)
                + "  |  tune: " + FerrumState.lastTuningAction,
            x, y, gray, true);
        y += 10;
        graphics.text(minecraft.font, "GPU: " + shorten(gpu, 48), x, y, gray, true);

        if (profile != null && profile.glTranslationLayer()) {
            y += 10;
            graphics.text(minecraft.font, "Compatibility: GL translation / mobile path", x, y, yellow, true);
        }

        y += 10;
        if (profile != null && profile.android()) {
            graphics.text(minecraft.font,
                "Vulkan: n/a on Android  |  " + VulkanSupport.activeBackend(),
                x, y, gray, true);
        } else {
            VulkanSupport.Status vulkan = VulkanSupport.status();
            graphics.text(minecraft.font,
                "Vulkan: " + (vulkan.runtimeAvailable() ? "ready" : "unavailable")
                    + "  |  preferred: " + vulkan.preferredBackend(),
                x, y, gray, true);
        }
    }

    private static String shorten(String value, int max) {
        if (value == null) {
            return "unknown";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "...";
    }
}
