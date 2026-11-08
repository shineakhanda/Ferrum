package dev.ferrum.client.mixin;

import dev.ferrum.client.culling.OcclusionCuller;
import dev.ferrum.client.state.FerrumState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
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
 * Globally rendered block entities (beacon beams, end gateways) are only affected by their toggles.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Shadow
    private Vec3 cameraPos;

    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private void ferrum$cull(
        BlockEntity blockEntity,
        float partialTicks,
        ModelFeatureRenderer.CrumblingOverlay breakProgress,
        boolean isGloballyRendered,
        CallbackInfoReturnable<BlockEntityRenderState> cir
    ) {
        if ((!FerrumState.renderBeaconBeams && blockEntity instanceof BeaconBlockEntity)
            || (!FerrumState.renderEnchantBook && blockEntity instanceof EnchantingTableBlockEntity)) {
            cir.setReturnValue(null);
            return;
        }
        if (isGloballyRendered || this.cameraPos == null) {
            return;
        }
        BlockPos pos = blockEntity.getBlockPos();
        if (FerrumState.blockEntityCulling) {
            double maxDistance = FerrumState.blockEntityCullDistance;
            if (pos.distToCenterSqr(this.cameraPos.x, this.cameraPos.y, this.cameraPos.z) > maxDistance * maxDistance) {
                FerrumState.blockEntityCulled++;
                cir.setReturnValue(null);
                return;
            }
        }
        if (FerrumState.occlusionCulling && FerrumState.occlusionBlockEntities) {
            OcclusionCuller.setCamera(this.cameraPos.x, this.cameraPos.y, this.cameraPos.z);
            if (OcclusionCuller.isBlockHidden(pos.asLong(), pos.getX(), pos.getY(), pos.getZ())) {
                FerrumState.occlusionHidden++;
                cir.setReturnValue(null);
            }
        }
    }
}
