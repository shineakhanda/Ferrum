package dev.ferrum.client.mixin;

import dev.ferrum.client.state.FerrumState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** "Name tags" toggle: hides custom names over mobs, item frames and armor stands. Player names stay. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void ferrum$nameTags(Entity entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if (!FerrumState.renderNameTags && !(entity instanceof Player)) {
            cir.setReturnValue(false);
        }
    }
}
