package dev.ferrum.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ferrum.client.access.FaceRangesHolder;
import dev.ferrum.client.render.terrain.FaceCullStats;
import dev.ferrum.client.render.terrain.FaceRanges;
import dev.ferrum.client.state.FerrumState;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draw-time half of per-face terrain culling (26.3). Vanilla turns every visible section layer into
 * one {@link DynamicGpuData.IndexedDraw}, used by both the separate-draw and the multi-draw-indirect
 * path. For face-sorted opaque meshes that single draw is replaced by one draw per contiguous run of
 * facing groups that can face the camera; groups that only contain back faces are not submitted.
 */
@Mixin(LevelRenderer.class)
public abstract class TerrainDrawMixin {
    @Unique
    private int[] ferrum$runs;

    @Inject(method = "extractSectionDrawGroups", at = @At("HEAD"))
    private void ferrum$frameStats(CallbackInfoReturnable<Integer> cir) {
        FaceCullStats.endFrame();
    }

    @WrapOperation(method = "extractSectionDrawGroups", at = @At(value = "INVOKE",
        target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private boolean ferrum$splitByFace(List<Object> list, Object element, Operation<Boolean> original,
                                       @Local SectionRenderDispatcher.RenderSection section,
                                       @Local ChunkSectionLayer layer,
                                       @Local SectionMesh mesh) {
        if (!FerrumState.faceCulling || !(element instanceof DynamicGpuData.IndexedDraw draw)
            || !(mesh instanceof FaceRangesHolder holder)) {
            return original.call(list, element);
        }
        FaceRanges[] all = holder.ferrum$faceRanges();
        FaceRanges ranges = all == null ? null : all[layer.ordinal()];
        if (ranges == null || ranges.totalQuads() * 6 != draw.indexCount()) {
            return original.call(list, element);
        }
        Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
        BlockPos origin = section.getRenderOrigin();
        int[] runs = this.ferrum$runs;
        if (runs == null) {
            runs = this.ferrum$runs = new int[16];
        }
        int count = ranges.visibleRuns(cam.x - origin.getX(), cam.y - origin.getY(), cam.z - origin.getZ(), runs);
        if (count < 0) {
            return original.call(list, element);
        }
        for (int i = 0; i < count; i++) {
            original.call(list, new DynamicGpuData.IndexedDraw(runs[i * 2 + 1] * 6, draw.instanceCount(),
                draw.firstIndex() + runs[i * 2] * 6, draw.baseVertex(), draw.baseInstance()));
        }
        return true;
    }
}
