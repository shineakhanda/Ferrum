package dev.ferrum.client.mixin;

import dev.ferrum.client.state.FerrumState;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sodium Extra style rain/snow toggle. Skips mesh building and both draw passes. */
@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {
    @Inject(
        method = "render",
        at = @At("HEAD"),
        cancellable = true
    )
    private void ferrum$skipWeather(CallbackInfo ci) {
        if (!FerrumState.weather) {
            ci.cancel();
        }
    }
}
