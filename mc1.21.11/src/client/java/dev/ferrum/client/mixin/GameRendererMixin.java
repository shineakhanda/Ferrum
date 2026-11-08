package dev.ferrum.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.ferrum.client.state.FerrumState;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ferrum render scale, 1.21.11 version. Same design as the 26.3 build: the 3D world is drawn into a
 * smaller target and upscaled; the GUI, post effects and screenshots stay at full resolution.
 *
 * <p>Differences from 26.3: the main target is a field on {@link Minecraft}, depth clears to 1.0
 * (no reverse-Z), and the level renderer is the only other owner of screen-sized targets.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
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

    @Unique
    private int ferrum$levelTargetsWidth;

    @Unique
    private int ferrum$levelTargetsHeight;

    @Inject(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V"))
    private void ferrum$beginScaledWorld(CallbackInfo ci) {
        MinecraftAccessor access = (MinecraftAccessor) this.minecraft;
        RenderTarget real = access.ferrum$getMainRenderTarget();
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
            this.ferrum$scaledTarget = new TextureTarget("ferrum_scaled_world", w, h, true);
        } else if (this.ferrum$scaledTarget.width != w || this.ferrum$scaledTarget.height != h) {
            this.ferrum$scaledTarget.resize(w, h);
        }

        if (this.ferrum$levelTargetsWidth != w || this.ferrum$levelTargetsHeight != h) {
            this.minecraft.levelRenderer.resize(w, h);
            this.ferrum$levelTargetsWidth = w;
            this.ferrum$levelTargetsHeight = h;
        }

        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
            this.ferrum$scaledTarget.getColorTexture(), 0xFF000000,
            this.ferrum$scaledTarget.getDepthTexture(), 1.0);

        this.ferrum$realTarget = real;
        access.ferrum$setMainRenderTarget(this.ferrum$scaledTarget);
        FerrumState.renderScaleActiveWidth = w;
        FerrumState.renderScaleActiveHeight = h;
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
        shift = At.Shift.AFTER))
    private void ferrum$endScaledWorld(CallbackInfo ci) {
        RenderTarget real = this.ferrum$realTarget;
        if (real == null) {
            return;
        }
        ((MinecraftAccessor) this.minecraft).ferrum$setMainRenderTarget(real);
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

    @Inject(method = "resize", at = @At("TAIL"))
    private void ferrum$keepScaledLevelTargets(int width, int height, CallbackInfo ci) {
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
        }
        this.ferrum$levelTargetsWidth = 0;
        this.ferrum$levelTargetsHeight = 0;
        if (this.ferrum$scaledTarget != null) {
            this.ferrum$scaledTarget.destroyBuffers();
            this.ferrum$scaledTarget = null;
        }
    }

    /** Full-screen triangle, bilinear (or nearest) sample, no blending. */
    @Unique
    private void ferrum$upscale(RenderTarget source, RenderTarget destination) {
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            () -> "Ferrum render scale upscale", destination.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(RenderPipelines.TRACY_BLIT);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", source.getColorTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(
                    FerrumState.renderScaleSharp ? FilterMode.NEAREST : FilterMode.LINEAR));
            pass.draw(0, 3);
        }
    }
}
