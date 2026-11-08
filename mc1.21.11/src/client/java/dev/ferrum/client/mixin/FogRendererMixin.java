package dev.ferrum.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ferrum.client.state.FerrumState;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.joml.Vector4f;

/**
 * Distance-fog toggle, 1.21.11 version. Here setupFog builds FogData locally and uploads it in the
 * same method, so the fields are pushed out right before the fog buffer is mapped. Water, lava,
 * powder snow, blindness and darkness fog (environmental fog) are untouched.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    private static final float FAR = 1.0E6F;

    @Inject(method = "setupFog", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/systems/CommandEncoder;mapBuffer(Lcom/mojang/blaze3d/buffers/GpuBuffer;ZZ)Lcom/mojang/blaze3d/buffers/GpuBuffer$MappedView;"))
    private void ferrum$distanceFog(CallbackInfoReturnable<Vector4f> cir, @Local FogData data) {
        if (FerrumState.fog) {
            return;
        }
        data.renderDistanceStart = FAR;
        data.renderDistanceEnd = FAR;
        data.skyEnd = FAR;
        data.cloudEnd = FAR;
    }
}
