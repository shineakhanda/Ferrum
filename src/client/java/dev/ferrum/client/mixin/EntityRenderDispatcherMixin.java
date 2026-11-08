package dev.ferrum.client.mixin;

import dev.ferrum.client.state.FerrumState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void ferrum$distanceCull(
        Entity entity,
        Frustum frustum,
        double camX,
        double camY,
        double camZ,
        float partialTicks,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (dev.ferrum.client.culling.EntityCulling.typeDisabled(entity)) {
            cir.setReturnValue(false);
            return;
        }
        if (!FerrumState.entityCulling) {
            return;
        }
        if (!FerrumState.cullPlayers && entity instanceof Player) {
            return;
        }
        if (entity instanceof EnderDragon || entity instanceof WitherBoss) {
            return;
        }

        double maxDistance = FerrumState.entityCullDistance
            + 1.0 + Math.max(entity.getBbWidth(), entity.getBbHeight()) * 0.5;
        double dx = entity.getX() - camX;
        double dy = entity.getY() - camY;
        double dz = entity.getZ() - camZ;
        if (dx * dx + dy * dy + dz * dz > maxDistance * maxDistance) {
            FerrumState.entityCulled++;
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void ferrum$markVisible(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                     float partialTicks, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && dev.ferrum.client.culling.EntityCulling.occluded(entity, camX, camY, camZ)) {
            cir.setReturnValue(false);
            return;
        }
        if (cir.getReturnValueZ()) {
            ((dev.ferrum.client.access.EntityVisibility) entity).ferrum$setLastVisibleTick(FerrumState.clientTick);
        }
    }
}
