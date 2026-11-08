package dev.ferrum.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderPass;
import dev.ferrum.client.access.FaceRangesHolder;
import dev.ferrum.client.render.terrain.FaceCullStats;
import dev.ferrum.client.render.terrain.FaceRanges;
import dev.ferrum.client.state.FerrumState;
import java.util.List;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionBuffers;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draw-time half of per-face terrain culling (1.21.11). Vanilla adds one {@link RenderPass.Draw}
 * per visible section layer, covering the whole mesh with the shared sequential quad index buffer.
 * For face-sorted SOLID / CUTOUT meshes that draw is replaced by one draw per contiguous run of facing
 * groups that can face the camera ({@code firstIndex = firstQuad * 6}); back-facing groups are
 * not submitted at all.
 */
@Mixin(LevelRenderer.class)
public abstract class TerrainDrawMixin {
    @Unique
    private int[] ferrum$runs;

    @Inject(method = "prepareChunkRenders", at = @At("HEAD"))
    private void ferrum$frameStats(Matrix4fc modelView, double camX, double camY, double camZ,
                                   CallbackInfoReturnable<ChunkSectionsToRender> cir) {
        FaceCullStats.endFrame();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @WrapOperation(method = "prepareChunkRenders", at = @At(value = "INVOKE",
        target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private boolean ferrum$splitByFace(List<Object> list, Object element, Operation<Boolean> original,
                                       @Local(argsOnly = true, ordinal = 0) double camX,
                                       @Local(argsOnly = true, ordinal = 1) double camY,
                                       @Local(argsOnly = true, ordinal = 2) double camZ,
                                       @Local SectionRenderDispatcher.RenderSection section,
                                       @Local SectionMesh mesh,
                                       @Local SectionBuffers buffers) {
        if (!FerrumState.faceCulling || !(element instanceof RenderPass.Draw draw) || draw.indexBuffer() != null
            || !(mesh instanceof FaceRangesHolder holder)) {
            return original.call(list, element);
        }
        FaceRanges[] all = holder.ferrum$faceRanges();
        if (all == null) {
            return original.call(list, element);
        }
        FaceRanges ranges = null;
        if (buffers == mesh.getBuffers(ChunkSectionLayer.SOLID)) {
            ranges = all[ChunkSectionLayer.SOLID.ordinal()];
        } else if (buffers == mesh.getBuffers(ChunkSectionLayer.CUTOUT)) {
            ranges = all[ChunkSectionLayer.CUTOUT.ordinal()];
        }
        if (ranges == null || ranges.totalQuads() * 6 != draw.indexCount()) {
            return original.call(list, element);
        }
        BlockPos origin = section.getRenderOrigin();
        int[] runs = this.ferrum$runs;
        if (runs == null) {
            runs = this.ferrum$runs = new int[16];
        }
        int count = ranges.visibleRuns(camX - origin.getX(), camY - origin.getY(), camZ - origin.getZ(), runs);
        if (count < 0) {
            return original.call(list, element);
        }
        for (int i = 0; i < count; i++) {
            original.call(list, new RenderPass.Draw(draw.slot(), draw.vertexBuffer(), null, null,
                draw.firstIndex() + runs[i * 2] * 6, runs[i * 2 + 1] * 6, draw.uniformUploaderConsumer()));
        }
        return true;
    }
}
