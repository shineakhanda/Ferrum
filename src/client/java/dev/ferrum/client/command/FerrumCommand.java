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
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PreferredGraphicsApi;
import net.minecraft.network.chat.Component;

public final class FerrumCommand {
    private FerrumCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var root = ClientCommands.literal("ferrum")
            .executes(context -> status(context.getSource()));

        root.then(ClientCommands.literal("bench")
            .executes(context -> {
                dev.ferrum.client.bench.Benchmark.INSTANCE.start(Minecraft.getInstance(), 30);
                return 1;
            })
            .then(ClientCommands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(5, 600))
                .executes(context -> {
                    dev.ferrum.client.bench.Benchmark.INSTANCE.start(Minecraft.getInstance(),
                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "seconds"));
                    return 1;
                })));

        root.then(ClientCommands.literal("status")
            .executes(context -> status(context.getSource())));

        root.then(ClientCommands.literal("preset")
            .executes(context -> {
                context.getSource().sendFeedback(Component.literal("Current preset: " + FerrumClient.presetDisplayName()));
                return 1;
            })
            .then(ClientCommands.argument("name", StringArgumentType.word())
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

        root.then(ClientCommands.literal("set")
            .then(ClientCommands.argument("key", StringArgumentType.word())
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
                .then(ClientCommands.argument("value", StringArgumentType.word())
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

        root.then(ClientCommands.literal("reload")
            .executes(context -> {
                FerrumClient.reloadConfig();
                context.getSource().sendFeedback(Component.literal("Ferrum config reloaded."));
                return 1;
            }));

        root.then(ClientCommands.literal("vulkan")
            .executes(context -> vulkan(context.getSource(), "status"))
            .then(ClientCommands.literal("status").executes(context -> vulkan(context.getSource(), "status")))
            .then(ClientCommands.literal("on").executes(context -> vulkan(context.getSource(), "on")))
            .then(ClientCommands.literal("off").executes(context -> vulkan(context.getSource(), "off")))
            .then(ClientCommands.literal("auto").executes(context -> vulkan(context.getSource(), "auto")))
            .then(ClientCommands.literal("default").executes(context -> vulkan(context.getSource(), "default"))));

        root.then(ClientCommands.literal("hud")
            .executes(context -> {
                FerrumConfig config = FerrumClient.CONFIG;
                config.debugHud = !config.debugHud;
                FerrumClient.applyRuntimeConfig();
                context.getSource().sendFeedback(Component.literal(
                    "Ferrum debug HUD " + (config.debugHud ? "enabled" : "disabled")));
                return 1;
            }));

        root.then(ClientCommands.literal("config")
            .executes(context -> {
                Minecraft minecraft = context.getSource().getClient();
                minecraft.gui.setScreen(new FerrumConfigScreen());
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

        switch (action) {
            case "status" -> {
                if (profile != null && profile.android()) {
                    source.sendFeedback(Component.literal(
                        "Android/Pojav uses OpenGL/GLES through the launcher. Active backend: "
                            + VulkanSupport.activeBackend()));
                    source.sendFeedback(Component.literal(
                        "Preferred setting in options.txt: " + VulkanSupport.preferred().getSerializedName()));
                    return 1;
                }
                VulkanSupport.Status status = VulkanSupport.status();
                source.sendFeedback(Component.literal(
                    "Active backend: " + status.activeBackend()
                        + " | preferred: " + status.preferredBackend()));
                source.sendFeedback(Component.literal(
                    "Vulkan runtime: " + status.runtimeAvailable() + " - " + status.message()));
                return 1;
            }
            case "on" -> {
                config.vulkanMode = "VULKAN";
                config.vulkanApplied = true;
                config.save();
                VulkanSupport.apply(PreferredGraphicsApi.VULKAN);
                source.sendFeedback(Component.literal(
                    "Preferred graphics API set to Vulkan. Restart Minecraft to apply. "
                        + "Minecraft falls back to OpenGL if Vulkan cannot start."));
                return 1;
            }
            case "off" -> {
                config.vulkanMode = "OPENGL";
                config.vulkanApplied = false;
                config.save();
                VulkanSupport.apply(PreferredGraphicsApi.OPENGL);
                source.sendFeedback(Component.literal(
                    "Preferred graphics API set to OpenGL. Restart Minecraft to apply."));
                return 1;
            }
            case "auto" -> {
                config.vulkanMode = "AUTO";
                config.vulkanApplied = false;
                config.save();
                PreferredGraphicsApi recommended = profile == null
                    ? PreferredGraphicsApi.DEFAULT
                    : VulkanSupport.recommended(profile);
                if (recommended != PreferredGraphicsApi.DEFAULT) {
                    VulkanSupport.apply(recommended);
                    config.vulkanApplied = recommended == PreferredGraphicsApi.VULKAN;
                    config.save();
                    source.sendFeedback(Component.literal(
                        "Ferrum chose " + recommended.getSerializedName()
                            + " for this system. Restart Minecraft to apply."));
                } else {
                    source.sendFeedback(Component.literal(
                        "Ferrum keeps Minecraft's current graphics API for this system."));
                }
                return 1;
            }
            case "default" -> {
                config.vulkanMode = "DEFAULT";
                config.vulkanApplied = false;
                config.save();
                VulkanSupport.apply(PreferredGraphicsApi.DEFAULT);
                source.sendFeedback(Component.literal(
                    "Graphics API reset to Minecraft default. Restart Minecraft to apply."));
                return 1;
            }
            default -> {
                source.sendError(Component.literal("Usage: /ferrum vulkan <status|on|off|auto|default>"));
                return 0;
            }
        }
    }
}
