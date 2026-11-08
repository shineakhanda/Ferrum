package dev.ferrum.client.render;

import dev.ferrum.client.state.FerrumState;
import net.minecraft.client.particle.CampfireSmokeParticle;
import net.minecraft.client.particle.DripParticle;
import net.minecraft.client.particle.ExplodeParticle;
import net.minecraft.client.particle.FallingLeavesParticle;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.HugeExplosionParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SmokeParticle;
import net.minecraft.client.particle.SpellParticle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.particle.WaterDropParticle;

/**
 * Sodium Extra-style per-type particle toggles, checked when a particle is added (before it is ever
 * ticked or drawn). Grouped by what players recognise rather than by particle id.
 */
public final class ParticleFilter {
    private ParticleFilter() {
    }

    public static boolean disabled(Particle particle) {
        if (!FerrumState.particleBlockBreak && particle instanceof TerrainParticle) {
            return true;
        }
        if (!FerrumState.particleRain && (particle instanceof WaterDropParticle || particle instanceof DripParticle)) {
            return true;
        }
        if (!FerrumState.particleExplosions && (particle instanceof HugeExplosionParticle || particle instanceof ExplodeParticle)) {
            return true;
        }
        if (!FerrumState.particleCampfireSmoke && particle instanceof CampfireSmokeParticle) {
            return true;
        }
        if (!FerrumState.particlePotion && particle instanceof SpellParticle) {
            return true;
        }
        if (!FerrumState.particleLeaves && particle instanceof FallingLeavesParticle) {
            return true;
        }
        return !FerrumState.particleFlames && (particle instanceof FlameParticle || particle instanceof SmokeParticle);
    }
}
