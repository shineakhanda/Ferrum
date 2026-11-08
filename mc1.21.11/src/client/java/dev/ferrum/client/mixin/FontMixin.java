package dev.ferrum.client.mixin;

import dev.ferrum.client.state.FerrumState;
import dev.ferrum.client.util.TextWidthCache;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Font.class)
public abstract class FontMixin {
    @Inject(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void ferrum$cachedWidth(String text, CallbackInfoReturnable<Integer> cir) {
        if (!FerrumState.textWidthCache) {
            return;
        }
        Integer cached = TextWidthCache.get((Font) (Object) this, text);
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "width(Ljava/lang/String;)I", at = @At("RETURN"))
    private void ferrum$storeWidth(String text, CallbackInfoReturnable<Integer> cir) {
        if (!FerrumState.textWidthCache) {
            return;
        }
        TextWidthCache.put((Font) (Object) this, text, cir.getReturnValue());
    }
}
