package dev.ferrum.client.mixin;

import dev.ferrum.client.state.FerrumState;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium Extra style fog control. Only the render-distance fog is pushed out; water, lava,
 * powder snow, blindness and darkness fog stay intact because they are gameplay information.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    private static final float FAR = 1.0E6F;

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void ferrum$distanceFog(CallbackInfoReturnable<FogData> cir) {
        if (FerrumState.fog) {
            return;
        }
        FogData data = cir.getReturnValue();
        if (data == null) {
            return;
        }
        data.renderDistanceStart = FAR;
        data.renderDistanceEnd = FAR;
        data.skyEnd = FAR;
        data.cloudEnd = FAR;
    }
}
