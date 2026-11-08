package dev.ferrum.client.render.terrain;

/**
 * Per-facing quad ranges of one opaque section mesh, produced by {@link FaceSorter} at build time.
 *
 * <p>Quads are stored grouped by geometric facing in the order
 * {@code -X, -Y, -Z, unassigned, +X, +Y, +Z}. For each axis-aligned group the extreme plane is kept:
 * the smallest plane coordinate of all +X quads, the largest of all -X quads, and so on, in
 * section-local block units. A +X quad at plane {@code p} can only be front-facing when the camera is
 * at {@code x > p}; if the camera is at or below the smallest +X plane, every quad in the group is
 * back-facing and the GPU would cull all of them anyway, so the group is simply not submitted.
 * Terrain pipelines cull back faces, so this never changes the image.
 */
public final class FaceRanges {
    public static final int NEG_X = 0;
    public static final int NEG_Y = 1;
    public static final int NEG_Z = 2;
    public static final int UNASSIGNED = 3;
    public static final int POS_X = 4;
    public static final int POS_Y = 5;
    public static final int POS_Z = 6;
    public static final int COUNT = 7;

    /** Below this the extra draw calls cost more than the vertices they save. */
    private static final int MIN_CULLED_QUADS = 48;
    private static final float EPSILON = 1.0E-3F;

    /** start[i]..start[i+1] is group i, in quads. start[COUNT] is the total. */
    final int[] start = new int[COUNT + 1];
    /** Smallest plane of the +X, +Y, +Z groups. */
    final float[] posMin = new float[3];
    /** Largest plane of the -X, -Y, -Z groups. */
    final float[] negMax = new float[3];

    public int totalQuads() {
        return this.start[COUNT];
    }

    public int quads(int group) {
        return this.start[group + 1] - this.start[group];
    }

    /**
     * Computes the contiguous quad runs that can face a camera at the given section-local position.
     *
     * @param out receives (firstQuad, quadCount) pairs; needs room for 8 ints
     * @return the number of runs, or -1 if culling is not worth it (draw the whole mesh unchanged)
     */
    public int visibleRuns(double camX, double camY, double camZ, int[] out) {
        int total = this.start[COUNT];
        boolean[] visible = VISIBLE.get();
        visible[UNASSIGNED] = true;
        visible[POS_X] = camX > this.posMin[0] - EPSILON;
        visible[POS_Y] = camY > this.posMin[1] - EPSILON;
        visible[POS_Z] = camZ > this.posMin[2] - EPSILON;
        visible[NEG_X] = camX < this.negMax[0] + EPSILON;
        visible[NEG_Y] = camY < this.negMax[1] + EPSILON;
        visible[NEG_Z] = camZ < this.negMax[2] + EPSILON;

        int culled = 0;
        for (int g = 0; g < COUNT; g++) {
            if (!visible[g]) {
                culled += this.quads(g);
            }
        }
        if (culled < MIN_CULLED_QUADS || culled * 8 < total) {
            return -1;
        }

        // Groups are contiguous in memory, so neighbouring visible groups merge into one run;
        // empty groups neither extend nor break a run.
        int runs = 0;
        int runStart = -1;
        int runEnd = -1;
        for (int g = 0; g < COUNT; g++) {
            if (this.quads(g) == 0) {
                continue;
            }
            if (visible[g]) {
                if (runStart < 0) {
                    runStart = this.start[g];
                }
                runEnd = this.start[g + 1];
            } else if (runStart >= 0) {
                out[runs * 2] = runStart;
                out[runs * 2 + 1] = runEnd - runStart;
                runs++;
                runStart = -1;
            }
        }
        if (runStart >= 0) {
            out[runs * 2] = runStart;
            out[runs * 2 + 1] = runEnd - runStart;
            runs++;
        }
        FaceCullStats.culledQuads += culled;
        FaceCullStats.splitSections++;
        return runs;
    }

    private static final ThreadLocal<boolean[]> VISIBLE = ThreadLocal.withInitial(() -> new boolean[COUNT]);
}
