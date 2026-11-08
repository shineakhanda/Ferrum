package dev.ferrum.client.mixin;

import dev.ferrum.client.state.FerrumState;
import java.util.Map;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Shadow
    @Final
    private Map<ParticleRenderType, ParticleGroup<?>> particles;

    @Shadow
    @Final
    private Queue<Particle> particlesToAdd;

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void ferrum$limitParticles(Particle particle, CallbackInfo ci) {
        if (dev.ferrum.client.render.ParticleFilter.disabled(particle)) {
            ci.cancel();
            return;
        }
        if (particle.getGroup() == ParticleRenderType.NO_RENDER) {
            return;
        }

        if (FerrumState.particleLimiting) {
            int pending = FerrumState.particleCount + this.particlesToAdd.size();
            if (pending >= FerrumState.maxParticles) {
                ci.cancel();
                return;
            }
        }

        if (FerrumState.particleDistance > 0.0 && FerrumState.keepLimitedParticles
            && particle.getParticleLimit().isPresent()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || FerrumState.particleDistance <= 0.0) {
            return;
        }

        ParticleAccessor accessor = (ParticleAccessor) (Object) particle;
        double dx = accessor.ferrum$getX() - minecraft.player.getX();
        double dy = accessor.ferrum$getY() - minecraft.player.getY();
        double dz = accessor.ferrum$getZ() - minecraft.player.getZ();
        double maxDistance = FerrumState.particleDistance;
        if (dx * dx + dy * dy + dz * dz > maxDistance * maxDistance) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void ferrum$updateParticleCount(CallbackInfo ci) {
        int total = this.particlesToAdd.size();
        for (ParticleGroup<?> group : this.particles.values()) {
            total += group.size();
        }
        FerrumState.particleCount = total;
    }
}
