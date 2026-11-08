package dev.ferrum.client.render;

import dev.ferrum.client.access.AnimatedSpriteHolder;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Sodium-style "animate only visible textures".
 *
 * <p>While a chunk section is meshed, every animated sprite its block quads use is recorded on the
 * compiled mesh. Each tick, before the block atlas uploads animation frames, the sprites of all
 * currently visible sections are collected; animated sprites nobody can see are not redrawn.
 * Fluids are always animated (they are meshed by a different path and are almost always on screen).
 */
public final class AnimatedSpriteTracker {
    private static final ThreadLocal<Set<TextureAtlasSprite>> COMPILING = new ThreadLocal<>();
    private static final Set<TextureAtlasSprite> EMPTY = Set.of();

    private static Set<TextureAtlasSprite> active = new ReferenceOpenHashSet<>();
    public static volatile int lastActiveCount;
    public static volatile int lastSkipped;

    private AnimatedSpriteTracker() {
    }

    public static void beginCompile() {
        COMPILING.set(new ReferenceOpenHashSet<>(4));
    }

    public static Set<TextureAtlasSprite> endCompile() {
        Set<TextureAtlasSprite> set = COMPILING.get();
        COMPILING.remove();
        return set == null || set.isEmpty() ? EMPTY : set;
    }

    public static void record(TextureAtlasSprite sprite) {
        Set<TextureAtlasSprite> set = COMPILING.get();
        if (set != null) {
            set.add(sprite);
        }
    }

    /**
     * Rebuilds the visible-sprite set. Returns false when culling must not happen this tick
     * (no world, a screen is open and may show block items, or nothing is visible yet).
     */
    public static boolean refresh(Minecraft mc) {
        if (mc.level == null || mc.screen != null || mc.levelRenderer == null) {
            return false;
        }
        var sections = ((dev.ferrum.client.mixin.LevelRendererAccessor) mc.levelRenderer).ferrum$visibleSections();
        if (sections.isEmpty()) {
            return false;
        }
        Set<TextureAtlasSprite> next = new ReferenceOpenHashSet<>(Math.max(16, active.size()));
        for (SectionRenderDispatcher.RenderSection section : sections) {
            if (section.getSectionMesh() instanceof CompiledSectionMesh mesh) {
                Set<TextureAtlasSprite> sprites = ((AnimatedSpriteHolder) mesh).ferrum$animatedSprites();
                if (!sprites.isEmpty()) {
                    next.addAll(sprites);
                }
            }
        }
        active = next;
        lastActiveCount = next.size();
        return true;
    }

    public static boolean isVisible(TextureAtlasSprite sprite) {
        if (active.contains(sprite)) {
            return true;
        }
        String path = sprite.contents().name().getPath();
        return path.contains("water") || path.contains("lava");
    }

    /** Sodium Extra-style per-type animation toggles. A disabled type keeps its current frame. */
    public static boolean typeEnabled(TextureAtlasSprite sprite) {
        String path = sprite.contents().name().getPath();
        if (path.contains("water")) {
            return dev.ferrum.client.state.FerrumState.animWater;
        }
        if (path.contains("lava")) {
            return dev.ferrum.client.state.FerrumState.animLava;
        }
        if (path.contains("fire")) {
            return dev.ferrum.client.state.FerrumState.animFire;
        }
        if (path.contains("portal")) {
            return dev.ferrum.client.state.FerrumState.animPortal;
        }
        return dev.ferrum.client.state.FerrumState.animBlocks;
    }
}
