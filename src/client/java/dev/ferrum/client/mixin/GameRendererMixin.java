package dev.ferrum.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.GpuFormat;
import dev.ferrum.client.state.FerrumState;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ferrum render scale.
 *
 * <p>Weak laptop GPUs and phone GPUs are limited by how many pixels they shade, not by draw calls.
 * When the render scale is below 100%, the 3D world (terrain, entities, sky, particles, hand) is drawn
 * into a smaller off-screen target, then upscaled with bilinear filtering into the real framebuffer.
 * Post effects, entity outlines, screenshots and the whole GUI keep running at full resolution, so
 * text and the hotbar stay sharp.
 *
 * <p>How it works: around {@code GameRenderer.renderLevel()} the {@code mainRenderTarget} field is
 * swapped for the scaled target, so every vanilla pass that asks for the main target renders at the
 * lower resolution. The level renderer's own targets (entity outline, translucency) and the 3D-HUD
 * depth target are kept at the same scaled size, since they share passes with the main target.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    @Mutable
    private RenderTarget mainRenderTarget;

    @Shadow
    @Final
    private RenderTarget hud3DTarget;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private TextureTarget ferrum$scaledTarget;

    @Unique
    private RenderTarget ferrum$realTarget;

    @Unique
    private dev.ferrum.client.render.FsrUpscaler ferrum$fsr;

    /** Created on first use: field initializers in mixins are not reliably merged into GameRenderer constructors. */
    @Unique
    private dev.ferrum.client.render.FsrUpscaler ferrum$fsr() {
        if (this.ferrum$fsr == null) {
            this.ferrum$fsr = new dev.ferrum.client.render.FsrUpscaler();
        }
        return this.ferrum$fsr;
    }

    /** Size the level renderer's internal targets currently have; 0 = window size. */
    @Unique
    private int ferrum$levelTargetsWidth;

    @Unique
    private int ferrum$levelTargetsHeight;

    @Unique
    private static final Vector4f FERRUM$CLEAR = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);

    @Inject(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel()V"))
    private void ferrum$beginScaledWorld(CallbackInfo ci) {
        RenderTarget real = this.mainRenderTarget;
        int fullW = real.width;
        int fullH = real.height;
        // Iris shader packs run their own render targets and passes; never rescale under them.
        int percent = dev.ferrum.client.compat.ModCompat.isShaderPackInUse() ? 100 : FerrumState.renderScale;

        if (percent >= 100 || fullW <= 0 || fullH <= 0) {
            this.ferrum$restoreLevelTargets(fullW, fullH);
            return;
        }

        float scale = percent / 100.0F;
        int w = Math.max(64, Math.round(fullW * scale));
        int h = Math.max(64, Math.round(fullH * scale));

        if (this.ferrum$scaledTarget == null) {
            this.ferrum$scaledTarget = new TextureTarget("ferrum_scaled_world", w, h,
                GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
        } else if (this.ferrum$scaledTarget.width != w || this.ferrum$scaledTarget.height != h) {
            this.ferrum$scaledTarget.resize(w, h);
        }

        if (this.ferrum$levelTargetsWidth != w || this.ferrum$levelTargetsHeight != h) {
            this.minecraft.levelRenderer.resize(w, h);
            this.hud3DTarget.resize(w, h);
            this.ferrum$levelTargetsWidth = w;
            this.ferrum$levelTargetsHeight = h;
        }

        // Vanilla clears the main target at the start of the frame with reverse-Z depth 0.0.
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
            this.ferrum$scaledTarget.getColorTexture(), FERRUM$CLEAR,
            this.ferrum$scaledTarget.getDepthTexture(), 0.0);

        this.ferrum$realTarget = real;
        this.mainRenderTarget = this.ferrum$scaledTarget;
        FerrumState.renderScaleActiveWidth = w;
        FerrumState.renderScaleActiveHeight = h;
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel()V", shift = At.Shift.AFTER))
    private void ferrum$endScaledWorld(CallbackInfo ci) {
        RenderTarget real = this.ferrum$realTarget;
        if (real == null) {
            return;
        }
        this.mainRenderTarget = real;
        this.ferrum$realTarget = null;
        if (FerrumState.fsr && !FerrumState.fsrFailed && !FerrumState.renderScaleSharp) {
            try {
                this.ferrum$fsr().upscale(this.ferrum$scaledTarget, real, FerrumState.fsrSharpness);
                return;
            } catch (Throwable t) {
                // Shader compile or driver failure on this GL layer: fall back to bilinear for the session.
                FerrumState.fsrFailed = true;
                dev.ferrum.client.FerrumClient.LOGGER.error("Ferrum FSR failed, using bilinear upscaling", t);
            }
        }
        this.ferrum$upscale(this.ferrum$scaledTarget, real);
    }

    /** Window resize: vanilla resizes the level renderer to the window; keep it at the scaled size. */
    @Inject(method = "resize", at = @At("TAIL"))
    private void ferrum$keepScaledLevelTargets(int width, int height, CallbackInfo ci) {
        // Force the next frame to re-apply the scaled size (vanilla just set them to full size).
        this.ferrum$levelTargetsWidth = width;
        this.ferrum$levelTargetsHeight = height;
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void ferrum$closeScaled(CallbackInfo ci) {
        if (this.ferrum$fsr != null) {
            this.ferrum$fsr.close();
        }
        if (this.ferrum$scaledTarget != null) {
            this.ferrum$scaledTarget.destroyBuffers();
            this.ferrum$scaledTarget = null;
        }
    }

    @Unique
    private void ferrum$restoreLevelTargets(int fullW, int fullH) {
        if (this.ferrum$fsr != null) {
            this.ferrum$fsr.close();
        }
        FerrumState.renderScaleActiveWidth = 0;
        FerrumState.renderScaleActiveHeight = 0;
        if (this.ferrum$levelTargetsWidth != 0
            && (this.ferrum$levelTargetsWidth != fullW || this.ferrum$levelTargetsHeight != fullH)) {
            this.minecraft.levelRenderer.resize(fullW, fullH);
            this.hud3DTarget.resize(fullW, fullH);
        }
        this.ferrum$levelTargetsWidth = 0;
        this.ferrum$levelTargetsHeight = 0;
        if (this.ferrum$scaledTarget != null) {
            // Free the VRAM when the user goes back to 100%.
            this.ferrum$scaledTarget.destroyBuffers();
            this.ferrum$scaledTarget = null;
        }
    }

    /** Full-screen triangle blit with bilinear filtering and no blending (opaque copy). */
    @Unique
    private void ferrum$upscale(RenderTarget source, RenderTarget destination) {
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            () -> "Ferrum render scale upscale",
            destination.getColorTextureView(), Optional.empty(),
            destination.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.TRACY_BLIT));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", source.getColorTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(
                    FerrumState.renderScaleSharp ? FilterMode.NEAREST : FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
    }
}
