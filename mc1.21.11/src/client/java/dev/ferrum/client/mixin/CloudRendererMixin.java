package dev.ferrum.client.mixin;

import dev.ferrum.client.state.FerrumState;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sodium Extra style "Clouds: OFF" toggle. Skips both the normal and OIT cloud passes. */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {
    @Inject(
        method = "render",
        at = @At("HEAD"),
        cancellable = true
    )
    private void ferrum$skipClouds(CallbackInfo ci) {
        if (!FerrumState.clouds) {
            ci.cancel();
        }
    }
}
