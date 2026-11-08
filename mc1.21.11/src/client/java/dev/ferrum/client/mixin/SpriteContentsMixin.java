package dev.ferrum.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.ferrum.client.access.SpriteOpacity;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scans each sprite once when it is loaded and remembers whether every pixel of every animation
 * frame is fully opaque. The chunk builder uses this to move cutout quads that can never discard a
 * fragment into the solid pass (see RenderPassOptimizer).
 */
@Mixin(SpriteContents.class)
public abstract class SpriteContentsMixin implements SpriteOpacity {
    @Shadow
    @Final
    private NativeImage originalImage;

    @Unique
    private boolean ferrum$fullyOpaque;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ferrum$scanOpacity(CallbackInfo ci) {
        NativeImage image = this.originalImage;
        if (image == null) {
            this.ferrum$fullyOpaque = false;
            return;
        }
        try {
            int w = image.getWidth();
            int h = image.getHeight();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if ((image.getPixel(x, y) >>> 24) != 0xFF) {
                        this.ferrum$fullyOpaque = false;
                        return;
                    }
                }
            }
            this.ferrum$fullyOpaque = true;
        } catch (Throwable t) {
            this.ferrum$fullyOpaque = false;
        }
    }

    @Override
    public boolean ferrum$isFullyOpaque() {
        return this.ferrum$fullyOpaque;
    }
}
