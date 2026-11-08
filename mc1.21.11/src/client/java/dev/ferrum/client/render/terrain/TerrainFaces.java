package dev.ferrum.client.render.terrain;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.ferrum.client.state.FerrumState;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

/**
 * Build-time half of Ferrum's per-face terrain culling (1.21.11): while a section compiles, each
 * finished SOLID / CUTOUT mesh is reordered into facing groups before it is stored in the results
 * and uploaded. Translucent and tripwire meshes are never touched.
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
        if (!FerrumState.faceCulling || (layer != ChunkSectionLayer.SOLID && layer != ChunkSectionLayer.CUTOUT)
            || mesh.indexBuffer() != null) {
            return;
        }
        MeshData.DrawState state = mesh.drawState();
        if (state.mode() != VertexFormat.Mode.QUADS) {
            return;
        }
        VertexFormat format = state.format();
        if (!format.contains(VertexFormatElement.POSITION)) {
            return;
        }
        ByteBuffer buffer = mesh.vertexBuffer();
        FaceRanges ranges = FaceSorter.sort(buffer, buffer.position(), format.getVertexSize(),
            format.getOffset(VertexFormatElement.POSITION), state.vertexCount());
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
