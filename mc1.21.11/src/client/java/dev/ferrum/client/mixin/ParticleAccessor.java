package dev.ferrum.client.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ParticleAccessor {
    @Accessor("x")
    double ferrum$getX();

    @Accessor("y")
    double ferrum$getY();

    @Accessor("z")
    double ferrum$getZ();
}
