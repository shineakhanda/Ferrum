package dev.ferrum.client.mixin;

import dev.ferrum.client.culling.OcclusionCuller;
import dev.ferrum.client.state.FerrumState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Block entity culling before extraction: type toggles, distance culling, then ray-traced occlusion
 * (chests, signs, banners, skulls, shulker boxes etc. behind walls or underground are skipped).
 * Renderers that draw off screen (beacon beams, end gateways) are only affected by their toggles.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Shadow
    private Vec3 cameraPos;

    @Shadow
    public abstract <E extends BlockEntity, S extends BlockEntityRenderState> BlockEntityRenderer<E, S> getRenderer(E blockEntity);

    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private void ferrum$cull(BlockEntity blockEntity, float partialTicks,
                             ModelFeatureRenderer.CrumblingOverlay breakProgress,
                             CallbackInfoReturnable<BlockEntityRenderState> cir) {
        if ((!FerrumState.renderBeaconBeams && blockEntity instanceof BeaconBlockEntity)
            || (!FerrumState.renderEnchantBook && blockEntity instanceof EnchantingTableBlockEntity)) {
            cir.setReturnValue(null);
            return;
        }
        if (this.cameraPos == null) {
            return;
        }
        boolean distanceCull = FerrumState.blockEntityCulling;
        boolean occlusion = FerrumState.occlusionCulling && FerrumState.occlusionBlockEntities;
        if (!distanceCull && !occlusion) {
            return;
        }
        BlockEntityRenderer<BlockEntity, BlockEntityRenderState> renderer = this.getRenderer(blockEntity);
        if (renderer != null && renderer.shouldRenderOffScreen()) {
            return;
        }
        BlockPos pos = blockEntity.getBlockPos();
        if (distanceCull) {
            double maxDistance = FerrumState.blockEntityCullDistance;
            if (pos.distToCenterSqr(this.cameraPos.x, this.cameraPos.y, this.cameraPos.z) > maxDistance * maxDistance) {
                FerrumState.blockEntityCulled++;
                cir.setReturnValue(null);
                return;
            }
        }
        if (occlusion) {
            OcclusionCuller.setCamera(this.cameraPos.x, this.cameraPos.y, this.cameraPos.z);
            if (OcclusionCuller.isBlockHidden(pos.asLong(), pos.getX(), pos.getY(), pos.getZ())) {
                FerrumState.occlusionHidden++;
                cir.setReturnValue(null);
            }
        }
    }
}
