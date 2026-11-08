package dev.ferrum.client.mixin;

import dev.ferrum.client.access.EntityVisibility;
import dev.ferrum.client.state.FerrumState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    /**
     * Client-side entity tick culling. The client ticks every entity it knows about (interpolation,
     * walk animation, client-side effects) even when it is far away and off screen. The server still
     * runs the real AI, so for an entity nobody can see, ticking it less often is invisible.
     * Entities that were drawn in the last few ticks, players, projectiles, bosses and anything
     * close by always tick normally.
     */
    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void ferrum$tickCull(Entity entity, CallbackInfo ci) {
        if (!FerrumState.entityTickCulling) {
            return;
        }
        if (entity instanceof Player || entity instanceof Projectile || entity instanceof EnderDragon
            || entity instanceof WitherBoss || entity.isVehicle()) {
            return;
        }
        int now = FerrumState.clientTick;
        if (now - ((EntityVisibility) entity).ferrum$lastVisibleTick() <= 5) {
            return;
        }
        Entity camera = Minecraft.getInstance().getCameraEntity();
        if (camera == null) {
            return;
        }
        double distSq = entity.distanceToSqr(camera);
        int interval;
        if (distSq < 24.0 * 24.0) {
            return;
        } else if (distSq < 48.0 * 48.0) {
            interval = 2;
        } else if (distSq < 96.0 * 96.0) {
            interval = 4;
        } else {
            interval = 8;
        }
        // Stagger by id so throttled entities don't all tick on the same frame.
        if (((now + entity.getId()) % interval) != 0) {
            FerrumState.entityTicksSkipped++;
            ci.cancel();
        }
    }

    /** Ambient block effects (torch smoke, drips, leaf particles) sample 667 random blocks per pass. */
    @ModifyConstant(method = "animateTick", constant = @Constant(intValue = 667))
    private int ferrum$ambientDensity(int original) {
        return Math.max(1, original * FerrumState.ambientParticleDensity / 100);
    }
}
