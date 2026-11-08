package dev.ferrum.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.ferrum.client.access.AnimatedSpriteHolder;
import dev.ferrum.client.access.FaceRangesHolder;
import dev.ferrum.client.render.AnimatedSpriteTracker;
import dev.ferrum.client.render.RenderPassOptimizer;
import dev.ferrum.client.render.terrain.TerrainFaces;
import java.util.Map;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Per-compile context for animated-sprite tracking, render pass optimization and per-face terrain
 * culling. The only {@code Map.put} in {@code compile} is {@code results.renderedLayers.put(layer, mesh)},
 * which is where each finished layer mesh is face-sorted before it is stored and later uploaded.
 */
@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {
    @Inject(method = "compile", at = @At("HEAD"))
    private void ferrum$begin(SectionPos sectionPos, RenderSectionRegion region, VertexSorting sorting,
                              SectionBufferBuilderPack buffers, CallbackInfoReturnable<SectionCompiler.Results> cir) {
        AnimatedSpriteTracker.beginCompile();
        RenderPassOptimizer.begin((SectionCompiler) (Object) this, buffers);
        TerrainFaces.begin();
    }

    @ModifyVariable(method = "compile", at = @At("STORE"), ordinal = 0)
    private Map<ChunkSectionLayer, BufferBuilder> ferrum$captureLayers(Map<ChunkSectionLayer, BufferBuilder> layers) {
        RenderPassOptimizer.layers(layers);
        return layers;
    }

    @WrapOperation(method = "compile", at = @At(value = "INVOKE",
        target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object ferrum$sortFaces(Map<Object, Object> map, Object key, Object value, Operation<Object> original) {
        if (key instanceof ChunkSectionLayer layer && value instanceof MeshData mesh) {
            try {
                TerrainFaces.sort(layer, mesh);
            } catch (Throwable ignored) {
                // A mesh we cannot parse is simply left unsorted and drawn whole.
            }
        }
        return original.call(map, key, value);
    }

    @Inject(method = "compile", at = @At("RETURN"))
    private void ferrum$end(SectionPos sectionPos, RenderSectionRegion region, VertexSorting sorting,
                            SectionBufferBuilderPack buffers, CallbackInfoReturnable<SectionCompiler.Results> cir) {
        RenderPassOptimizer.end();
        SectionCompiler.Results results = cir.getReturnValue();
        ((AnimatedSpriteHolder) (Object) results).ferrum$setAnimatedSprites(AnimatedSpriteTracker.endCompile());
        ((FaceRangesHolder) (Object) results).ferrum$setFaceRanges(TerrainFaces.end());
    }
}
