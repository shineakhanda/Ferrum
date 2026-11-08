package dev.ferrum.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.ferrum.client.access.SpriteOpacity;
import dev.ferrum.client.mixin.SectionCompilerAccessor;
import dev.ferrum.client.state.FerrumState;
import java.util.Map;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Render pass optimization (the technique Sodium and Embeddium call by the same name), written for
 * vanilla's section compiler.
 *
 * <p>Vanilla picks the chunk layer per block, not per quad: a grass block is CUTOUT because its
 * side overlay has transparent pixels, so its top, bottom and dirt sides go through the cutout
 * pipeline too. The cutout fragment shader can {@code discard}, and a shader that can discard turns
 * off early depth testing and hidden-surface removal on GPUs. On tile-based phone GPUs (Adreno,
 * Mali, PowerVR, Xclipse) that makes every covered pixel real shading work.
 *
 * <p>While a section compiles, quads headed for the CUTOUT layer whose sprite is fully opaque
 * (alpha 255 everywhere, see SpriteContentsMixin) are written into the SOLID layer instead. Such a
 * quad can never discard, so the image is identical. Translucent geometry is never touched, so
 * translucency sorting is unaffected.
 *
 * <p>The context is thread-local because sections compile on several chunk-builder threads.
 */
public final class RenderPassOptimizer {
    private static final class Context {
        SectionCompiler compiler;
        SectionBufferBuilderPack buffers;
        Map<ChunkSectionLayer, BufferBuilder> layers;
    }

    private static final ThreadLocal<Context> CONTEXT = ThreadLocal.withInitial(Context::new);

    private RenderPassOptimizer() {
    }

    public static void begin(SectionCompiler compiler, SectionBufferBuilderPack buffers) {
        Context c = CONTEXT.get();
        c.compiler = compiler;
        c.buffers = buffers;
        c.layers = null;
    }

    public static void layers(Map<ChunkSectionLayer, BufferBuilder> layers) {
        CONTEXT.get().layers = layers;
    }

    public static void end() {
        Context c = CONTEXT.get();
        c.compiler = null;
        c.buffers = null;
        c.layers = null;
    }

    public static boolean opaque(TextureAtlasSprite sprite) {
        return sprite != null && ((SpriteOpacity) sprite.contents()).ferrum$isFullyOpaque();
    }

    /** The CUTOUT builder of the section being compiled on this thread, or null. */
    public static BufferBuilder cutoutBuilder() {
        Map<ChunkSectionLayer, BufferBuilder> layers = CONTEXT.get().layers;
        return layers == null ? null : layers.get(ChunkSectionLayer.CUTOUT);
    }

    /** The SOLID builder of the section being compiled (started on demand), or null outside a compile. */
    public static BufferBuilder solidBuilder() {
        if (!FerrumState.renderPassOptimization) {
            return null;
        }
        Context c = CONTEXT.get();
        if (c.compiler == null || c.layers == null) {
            return null;
        }
        FerrumState.quadsPromoted++;
        return ((SectionCompilerAccessor) c.compiler).ferrum$getOrBeginLayer(c.layers, c.buffers, ChunkSectionLayer.SOLID);
    }
}
