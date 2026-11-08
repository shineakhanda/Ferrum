package dev.ferrum.client.mixin;

import dev.ferrum.client.state.FerrumState;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sodium Extra style sky detail toggles. Each piece is a separate draw call we can skip. */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void ferrum$skyDisc(CallbackInfo ci) {
        if (!FerrumState.sky) {
            ci.cancel();
        }
    }

    @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void ferrum$darkDisc(CallbackInfo ci) {
        if (!FerrumState.sky) {
            ci.cancel();
        }
    }

    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
    private void ferrum$endSky(CallbackInfo ci) {
        if (!FerrumState.sky) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
    private void ferrum$stars(CallbackInfo ci) {
        if (!FerrumState.stars) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void ferrum$sun(CallbackInfo ci) {
        if (!FerrumState.sun) {
            ci.cancel();
        }
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void ferrum$moon(CallbackInfo ci) {
        if (!FerrumState.moon) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void ferrum$sunrise(CallbackInfo ci) {
        if (!FerrumState.sunriseSunset) {
            ci.cancel();
        }
    }
}
