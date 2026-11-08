package dev.ferrum.client;

import dev.ferrum.client.command.FerrumCommand;
import dev.ferrum.client.compat.ModCompat;
import dev.ferrum.client.config.FerrumConfig;
import dev.ferrum.client.config.Preset;
import dev.ferrum.client.dynamic.DynamicLights;
import dev.ferrum.client.hardware.HardwareDetector;
import dev.ferrum.client.hardware.HardwareProfile;
import dev.ferrum.client.hardware.VulkanSupport;
import dev.ferrum.client.state.FerrumState;
import dev.ferrum.client.tuning.AdaptiveTuner;
import dev.ferrum.client.tuning.VanillaOptionsApplier;
import dev.ferrum.client.ui.FerrumConfigScreen;
import dev.ferrum.client.ui.FerrumHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FerrumClient implements ClientModInitializer {
    public static final String MOD_ID = "ferrum";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger("Ferrum");

    public static volatile FerrumConfig CONFIG;
    public static volatile HardwareProfile PROFILE;

    private static final KeyMapping OPEN_CONFIG = new KeyMapping(
        "key.ferrum.config",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_F6,
        KeyMapping.Category.MISC
    );

    private int counterTicks;
    private Object occlusionLevel;

    @Override
    public void onInitializeClient() {
        CONFIG = FerrumConfig.load();
        FerrumState.apply(CONFIG);
        AdaptiveTuner.onConfigLoaded(CONFIG);

        KeyBindingHelper.registerKeyBinding(OPEN_CONFIG);
        HudElementRegistry.addLast(FerrumHud.ID, FerrumHud.INSTANCE);

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> this.onClientStarted());
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> FerrumCommand.register(dispatcher));
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof VideoSettingsScreen) {
                Screens.getButtons(screen).add(Button.builder(Component.literal("Ferrum..."),
                        b -> client.setScreen(new FerrumConfigScreen(screen)))
                    .bounds(w - 84, 6, 78, 20).build());
            }
        });

        LOGGER.info("Ferrum {} initialized (config: {})", VERSION, CONFIG.configPath);
    }

    private void onClientStarted() {
        Minecraft minecraft = Minecraft.getInstance();
        PROFILE = HardwareDetector.detect();

        LOGGER.info("Ferrum hardware: {}", PROFILE.shortSummary());
        LOGGER.info("Ferrum GPU: {} / {} / {} / type {} / max texture {}",
            PROFILE.gpuName(), PROFILE.gpuVendor(), PROFILE.backend(), PROFILE.gpuType(), PROFILE.maxTextureSize());
        LOGGER.info("Ferrum flags: android={} pojav={} glTranslation={} mobileGpu={} software={}",
            PROFILE.android(), PROFILE.pojav(), PROFILE.glTranslationLayer(), PROFILE.mobileGpu(), PROFILE.softwareGpu());
        LOGGER.info("Ferrum compatibility: {}", ModCompat.summary());

        boolean firstRun = CONFIG.firstRun;
        if (firstRun && CONFIG.preset() == Preset.AUTO) {
            CONFIG.applyPresetValues(PROFILE.recommendedPreset());
            CONFIG.sanitize();
        }

        if (CONFIG.liftLegacyFrameCap) {
            CONFIG.liftLegacyFrameCap = false;
            int limit = minecraft.options.framerateLimit().get();
            if (limit == 30 || limit == 60) {
                minecraft.options.framerateLimit().set(260);
                minecraft.options.save();
                LOGGER.info("Ferrum removed the {} FPS limit set by an older preset", limit);
            }
            VanillaOptionsApplier.applyTuningUpgrade(minecraft, PROFILE, CONFIG.preset());
            CONFIG.save();
        }

        FerrumState.apply(CONFIG);
        AdaptiveTuner.onConfigLoaded(CONFIG);

        if (firstRun) {
            VanillaOptionsApplier.apply(minecraft, PROFILE, CONFIG.preset());
            CONFIG.firstRun = false;
            CONFIG.save();
        }


        LOGGER.info("Ferrum graphics: {} ({})", VulkanSupport.activeBackend(), VulkanSupport.status().message());

        if (ModCompat.OTHER_DYNAMIC_LIGHTS) {
            LOGGER.info("Ferrum dynamic lights pause while another dynamic-light mod is present");
        }
    }

    private void onClientTick(Minecraft minecraft) {
        while (OPEN_CONFIG.consumeClick()) {
            if (minecraft.screen == null) {
                minecraft.setScreen(new FerrumConfigScreen());
            }
        }

        FerrumState.clientTick++;
        if (minecraft.level != this.occlusionLevel) {
            this.occlusionLevel = minecraft.level;
            dev.ferrum.client.culling.OcclusionCuller.setOpacity(
                minecraft.level == null ? null : new dev.ferrum.client.culling.LevelOpacity(minecraft.level));
        }
        DynamicLights.tick(minecraft);
        AdaptiveTuner.onClientTick(minecraft);

        if (++this.counterTicks >= 20) {
            this.counterTicks = 0;
            FerrumState.snapshotCounters();
        }
    }

    public static void applyRuntimeConfig() {
        FerrumState.apply(CONFIG);
        AdaptiveTuner.onConfigLoaded(CONFIG);
        DynamicLights.onConfigChanged();
        CONFIG.save();
    }

    public static void reloadConfig() {
        CONFIG = FerrumConfig.load();
        FerrumState.apply(CONFIG);
        AdaptiveTuner.onConfigLoaded(CONFIG);
        DynamicLights.onConfigChanged();
        LOGGER.info("Ferrum config reloaded from {}", CONFIG.configPath);
    }

    public static void applyPreset(Preset preset) {
        Preset resolved;
        if (preset == Preset.AUTO) {
            CONFIG.preset = Preset.AUTO.id();
            resolved = PROFILE == null ? Preset.BALANCED : PROFILE.recommendedPreset();
            CONFIG.applyPresetValues(resolved);
        } else {
            CONFIG.setPreset(preset);
            resolved = preset;
        }
        CONFIG.sanitize();
        applyRuntimeConfig();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            VanillaOptionsApplier.apply(minecraft, PROFILE, resolved);
        }
        LOGGER.info("Ferrum preset applied: {} (config preset {})", resolved.id(), CONFIG.preset);
    }

    public static String presetDisplayName() {
        Preset preset = CONFIG == null ? Preset.AUTO : CONFIG.preset();
        if (preset == Preset.AUTO && PROFILE != null) {
            return "AUTO (" + PROFILE.recommendedPreset().displayName() + ")";
        }
        return preset.displayName();
    }
}
