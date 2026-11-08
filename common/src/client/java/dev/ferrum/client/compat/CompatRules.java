package dev.ferrum.client.compat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Which Ferrum features step aside for which mods. Used by {@link FerrumMixinPlugin} before any
 * Minecraft class loads, and by {@link ModCompat} to report the result in F3 and {@code /ferrum status}.
 *
 * <p>The rule of thumb: Ferrum never runs two implementations of the same optimization, and never
 * patches code that another mod replaces wholesale.
 */
public final class CompatRules {
    /** Feature name shown to players, the mixins that implement it, and the mods that replace it. */
    public record Rule(String feature, List<String> mixins, List<String> replacedBy) {
    }

    private static final List<String> RENDERER_REPLACERS = List.of("sodium", "vulkanmod");

    public static final List<Rule> RULES = List.of(
        // Sodium and VulkanMod build and draw chunks with their own renderer; vanilla's section
        // compiler, chunk draw list and animated-texture path that these mixins patch are unused.
        new Rule("Terrain path (face culling, render pass optimization, animated textures)",
            List.of("SectionCompilerMixin", "SectionCompilerResultsMixin", "CompiledSectionMeshMixin",
                "ModelBlockRendererMixin", "TerrainDrawMixin", "TextureAtlasMixin", "SpriteContentsMixin",
                "SectionCompilerAccessor", "LevelRendererMixin", "LevelRendererAccessor"),
            RENDERER_REPLACERS),
        // Both ship their own video settings screen; keep theirs and add a "Ferrum..." button instead.
        new Rule("Video Settings replacement", List.of("VideoSettingsReplaceMixin"), RENDERER_REPLACERS),
        // VulkanMod replaces Minecraft's GPU backend; render scale and FSR use that backend directly.
        new Rule("Render scale / FSR", List.of("GameRendererMixin"), List.of("vulkanmod")),
        // BadOptimizations already skips unchanged lightmap updates.
        new Rule("Lightmap cache", List.of("LightmapMixin", "LightTextureMixin"), List.of("badoptimizations", "vulkanmod"))
    );

    private CompatRules() {
    }

    public static boolean isLoaded(String modId) {
        try {
            return FabricLoader.getInstance().isModLoaded(modId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** The first installed mod that replaces the given mixin, or null if Ferrum should apply it. */
    public static String replacementFor(String mixinSimpleName) {
        for (Rule rule : RULES) {
            if (rule.mixins().contains(mixinSimpleName)) {
                for (String mod : rule.replacedBy()) {
                    if (isLoaded(mod)) {
                        return mod;
                    }
                }
            }
        }
        return null;
    }

    /** Feature -> mod that replaced it, for every rule that is active in this game. */
    public static Map<String, String> disabledFeatures() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Rule rule : RULES) {
            for (String mod : rule.replacedBy()) {
                if (isLoaded(mod)) {
                    result.put(rule.feature(), mod);
                    break;
                }
            }
        }
        return result;
    }

    /** Performance mods Ferrum knows about and is tested to run alongside. */
    public static final Map<String, String> KNOWN_MODS = Map.ofEntries(
        Map.entry("sodium", "Sodium"),
        Map.entry("iris", "Iris"),
        Map.entry("vulkanmod", "VulkanMod"),
        Map.entry("lithium", "Lithium"),
        Map.entry("ferritecore", "FerriteCore"),
        Map.entry("badoptimizations", "BadOptimizations"),
        Map.entry("immediatelyfast", "ImmediatelyFast"),
        Map.entry("entityculling", "EntityCulling"),
        Map.entry("moreculling", "More Culling"),
        Map.entry("modernfix", "ModernFix"),
        Map.entry("c2me", "C2ME"),
        Map.entry("krypton", "Krypton"),
        Map.entry("dynamic_fps", "Dynamic FPS"),
        Map.entry("scalablelux", "ScalableLux"),
        Map.entry("noisium", "Noisium"),
        Map.entry("distanthorizons", "Distant Horizons"),
        Map.entry("lambdynlights", "LambDynamicLights"),
        Map.entry("sodiumdynamiclights", "Sodium Dynamic Lights")
    );

    public static List<String> installedKnownMods() {
        List<String> names = new ArrayList<>();
        KNOWN_MODS.forEach((id, name) -> {
            if (isLoaded(id)) {
                names.add(name);
            }
        });
        names.sort(String::compareToIgnoreCase);
        return names;
    }
}
