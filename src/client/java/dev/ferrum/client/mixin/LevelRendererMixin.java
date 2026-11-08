package dev.ferrum.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.DeviceInfo;
import dev.ferrum.client.state.FerrumState;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Batched terrain drawing. Minecraft already has a multi-draw-indirect terrain path (one draw call
 * per buffer instead of one per chunk section) but disables it on every non-Arc Intel GPU because of
 * driver bugs. This lets the user force it on when the driver supports the extensions; it is
 * experimental and off by default.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow
    @Final
    @Mutable
    private boolean multiDrawIndirectAvailable;

    @Unique
    private Boolean ferrum$vanillaMultiDraw;

    @Unique
    private Boolean ferrum$deviceCanMultiDraw;

    @Inject(method = "render", at = @At("HEAD"))
    private void ferrum$forceMultiDraw(CallbackInfo ci) {
        if (this.ferrum$vanillaMultiDraw == null) {
            this.ferrum$vanillaMultiDraw = this.multiDrawIndirectAvailable;
            DeviceInfo info = RenderSystem.getDevice().getDeviceInfo();
            this.ferrum$deviceCanMultiDraw = info.limits().maxDrawIndirectDrawCount() > 0
                && info.features().nonZeroFirstInstance();
            FerrumState.multiDrawVanilla = this.ferrum$vanillaMultiDraw;
            FerrumState.multiDrawSupported = this.ferrum$deviceCanMultiDraw;
        }
        boolean active = this.ferrum$vanillaMultiDraw
            || (FerrumState.forceMultiDraw && this.ferrum$deviceCanMultiDraw);
        this.multiDrawIndirectAvailable = active;
        FerrumState.multiDrawActive = active;
    }
}
