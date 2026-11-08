package dev.ferrum.client.render.terrain;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;
import dev.ferrum.client.state.FerrumState;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

/**
 * Build-time half of Ferrum's per-face terrain culling (26.3): while a section compiles, each
 * finished SOLID / CUTOUT mesh is reordered into facing groups before it is stored in the results
 * and uploaded. Translucent meshes are never touched (they are depth-sorted by vanilla).
 */
public final class TerrainFaces {
    private static final int LAYERS = ChunkSectionLayer.values().length;
    private static final ThreadLocal<FaceRanges[]> CURRENT = new ThreadLocal<>();

    private TerrainFaces() {
    }

    public static void begin() {
        CURRENT.set(null);
    }

    public static FaceRanges[] end() {
        FaceRanges[] ranges = CURRENT.get();
        CURRENT.set(null);
        return ranges;
    }

    public static void sort(ChunkSectionLayer layer, MeshData mesh) {
        if (!FerrumState.faceCulling || layer.translucent() || mesh.indexBuffer() != null) {
            return;
        }
        MeshData.DrawState state = mesh.drawState();
        if (state.primitiveTopology() != PrimitiveTopology.QUADS) {
            return;
        }
        VertexFormat format = state.format();
        VertexFormatElement position = format.getElement("Position");
        if (position == null) {
            return;
        }
        ByteBuffer buffer = mesh.vertexBuffer();
        FaceRanges ranges = FaceSorter.sort(buffer, buffer.position(), format.getVertexSize(), position.offset(),
            state.vertexCount());
        if (ranges == null) {
            return;
        }
        FaceRanges[] current = CURRENT.get();
        if (current == null) {
            current = new FaceRanges[LAYERS];
            CURRENT.set(current);
        }
        current[layer.ordinal()] = ranges;
    }
}
