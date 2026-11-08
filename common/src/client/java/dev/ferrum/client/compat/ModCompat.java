package dev.ferrum.client.compat;

import net.fabricmc.loader.api.FabricLoader;

public final class ModCompat {
    public static final boolean SODIUM = isLoaded("sodium");
    public static final boolean IRIS = isLoaded("iris");
    public static final boolean VULKAN_MOD = isLoaded("vulkanmod");
    /** EntityCulling (tr7zw) does ray-traced entity culling and tick culling; Ferrum's versions step aside. */
    public static final boolean ENTITY_CULLING = isLoaded("entityculling");
    public static final boolean BAD_OPTIMIZATIONS = isLoaded("badoptimizations");
    public static final boolean OTHER_DYNAMIC_LIGHTS = isLoaded("lambdynlights")
        || isLoaded("sodiumdynamiclights")
        || isLoaded("dynamiclights")
        || isLoaded("dynamic_light");

    private static volatile boolean shaderPackInUse;
    private static volatile long lastShaderCheckMs;

    private ModCompat() {
    }

    /** One line for F3 and /ferrum status: known performance mods installed and what Ferrum handed over. */
    public static String summary() {
        java.util.List<String> installed = CompatRules.installedKnownMods();
        java.util.Map<String, String> disabled = CompatRules.disabledFeatures();
        StringBuilder sb = new StringBuilder(installed.isEmpty() ? "no other performance mods" : String.join(", ", installed));
        if (ENTITY_CULLING) {
            disabled = new java.util.LinkedHashMap<>(disabled);
            disabled.put("Occlusion + tick culling", "entityculling");
        }
        if (!disabled.isEmpty()) {
            sb.append(" | handed over: ").append(String.join(", ", disabled.keySet()));
        }
        return sb.toString();
    }

    public static boolean isLoaded(String modId) {
        try {
            return FabricLoader.getInstance().isModLoaded(modId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isShaderPackInUse() {
        long now = System.currentTimeMillis();
        if (now - lastShaderCheckMs < 1000L) {
            return shaderPackInUse;
        }
        lastShaderCheckMs = now;

        if (!IRIS) {
            shaderPackInUse = false;
            return false;
        }

        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object result = apiClass.getMethod("isShaderPackInUse").invoke(api);
            shaderPackInUse = result instanceof Boolean value && value;
        } catch (Throwable ignored) {
            shaderPackInUse = false;
        }
        return shaderPackInUse;
    }
}
