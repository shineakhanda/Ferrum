package dev.ferrum.client.render.terrain;

import java.nio.ByteBuffer;

/**
 * Reorders the quads of a finished opaque section mesh (QUADS topology, sequential indices) into
 * facing groups, in place, and returns the group ranges and plane bounds.
 *
 * <p>Classification uses only geometry, so it works for every block model including modded ones:
 * a quad whose four corners share one coordinate on an axis lies in that axis plane, and the sign of
 * its geometric normal ({@code (v1 - v0) x (v2 - v0)}, counter-clockwise front faces as in vanilla's
 * {@code FaceInfo}) tells which way it faces. Everything else (cross plants, rotated or sloped model
 * elements, degenerate quads) goes to the unassigned group, which is always drawn.
 *
 * <p>Runs on chunk-builder threads; scratch arrays are thread-local.
 */
public final class FaceSorter {
    private static final float PLANE_EPSILON = 1.0E-4F;

    private static final class Scratch {
        byte[] facing = new byte[1024];
        byte[] copy = new byte[65536];
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private FaceSorter() {
    }

    /**
     * @param buffer      vertex data; quads start at absolute index {@code base}
     * @param base        absolute byte index of the first vertex
     * @param stride      vertex size in bytes
     * @param posOffset   byte offset of the float3 position inside a vertex
     * @param vertexCount number of vertices (multiple of 4)
     * @return the ranges, or null if the mesh is empty or not quad-aligned
     */
    public static FaceRanges sort(ByteBuffer buffer, int base, int stride, int posOffset, int vertexCount) {
        if (vertexCount <= 0 || (vertexCount & 3) != 0) {
            return null;
        }
        int quadCount = vertexCount >> 2;
        int quadStride = stride * 4;
        Scratch scratch = SCRATCH.get();
        if (scratch.facing.length < quadCount) {
            scratch.facing = new byte[Math.max(quadCount, scratch.facing.length * 2)];
        }
        byte[] facing = scratch.facing;

        FaceRanges ranges = new FaceRanges();
        int[] counts = new int[FaceRanges.COUNT];
        float[] posMin = ranges.posMin;
        float[] negMax = ranges.negMax;
        posMin[0] = posMin[1] = posMin[2] = Float.POSITIVE_INFINITY;
        negMax[0] = negMax[1] = negMax[2] = Float.NEGATIVE_INFINITY;

        for (int q = 0; q < quadCount; q++) {
            int p = base + q * quadStride + posOffset;
            float x0 = buffer.getFloat(p), y0 = buffer.getFloat(p + 4), z0 = buffer.getFloat(p + 8);
            p += stride;
            float x1 = buffer.getFloat(p), y1 = buffer.getFloat(p + 4), z1 = buffer.getFloat(p + 8);
            p += stride;
            float x2 = buffer.getFloat(p), y2 = buffer.getFloat(p + 4), z2 = buffer.getFloat(p + 8);
            p += stride;
            float x3 = buffer.getFloat(p), y3 = buffer.getFloat(p + 4), z3 = buffer.getFloat(p + 8);

            int group = FaceRanges.UNASSIGNED;
            float plane = 0.0F;
            float ax = x1 - x0, ay = y1 - y0, az = z1 - z0;
            float bx = x2 - x0, by = y2 - y0, bz = z2 - z0;
            float nx = ay * bz - az * by;
            float ny = az * bx - ax * bz;
            float nz = ax * by - ay * bx;
            if (nx == 0.0F && ny == 0.0F && nz == 0.0F) {
                // Degenerate first triangle: use the second one.
                ax = x2 - x0; ay = y2 - y0; az = z2 - z0;
                bx = x3 - x0; by = y3 - y0; bz = z3 - z0;
                nx = ay * bz - az * by;
                ny = az * bx - ax * bz;
                nz = ax * by - ay * bx;
            }

            if (same(x0, x1, x2, x3) && nx != 0.0F) {
                group = nx > 0.0F ? FaceRanges.POS_X : FaceRanges.NEG_X;
                plane = x0;
            } else if (same(y0, y1, y2, y3) && ny != 0.0F) {
                group = ny > 0.0F ? FaceRanges.POS_Y : FaceRanges.NEG_Y;
                plane = y0;
            } else if (same(z0, z1, z2, z3) && nz != 0.0F) {
                group = nz > 0.0F ? FaceRanges.POS_Z : FaceRanges.NEG_Z;
                plane = z0;
            }

            switch (group) {
                case FaceRanges.POS_X -> posMin[0] = Math.min(posMin[0], plane);
                case FaceRanges.POS_Y -> posMin[1] = Math.min(posMin[1], plane);
                case FaceRanges.POS_Z -> posMin[2] = Math.min(posMin[2], plane);
                case FaceRanges.NEG_X -> negMax[0] = Math.max(negMax[0], plane);
                case FaceRanges.NEG_Y -> negMax[1] = Math.max(negMax[1], plane);
                case FaceRanges.NEG_Z -> negMax[2] = Math.max(negMax[2], plane);
                default -> {
                }
            }
            facing[q] = (byte) group;
            counts[group]++;
        }

        int[] start = ranges.start;
        start[0] = 0;
        int nonEmpty = 0;
        for (int g = 0; g < FaceRanges.COUNT; g++) {
            start[g + 1] = start[g] + counts[g];
            if (counts[g] > 0) {
                nonEmpty++;
            }
        }

        if (nonEmpty > 1) {
            int bytes = quadCount * quadStride;
            if (scratch.copy.length < bytes) {
                scratch.copy = new byte[Math.max(bytes, scratch.copy.length * 2)];
            }
            byte[] copy = scratch.copy;
            int[] cursor = new int[FaceRanges.COUNT];
            System.arraycopy(start, 0, cursor, 0, FaceRanges.COUNT);
            // Stable counting sort: quads keep their relative order inside each group.
            for (int q = 0; q < quadCount; q++) {
                int dst = cursor[facing[q]]++;
                buffer.get(base + q * quadStride, copy, dst * quadStride, quadStride);
            }
            buffer.put(base, copy, 0, bytes);
        }
        FaceCullStats.sortedMeshes++;
        return ranges;
    }

    private static boolean same(float a, float b, float c, float d) {
        return Math.abs(a - b) < PLANE_EPSILON && Math.abs(a - c) < PLANE_EPSILON && Math.abs(a - d) < PLANE_EPSILON;
    }
}
