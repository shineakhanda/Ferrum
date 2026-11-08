package dev.ferrum.client.render.terrain;

/** Counters for F3. Approximate: written from builder threads and the render thread without locks. */
public final class FaceCullStats {
    public static volatile long sortedMeshes;
    public static volatile long culledQuads;
    public static volatile long splitSections;
    public static long culledQuadsPerFrame;
    public static long splitSectionsPerFrame;

    private FaceCullStats() {
    }

    /** Called once per frame on the render thread. */
    public static void endFrame() {
        culledQuadsPerFrame = culledQuads;
        splitSectionsPerFrame = splitSections;
        culledQuads = 0;
        splitSections = 0;
    }
}
