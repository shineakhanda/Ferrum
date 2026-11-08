package dev.ferrum.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.ferrum.client.FerrumClient;
import dev.ferrum.client.compat.ModCompat;
import dev.ferrum.client.config.FerrumConfig;
import dev.ferrum.client.config.Preset;
import dev.ferrum.client.hardware.HardwareProfile;
import dev.ferrum.client.hardware.VulkanSupport;
import dev.ferrum.client.state.FerrumState;
import dev.ferrum.client.ui.FerrumConfigScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class FerrumCommand {
    private FerrumCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var root = ClientCommandManager.literal("ferrum")
            .executes(context -> status(context.getSource()));

        root.then(ClientCommandManager.literal("status")
            .executes(context -> status(context.getSource())));

        root.then(ClientCommandManager.literal("preset")
            .executes(context -> {
                context.getSource().sendFeedback(Component.literal("Current preset: " + FerrumClient.presetDisplayName()));
                return 1;
            })
            .then(ClientCommandManager.argument("name", StringArgumentType.word())
                .suggests((context, builder) -> {
                    for (Preset preset : Preset.values()) {
                        builder.suggest(preset.id());
                    }
                    return builder.buildFuture();
                })
                .executes(context -> {
                    String name = StringArgumentType.getString(context, "name");
                    Preset preset = Preset.byId(name);
                    if (preset == Preset.AUTO && !name.equalsIgnoreCase("auto")) {
                        context.getSource().sendError(Component.literal("Unknown preset: " + name));
                        return 0;
                    }
                    FerrumClient.applyPreset(preset);
                    context.getSource().sendFeedback(Component.literal(
                        "Applied preset " + FerrumClient.presetDisplayName()
                            + ". Vanilla video options were adjusted conservatively."));
                    return 1;
                })));

        root.then(ClientCommandManager.literal("set")
            .then(ClientCommandManager.argument("key", StringArgumentType.word())
                .suggests((context, builder) -> {
                    for (String key : new String[]{
                        "entityCulling", "entityDistance", "cullPlayers", "blockEntityCulling",
                        "blockEntityDistance", "particleLimiting", "maxParticles", "particleDistance",
                        "keepLimitedParticles", "dynamicLights", "dynamicLightRadius", "dynamicLightEntities",
                        "dynamicLightItems", "dynamicLightMaxSources", "adaptiveQuality", "targetFps",
                        "fpsCap", "textWidthCache", "irisSafeMode", "debugHud", "autoApplyVanillaOptions",
                        "fog", "clouds", "weather", "sky", "stars", "sun", "moon", "sunriseSunset",
                        "entityTickCulling", "ambientParticleDensity", "animateOnlyVisible", "lightmapCache", "renderPassOptimization", "faceCulling",
                        "replaceVideoSettings", "renderScale", "renderScaleSharp", "fsr", "fsrSharpness", "occlusionCulling",
                        "occlusionBlockEntities", "animWater", "animLava", "animFire", "animPortal", "animBlocks",
                        "particleBlockBreak", "particleRain", "particleExplosions", "particleCampfireSmoke",
                        "particlePotion", "particleLeaves", "particleFlames", "renderItemFrames",
                        "renderArmorStands", "renderPaintings", "renderNameTags", "renderBeaconBeams", "renderEnchantBook"
                    }) {
                        builder.suggest(key);
                    }
                    return builder.buildFuture();
                })
                .then(ClientCommandManager.argument("value", StringArgumentType.word())
                    .executes(context -> {
                        String key = StringArgumentType.getString(context, "key");
                        String value = StringArgumentType.getString(context, "value");
                        FerrumConfig config = FerrumClient.CONFIG;
                        if (!config.setValue(key, value)) {
                            context.getSource().sendError(Component.literal("Could not set " + key + " to " + value));
                            return 0;
                        }
                        FerrumClient.applyRuntimeConfig();
                        if (key.equals("fpsCap")) {
                            Minecraft.getInstance().options.framerateLimit().set(config.fpsCap > 0 ? config.fpsCap : 260);
                            Minecraft.getInstance().options.save();
                        }
                        context.getSource().sendFeedback(Component.literal("Ferrum: " + key + " = " + value));
                        return 1;
                    }))));

        root.then(ClientCommandManager.literal("reload")
            .executes(context -> {
                FerrumClient.reloadConfig();
                context.getSource().sendFeedback(Component.literal("Ferrum config reloaded."));
                return 1;
            }));

        root.then(ClientCommandManager.literal("vulkan")
            .executes(context -> vulkan(context.getSource(), "status"))
            .then(ClientCommandManager.literal("status").executes(context -> vulkan(context.getSource(), "status")))
            .then(ClientCommandManager.literal("on").executes(context -> vulkan(context.getSource(), "on")))
            .then(ClientCommandManager.literal("off").executes(context -> vulkan(context.getSource(), "off")))
            .then(ClientCommandManager.literal("auto").executes(context -> vulkan(context.getSource(), "auto")))
            .then(ClientCommandManager.literal("default").executes(context -> vulkan(context.getSource(), "default"))));

        root.then(ClientCommandManager.literal("hud")
            .executes(context -> {
                FerrumConfig config = FerrumClient.CONFIG;
                config.debugHud = !config.debugHud;
                FerrumClient.applyRuntimeConfig();
                context.getSource().sendFeedback(Component.literal(
                    "Ferrum debug HUD " + (config.debugHud ? "enabled" : "disabled")));
                return 1;
            }));

        root.then(ClientCommandManager.literal("config")
            .executes(context -> {
                Minecraft minecraft = context.getSource().getClient();
                minecraft.setScreen(new FerrumConfigScreen());
                return 1;
            }));

        dispatcher.register(root);
    }

    private static int status(FabricClientCommandSource source) {
        HardwareProfile profile = FerrumClient.PROFILE;
        FerrumConfig config = FerrumClient.CONFIG;

        source.sendFeedback(Component.literal("Ferrum 1.0.0 - low-end first performance"));
        if (profile == null) {
            source.sendFeedback(Component.literal("Hardware profile: not available yet"));
        } else {
            source.sendFeedback(Component.literal("System: " + profile.shortSummary()));
            source.sendFeedback(Component.literal(
                "GPU: " + profile.gpuName() + " / " + profile.gpuVendor()
                    + " / type " + profile.gpuType() + " / backend " + profile.backend()));
            source.sendFeedback(Component.literal(
                "Flags: android=" + profile.android() + " pojav=" + profile.pojav()
                    + " glTranslation=" + profile.glTranslationLayer()
                    + " mobileGpu=" + profile.mobileGpu()
                    + " software=" + profile.softwareGpu()));
        }

        source.sendFeedback(Component.literal(
            "Compat: " + ModCompat.summary() + (ModCompat.isShaderPackInUse() ? " | shader pack active" : "")));

        String vulkanLine;
        if (profile != null && profile.android()) {
            vulkanLine = "Vulkan: not used on Android/Pojav; OpenGL/GLES path active ("
                + VulkanSupport.activeBackend() + ")";
        } else {
            VulkanSupport.Status status = VulkanSupport.status();
            vulkanLine = "Vulkan: runtime=" + status.runtimeAvailable()
                + " (" + status.message() + "), active=" + status.activeBackend()
                + ", preferred=" + status.preferredBackend();
        }
        source.sendFeedback(Component.literal(vulkanLine));

        source.sendFeedback(Component.literal(
            "Preset: " + FerrumClient.presetDisplayName()
                + " | culling E=" + config.entityCulling + " (" + Math.round(FerrumState.entityCullDistance) + ")"
                + " BE=" + config.blockEntityCulling + " (" + Math.round(FerrumState.blockEntityCullDistance) + ")"
                + " | particles=" + config.particleLimiting + " cap=" + FerrumState.maxParticles
                + " dist=" + Math.round(FerrumState.particleDistance)
                + " | lights=" + config.dynamicLights + " r=" + config.dynamicLightRadius));
        source.sendFeedback(Component.literal(
            "Live: entities culled/s=" + FerrumState.entityCulledPerSecond
                + ", block entities/s=" + FerrumState.blockEntityCulledPerSecond
                + ", particles=" + FerrumState.particleCount
                + ", light cells=" + FerrumState.dynamicLightCells
                + ", tune=" + FerrumState.lastTuningAction));
        source.sendFeedback(Component.literal("Config: " + config.configPath));
        return 1;
    }

    private static int vulkan(FabricClientCommandSource source, String action) {
        FerrumConfig config = FerrumClient.CONFIG;
        HardwareProfile profile = FerrumClient.PROFILE;

        VulkanSupport.Status status = VulkanSupport.status();
        source.sendFeedback(Component.literal("Active backend: " + status.activeBackend()));
        source.sendFeedback(Component.literal(status.message()));
        if (!action.equals("status")) {
            source.sendError(Component.literal("Graphics API switching needs Minecraft 26.x; this is the 1.21.11 build."));
            return 0;
        }
        return 1;
    }
}
