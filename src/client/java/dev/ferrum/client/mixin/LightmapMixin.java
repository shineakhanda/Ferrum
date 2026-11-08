package dev.ferrum.client.mixin;

import dev.ferrum.client.state.FerrumState;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lightmap cache. 26.3 extracts a new lightmap state every tick (20/s) and redraws the lightmap
 * texture with its own render pass even when the values are identical. On tile-based mobile GPUs and
 * GL translation layers each extra pass into another framebuffer flushes tile memory. If no input
 * moved by a visually invisible amount since the last upload, the redraw is skipped; torch flicker
 * accumulates against the last uploaded value, so it stays visible.
 */
@Mixin(Lightmap.class)
public abstract class LightmapMixin {
    @Unique private static final float EPS = 0.002F;
    @Unique private static final float FLICKER_EPS = 0.006F;

    @Unique private boolean ferrum$valid;
    @Unique private float ferrum$blockFactor, ferrum$skyFactor, ferrum$brightness, ferrum$darkness,
        ferrum$nightVision, ferrum$bossDarkening;
    @Unique private float[] ferrum$vectors;

    @Inject(method = "render", at = @At("HEAD"))
    private void ferrum$skipUnchanged(LightmapRenderState state, CallbackInfo ci) {
        if (!state.needsUpdate) {
            return;
        }
        if (!FerrumState.lightmapCache) {
            this.ferrum$valid = false;
            return;
        }

        float[] v = this.ferrum$vectors;
        if (v == null) {
            v = this.ferrum$vectors = new float[12];
        }
        boolean same = this.ferrum$valid
            && Math.abs(this.ferrum$blockFactor - state.blockFactor) < FLICKER_EPS
            && Math.abs(this.ferrum$skyFactor - state.skyFactor) < EPS
            && Math.abs(this.ferrum$brightness - state.brightness) < EPS
            && Math.abs(this.ferrum$darkness - state.darknessEffectScale) < EPS
            && Math.abs(this.ferrum$nightVision - state.nightVisionEffectIntensity) < EPS
            && Math.abs(this.ferrum$bossDarkening - state.bossOverlayWorldDarkening) < EPS
            && ferrum$same(v, 0, state.blockLightTint)
            && ferrum$same(v, 3, state.skyLightColor)
            && ferrum$same(v, 6, state.ambientColor)
            && ferrum$same(v, 9, state.nightVisionColor);

        if (same) {
            state.needsUpdate = false;
            FerrumState.lightmapSkipped++;
            return;
        }

        this.ferrum$valid = true;
        this.ferrum$blockFactor = state.blockFactor;
        this.ferrum$skyFactor = state.skyFactor;
        this.ferrum$brightness = state.brightness;
        this.ferrum$darkness = state.darknessEffectScale;
        this.ferrum$nightVision = state.nightVisionEffectIntensity;
        this.ferrum$bossDarkening = state.bossOverlayWorldDarkening;
        ferrum$store(v, 0, state.blockLightTint);
        ferrum$store(v, 3, state.skyLightColor);
        ferrum$store(v, 6, state.ambientColor);
        ferrum$store(v, 9, state.nightVisionColor);
        FerrumState.lightmapUpdates++;
    }

    @Unique
    private static boolean ferrum$same(float[] v, int i, Vector3fc c) {
        return Math.abs(v[i] - c.x()) < EPS && Math.abs(v[i + 1] - c.y()) < EPS && Math.abs(v[i + 2] - c.z()) < EPS;
    }

    @Unique
    private static void ferrum$store(float[] v, int i, Vector3fc c) {
        v[i] = c.x();
        v[i + 1] = c.y();
        v[i + 2] = c.z();
    }
}
