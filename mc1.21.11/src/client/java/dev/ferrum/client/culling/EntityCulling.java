package dev.ferrum.client.culling;

import dev.ferrum.client.state.FerrumState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.phys.AABB;

/** Entity-side decisions for the render dispatcher hook: type toggles and occlusion culling. */
public final class EntityCulling {
    private EntityCulling() {
    }

    /** Sodium Extra-style "don't render this kind of entity" toggles. Glow item frames are item frames. */
    public static boolean typeDisabled(Entity entity) {
        return (!FerrumState.renderItemFrames && entity instanceof ItemFrame)
            || (!FerrumState.renderArmorStands && entity instanceof ArmorStand)
            || (!FerrumState.renderPaintings && entity instanceof Painting);
    }

    /**
     * True if the entity passed vanilla's frustum test but no ray from the camera reaches it.
     * The camera entity, glowing entities (visible through walls by design), bosses and anything
     * carrying the camera are never occlusion-culled.
     */
    public static boolean occluded(Entity entity, double camX, double camY, double camZ) {
        if (!FerrumState.occlusionCulling) {
            return false;
        }
        OcclusionCuller.setCamera(camX, camY, camZ);
        Minecraft mc = Minecraft.getInstance();
        Entity camera = mc.getCameraEntity();
        if (entity == camera || entity.isCurrentlyGlowing()
            || entity instanceof EnderDragon || entity instanceof WitherBoss
            || (camera != null && (entity.hasIndirectPassenger(camera) || camera.getVehicle() == entity))) {
            return false;
        }
        AABB box = entity.getBoundingBox();
        // Pad sideways for animation overshoot (swinging limbs, held items) and upwards for heads/name tags.
        boolean hidden = OcclusionCuller.isEntityHidden(entity.getId(),
            box.minX - 0.3, box.minY - 0.1, box.minZ - 0.3, box.maxX + 0.3, box.maxY + 0.6, box.maxZ + 0.3);
        if (hidden) {
            FerrumState.occlusionHidden++;
        }
        return hidden;
    }
}
