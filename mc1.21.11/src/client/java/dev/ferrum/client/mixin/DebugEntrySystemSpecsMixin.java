package dev.ferrum.client.mixin;

import dev.ferrum.client.FerrumClient;
import dev.ferrum.client.hardware.HardwareProfile;
import dev.ferrum.client.render.AnimatedSpriteTracker;
import dev.ferrum.client.state.FerrumState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.debug.DebugEntrySystemSpecs;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the Ferrum renderer block to the F3 system-specs column (right side), the same place
 * Sodium reports itself, so players and bug reports can see which renderer features are active.
 */
@Mixin(DebugEntrySystemSpecs.class)
public abstract class DebugEntrySystemSpecsMixin {
    @Inject(method = "display", at = @At("TAIL"))
    private void ferrum$rendererInfo(DebugScreenDisplayer displayer, @Nullable Level level,
                                     @Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk,
                                     CallbackInfo ci) {
        displayer.addLine("");
        displayer.addLine(ChatFormatting.GRAY + "Ferrum Renderer " + ChatFormatting.WHITE + FerrumClient.VERSION
            + ChatFormatting.GRAY + " (" + FerrumClient.presetDisplayName() + ")");

        HardwareProfile profile = FerrumClient.PROFILE;
        if (profile != null) {
            String path = profile.android() ? "Android" : profile.glTranslationLayer() ? "GL translation" : profile.backend();
            displayer.addLine(ChatFormatting.GRAY + "Path: " + ChatFormatting.WHITE + path
                + (profile.mobileGpu() ? ChatFormatting.GRAY + " / mobile GPU" : ""));
        }

        int w = FerrumState.renderScaleActiveWidth;
        displayer.addLine(ChatFormatting.GRAY + "Render scale: " + ChatFormatting.WHITE + FerrumState.renderScale + "%"
            + (w > 0 ? ChatFormatting.GRAY + " (" + w + "x" + FerrumState.renderScaleActiveHeight + ")" : "")
            + (w > 0 ? ChatFormatting.GRAY + " " + (FerrumState.fsr && !FerrumState.fsrFailed && !FerrumState.renderScaleSharp ? "FSR" : FerrumState.renderScaleSharp ? "nearest" : "bilinear") : ""));
        displayer.addLine(ChatFormatting.GRAY + "Terrain draw: " + ChatFormatting.WHITE
            + (FerrumState.multiDrawActive ? "multi-draw" : "per-section")
            + ChatFormatting.GRAY + "  Anim tex: " + ChatFormatting.WHITE
            + (FerrumState.animateOnlyVisible ? AnimatedSpriteTracker.lastActiveCount + " visible, "
                + AnimatedSpriteTracker.lastSkipped + " skipped" : "all"));
        displayer.addLine(ChatFormatting.GRAY + "Culled/s: " + ChatFormatting.WHITE
            + "E " + FerrumState.entityCulledPerSecond + "  BE " + FerrumState.blockEntityCulledPerSecond
            + "  ticks " + FerrumState.entityTicksSkippedPerSecond);
        displayer.addLine(ChatFormatting.GRAY + "Lightmap: " + ChatFormatting.WHITE + FerrumState.lightmapUpdates
            + " redraws, " + FerrumState.lightmapSkipped + " skipped"
            + "  Solid-promoted quads: " + FerrumState.quadsPromoted);
        displayer.addLine(ChatFormatting.GRAY + "Compat: " + ChatFormatting.WHITE + dev.ferrum.client.compat.ModCompat.summary());
        displayer.addLine(ChatFormatting.GRAY + "Face culling: " + ChatFormatting.WHITE
            + (FerrumState.faceCulling ? dev.ferrum.client.render.terrain.FaceCullStats.culledQuadsPerFrame + " quads skipped, "
                + dev.ferrum.client.render.terrain.FaceCullStats.splitSectionsPerFrame + " sections split, "
                + dev.ferrum.client.render.terrain.FaceCullStats.sortedMeshes + " meshes sorted" : "off"));
        displayer.addLine(ChatFormatting.GRAY + "Occlusion: " + ChatFormatting.WHITE
            + (FerrumState.occlusionCulling ? dev.ferrum.client.culling.OcclusionCuller.lastHidden + "/"
                + dev.ferrum.client.culling.OcclusionCuller.lastTargets + " hidden, "
                + FerrumState.occlusionHiddenPerSecond + "/s skipped, pass "
                + (dev.ferrum.client.culling.OcclusionCuller.lastPassMicros / 1000.0) + " ms" : "off"));
    }
}
