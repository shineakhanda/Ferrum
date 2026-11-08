package dev.ferrum.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.QuadInstance;
import dev.ferrum.client.render.AnimatedSpriteTracker;
import dev.ferrum.client.render.RenderPassOptimizer;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
    @Inject(method = "putQuadWithTint", at = @At("HEAD"))
    private void ferrum$recordSprite(CallbackInfo ci, @Local(argsOnly = true) BakedQuad quad) {
        TextureAtlasSprite sprite = quad.materialInfo().sprite();
        if (sprite.contents().isAnimated()) {
            AnimatedSpriteTracker.record(sprite);
        }
    }

    /**
     * Render pass optimization: a CUTOUT quad whose sprite has no transparent pixel goes to the SOLID
     * layer of the section being compiled. Outside chunk compilation (falling blocks, block items,
     * block entities) there is no context and the quad goes to its original output.
     */
    @WrapOperation(method = "putQuadWithTint", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/block/BlockQuadOutput;put(FFFLnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"))
    private void ferrum$promoteOpaqueCutout(BlockQuadOutput output, float x, float y, float z, BakedQuad quad,
                                            QuadInstance instance, Operation<Void> original) {
        BakedQuad.MaterialInfo material = quad.materialInfo();
        if (material.layer() == ChunkSectionLayer.CUTOUT && RenderPassOptimizer.opaque(material.sprite())) {
            BufferBuilder solid = RenderPassOptimizer.solidBuilder();
            if (solid != null) {
                solid.putBlockBakedQuad(x, y, z, quad, instance);
                return;
            }
        }
        original.call(output, x, y, z, quad, instance);
    }
}
