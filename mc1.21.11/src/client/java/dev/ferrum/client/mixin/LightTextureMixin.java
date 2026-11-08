package dev.ferrum.client.mixin;

import dev.ferrum.client.state.FerrumState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.EndFlashState;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lightmap cache. Vanilla redraws the 16x16 lightmap 20 times a second (every tick sets
 * {@code updateLightTexture}) even when nothing about the lighting changed: each redraw maps a
 * uniform buffer and runs a separate render pass into another framebuffer. On tile-based phone GPUs
 * and GL translation layers (MobileGlues, GL4ES) a framebuffer switch flushes the tile memory, so
 * this is real per-frame cost for an image that is usually identical.
 *
 * <p>Here the same inputs vanilla feeds the lightmap shader are computed first; if none moved by
 * more than a visually invisible amount since the last upload, the redraw is skipped. Torch flicker
 * still accumulates against the last uploaded value, so flicker stays visible, just quantised.
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
    @Unique private static final float EPS = 0.002F;
    @Unique private static final float FLICKER_EPS = 0.006F;

    @Shadow private boolean updateLightTexture;
    @Shadow private float blockLightRedFlicker;
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private GameRenderer renderer;

    @Unique private boolean ferrum$valid;
    @Unique private int ferrum$skyColor;
    @Unique private float ferrum$ambient, ferrum$skyFactor, ferrum$flicker, ferrum$nightVision,
        ferrum$darknessScale, ferrum$darken, ferrum$gamma;
    @Unique private Object ferrum$level;

    @Inject(method = "updateLightTexture", at = @At("HEAD"), cancellable = true)
    private void ferrum$skipUnchanged(float partialTick, CallbackInfo ci) {
        if (!this.updateLightTexture) {
            return;
        }
        if (!FerrumState.lightmapCache) {
            this.ferrum$valid = false;
            return;
        }
        ClientLevel level = this.minecraft.level;
        LocalPlayer player = this.minecraft.player;
        if (level == null || player == null) {
            this.ferrum$valid = false;
            return;
        }
        EndFlashState endFlash = level.endFlashState();
        if (endFlash != null) {
            // End flashes change every frame; let vanilla handle them.
            this.ferrum$valid = false;
            FerrumState.lightmapUpdates++;
            return;
        }

        Camera camera = this.minecraft.gameRenderer.getMainCamera();
        int skyColor = (Integer) camera.attributeProbe().getValue(EnvironmentAttributes.SKY_LIGHT_COLOR, partialTick);
        float ambient = level.dimensionType().ambientLight();
        float skyFactor = (Float) camera.attributeProbe().getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, partialTick);
        float darknessOption = this.minecraft.options.darknessEffectScale().get().floatValue();
        float darkness = player.getEffectBlendFactor(MobEffects.DARKNESS, partialTick) * darknessOption;
        float darknessScale = Math.max(0.0F,
            Mth.cos((player.tickCount - partialTick) * (float) Math.PI * 0.025F) * 0.45F * darkness) * darknessOption;
        float waterVision = player.getWaterVision();
        float nightVision;
        if (player.hasEffect(MobEffects.NIGHT_VISION)) {
            nightVision = GameRenderer.getNightVisionScale(player, partialTick);
        } else if (waterVision > 0.0F && player.hasEffect(MobEffects.CONDUIT_POWER)) {
            nightVision = waterVision;
        } else {
            nightVision = 0.0F;
        }
        float darken = this.renderer.getDarkenWorldAmount(partialTick);
        float gamma = Math.max(0.0F, this.minecraft.options.gamma().get().floatValue() - darkness);
        float flicker = this.blockLightRedFlicker;

        boolean same = this.ferrum$valid
            && this.ferrum$level == level
            && this.ferrum$skyColor == skyColor
            && Math.abs(this.ferrum$ambient - ambient) < EPS
            && Math.abs(this.ferrum$skyFactor - skyFactor) < EPS
            && Math.abs(this.ferrum$flicker - flicker) < FLICKER_EPS
            && Math.abs(this.ferrum$nightVision - nightVision) < EPS
            && Math.abs(this.ferrum$darknessScale - darknessScale) < EPS
            && Math.abs(this.ferrum$darken - darken) < EPS
            && Math.abs(this.ferrum$gamma - gamma) < EPS;

        if (same) {
            this.updateLightTexture = false;
            FerrumState.lightmapSkipped++;
            ci.cancel();
            return;
        }

        this.ferrum$valid = true;
        this.ferrum$level = level;
        this.ferrum$skyColor = skyColor;
        this.ferrum$ambient = ambient;
        this.ferrum$skyFactor = skyFactor;
        this.ferrum$flicker = flicker;
        this.ferrum$nightVision = nightVision;
        this.ferrum$darknessScale = darknessScale;
        this.ferrum$darken = darken;
        this.ferrum$gamma = gamma;
        FerrumState.lightmapUpdates++;
    }
}
