package dev.ferrum.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ferrum.client.render.AnimatedSpriteTracker;
import dev.ferrum.client.render.RenderPassOptimizer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
    @Inject(method = "putQuadData", at = @At("HEAD"))
    private void ferrum$recordSprite(CallbackInfo ci, @Local(argsOnly = true) BakedQuad quad) {
        TextureAtlasSprite sprite = quad.sprite();
        if (sprite.contents().isAnimated()) {
            AnimatedSpriteTracker.record(sprite);
        }
    }

    /**
     * Render pass optimization. 1.21.11 passes the block's layer buffer down to putQuadData, so a
     * quad is in the cutout layer exactly when its consumer is the section's CUTOUT builder. If its
     * sprite has no transparent pixel it is written to the SOLID builder instead. Outside chunk
     * compilation there is no context and nothing changes.
     */
    @WrapOperation(method = "putQuadData", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[II)V"))
    private void ferrum$promoteOpaqueCutout(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad,
                                            float[] brightness, float r, float g, float b, float a,
                                            int[] lightmap, int overlay, Operation<Void> original) {
        if (consumer == RenderPassOptimizer.cutoutBuilder() && RenderPassOptimizer.opaque(quad.sprite())) {
            BufferBuilder solid = RenderPassOptimizer.solidBuilder();
            if (solid != null) {
                original.call(solid, pose, quad, brightness, r, g, b, a, lightmap, overlay);
                return;
            }
        }
        original.call(consumer, pose, quad, brightness, r, g, b, a, lightmap, overlay);
    }
}
