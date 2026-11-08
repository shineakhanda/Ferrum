package dev.ferrum.client.culling;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;

/**
 * Asynchronous ray-traced occlusion culling for entities and block entities.
 *
 * <p>Vanilla only frustum-culls entities and block entities: every mob, item, chest, sign, banner,
 * skull or shulker box inside the view cone is extracted, animated and submitted even when it is
 * behind a mountain, underground or inside the next room. In bases, villages and caves that is most
 * of them. This class answers "can the camera see any part of this box?" by tracing rays through the
 * loaded block grid on a background thread, so the render thread only does a hash lookup.
 *
 * <p>How it works:
 * <ol>
 *   <li>The render thread calls {@link #isEntityHidden} / {@link #isBlockHidden} for every candidate
 *       that already passed vanilla's frustum test. That registers (or refreshes) a target with its
 *       current bounding box and returns the last result from the worker.</li>
 *   <li>The worker thread loops over all targets requested in the last two seconds. For each it casts
 *       rays from the camera to the box centre and its eight (slightly inset) corners with a 3D DDA
 *       voxel walk. The box is visible as soon as one ray reaches it without passing through a block
 *       that is a full opaque cube. Block lookups go through a per-pass cache because rays to the same
 *       box share most voxels.</li>
 *   <li>A hidden result is only trusted while the camera and the target are within
 *       {@link #MAX_DRIFT} blocks of where they were when it was computed; otherwise the target is
 *       drawn until the next pass (about 20 ms later). This keeps pop-in to a frame or two.</li>
 * </ol>
 *
 * <p>Nothing here touches Minecraft classes; each version supplies a {@link BlockOpacity} that reads
 * its client level. Reads from the worker thread are racy by design (like other async culling
 * mods): a block changing mid-pass can at worst produce one wrong result that the next pass fixes.
 */
public final class OcclusionCuller {
    /** Version adapter: true if the block at the position is a full, opaque cube. */
    public interface BlockOpacity {
        boolean isOpaque(int x, int y, int z);
    }

    private static final long REQUEST_TIMEOUT_NANOS = 2_000_000_000L;
    private static final long MIN_PASS_NANOS = 15_000_000L;
    private static final double MAX_DRIFT = 1.25;
    private static final double ALWAYS_VISIBLE_DISTANCE = 4.0;
    private static final double MAX_TRACE_DISTANCE = 160.0;
    private static final double INSET = 0.05;

    private static final class Target {
        volatile double minX, minY, minZ, maxX, maxY, maxZ;
        volatile long lastRequest;
        // Result, written by the worker: the camera position and box corner it was computed for.
        volatile double resCamX, resCamY, resCamZ, resMinX, resMinY, resMinZ;
        volatile boolean hidden;
    }

    private static final Map<Integer, Target> ENTITIES = new ConcurrentHashMap<>();
    private static final Map<Long, Target> BLOCKS = new ConcurrentHashMap<>();

    private static volatile double camX, camY, camZ;
    private static volatile BlockOpacity opacity;
    private static volatile boolean enabled;
    private static volatile Thread worker;

    // Stats for F3 / HUD
    public static volatile int lastHidden;
    public static volatile int lastTargets;
    public static volatile long lastPassMicros;

    private OcclusionCuller() {
    }

    // ------------------------------------------------------------------ render-thread API

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            ENTITIES.clear();
            BLOCKS.clear();
            lastHidden = 0;
            lastTargets = 0;
        } else {
            ensureWorker();
        }
    }

    public static boolean enabled() {
        return enabled;
    }

    /** Called when the client level changes; drops all results computed against the old world. */
    public static void setOpacity(BlockOpacity value) {
        opacity = value;
        ENTITIES.clear();
        BLOCKS.clear();
    }

    public static void setCamera(double x, double y, double z) {
        camX = x;
        camY = y;
        camZ = z;
    }

    public static boolean isEntityHidden(int id, double minX, double minY, double minZ,
                                         double maxX, double maxY, double maxZ) {
        if (!enabled || opacity == null) {
            return false;
        }
        Target t = ENTITIES.get(id);
        if (t == null) {
            t = new Target();
            t.hidden = false;
            ENTITIES.put(id, t);
        }
        return request(t, minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static boolean isBlockHidden(long key, int x, int y, int z) {
        if (!enabled || opacity == null) {
            return false;
        }
        Target t = BLOCKS.get(key);
        if (t == null) {
            t = new Target();
            BLOCKS.put(key, t);
        }
        return request(t, x, y, z, x + 1.0, y + 1.0, z + 1.0);
    }

    private static boolean request(Target t, double minX, double minY, double minZ,
                                   double maxX, double maxY, double maxZ) {
        t.minX = minX;
        t.minY = minY;
        t.minZ = minZ;
        t.maxX = maxX;
        t.maxY = maxY;
        t.maxZ = maxZ;
        t.lastRequest = System.nanoTime();
        if (!t.hidden) {
            return false;
        }
        double cx = camX, cy = camY, cz = camZ;
        return Math.abs(t.resCamX - cx) < MAX_DRIFT && Math.abs(t.resCamY - cy) < MAX_DRIFT
            && Math.abs(t.resCamZ - cz) < MAX_DRIFT
            && Math.abs(t.resMinX - minX) < MAX_DRIFT && Math.abs(t.resMinY - minY) < MAX_DRIFT
            && Math.abs(t.resMinZ - minZ) < MAX_DRIFT;
    }

    // ------------------------------------------------------------------ worker

    private static synchronized void ensureWorker() {
        if (worker != null && worker.isAlive()) {
            return;
        }
        Thread thread = new Thread(OcclusionCuller::run, "Ferrum Occlusion Culler");
        thread.setDaemon(true);
        // Below the render thread so it never competes with it on 4-core phones.
        thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 2));
        worker = thread;
        thread.start();
    }

    private static void run() {
        Tracer tracer = new Tracer();
        while (true) {
            long start = System.nanoTime();
            BlockOpacity view = opacity;
            if (!enabled || view == null) {
                LockSupport.parkNanos(100_000_000L);
                continue;
            }
            try {
                tracer.begin(view, camX, camY, camZ);
                int hidden = pass(ENTITIES, tracer, start) + pass(BLOCKS, tracer, start);
                lastHidden = hidden;
                lastTargets = ENTITIES.size() + BLOCKS.size();
            } catch (Throwable ignored) {
                // Racy world reads; the next pass recomputes everything.
            }
            long took = System.nanoTime() - start;
            lastPassMicros = took / 1000L;
            long wait = Math.max(MIN_PASS_NANOS - took, 2_000_000L);
            LockSupport.parkNanos(wait);
        }
    }

    private static <K> int pass(Map<K, Target> targets, Tracer tracer, long now) {
        int hidden = 0;
        for (Iterator<Target> it = targets.values().iterator(); it.hasNext(); ) {
            Target t = it.next();
            if (now - t.lastRequest > REQUEST_TIMEOUT_NANOS) {
                it.remove();
                continue;
            }
            double minX = t.minX, minY = t.minY, minZ = t.minZ, maxX = t.maxX, maxY = t.maxY, maxZ = t.maxZ;
            boolean visible = tracer.boxVisible(minX, minY, minZ, maxX, maxY, maxZ);
            t.resCamX = tracer.ox;
            t.resCamY = tracer.oy;
            t.resCamZ = tracer.oz;
            t.resMinX = minX;
            t.resMinY = minY;
            t.resMinZ = minZ;
            t.hidden = !visible; // written last: readers see a complete result
            if (!visible) {
                hidden++;
            }
        }
        return hidden;
    }

    /** Worker-thread-confined ray tracer with a per-pass opacity cache. */
    private static final class Tracer {
        private static final int CACHE_BITS = 15;
        private static final int CACHE_MASK = (1 << CACHE_BITS) - 1;
        private final long[] keys = new long[1 << CACHE_BITS];
        private final int[] stamps = new int[1 << CACHE_BITS];
        private final boolean[] values = new boolean[1 << CACHE_BITS];
        private int stamp;

        private BlockOpacity view;
        double ox, oy, oz;
        private int camBlockX, camBlockY, camBlockZ;
        private boolean cameraInOpaque;

        void begin(BlockOpacity view, double x, double y, double z) {
            this.view = view;
            this.ox = x;
            this.oy = y;
            this.oz = z;
            this.camBlockX = floor(x);
            this.camBlockY = floor(y);
            this.camBlockZ = floor(z);
            if (++this.stamp == 0) {
                java.util.Arrays.fill(this.stamps, 0);
                this.stamp = 1;
            }
            // Spectators inside blocks: never cull, every ray would start blocked.
            this.cameraInOpaque = this.opaque(this.camBlockX, this.camBlockY, this.camBlockZ);
        }

        boolean boxVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            if (this.cameraInOpaque) {
                return true;
            }
            double cx = (minX + maxX) * 0.5, cy = (minY + maxY) * 0.5, cz = (minZ + maxZ) * 0.5;
            double dx = cx - this.ox, dy = cy - this.oy, dz = cz - this.oz;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < ALWAYS_VISIBLE_DISTANCE * ALWAYS_VISIBLE_DISTANCE
                || distSq > MAX_TRACE_DISTANCE * MAX_TRACE_DISTANCE) {
                return true;
            }
            if (this.ox >= minX && this.ox <= maxX && this.oy >= minY && this.oy <= maxY
                && this.oz >= minZ && this.oz <= maxZ) {
                return true;
            }
            // Grid cells the box occupies count as "arrived": a ray that enters them saw the target.
            int bx0 = floor(minX), by0 = floor(minY), bz0 = floor(minZ);
            int bx1 = floor(maxX - 1.0E-7), by1 = floor(maxY - 1.0E-7), bz1 = floor(maxZ - 1.0E-7);

            if (this.ray(cx, cy, cz, bx0, by0, bz0, bx1, by1, bz1)) {
                return true;
            }
            double ix0 = minX + INSET, iy0 = minY + INSET, iz0 = minZ + INSET;
            double ix1 = maxX - INSET, iy1 = maxY - INSET, iz1 = maxZ - INSET;
            // Corners nearest the camera first: they are the most likely to be visible.
            double nearX = this.ox < cx ? ix0 : ix1, farX = this.ox < cx ? ix1 : ix0;
            double nearY = this.oy < cy ? iy0 : iy1, farY = this.oy < cy ? iy1 : iy0;
            double nearZ = this.oz < cz ? iz0 : iz1, farZ = this.oz < cz ? iz1 : iz0;
            return this.ray(nearX, nearY, nearZ, bx0, by0, bz0, bx1, by1, bz1)
                || this.ray(farX, nearY, nearZ, bx0, by0, bz0, bx1, by1, bz1)
                || this.ray(nearX, farY, nearZ, bx0, by0, bz0, bx1, by1, bz1)
                || this.ray(nearX, nearY, farZ, bx0, by0, bz0, bx1, by1, bz1)
                || this.ray(farX, farY, nearZ, bx0, by0, bz0, bx1, by1, bz1)
                || this.ray(farX, nearY, farZ, bx0, by0, bz0, bx1, by1, bz1)
                || this.ray(nearX, farY, farZ, bx0, by0, bz0, bx1, by1, bz1)
                || this.ray(farX, farY, farZ, bx0, by0, bz0, bx1, by1, bz1);
        }

        /** 3D DDA (Amanatides-Woo) from the camera towards (tx,ty,tz). True if it reaches the target cells. */
        private boolean ray(double tx, double ty, double tz,
                            int bx0, int by0, int bz0, int bx1, int by1, int bz1) {
            double dx = tx - this.ox, dy = ty - this.oy, dz = tz - this.oz;
            int x = this.camBlockX, y = this.camBlockY, z = this.camBlockZ;
            int endX = floor(tx), endY = floor(ty), endZ = floor(tz);

            int stepX = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
            int stepY = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
            int stepZ = dz > 0 ? 1 : (dz < 0 ? -1 : 0);
            double tDeltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
            double tDeltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
            double tDeltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
            double tMaxX = stepX == 0 ? Double.MAX_VALUE
                : (stepX > 0 ? (x + 1 - this.ox) : (this.ox - x)) * tDeltaX;
            double tMaxY = stepY == 0 ? Double.MAX_VALUE
                : (stepY > 0 ? (y + 1 - this.oy) : (this.oy - y)) * tDeltaY;
            double tMaxZ = stepZ == 0 ? Double.MAX_VALUE
                : (stepZ > 0 ? (z + 1 - this.oz) : (this.oz - z)) * tDeltaZ;

            int maxSteps = Math.abs(endX - x) + Math.abs(endY - y) + Math.abs(endZ - z) + 1;
            for (int i = 0; i < maxSteps; i++) {
                if (tMaxX < tMaxY) {
                    if (tMaxX < tMaxZ) {
                        x += stepX;
                        tMaxX += tDeltaX;
                    } else {
                        z += stepZ;
                        tMaxZ += tDeltaZ;
                    }
                } else if (tMaxY < tMaxZ) {
                    y += stepY;
                    tMaxY += tDeltaY;
                } else {
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }
                if (x >= bx0 && x <= bx1 && y >= by0 && y <= by1 && z >= bz0 && z <= bz1) {
                    return true;
                }
                if (this.opaque(x, y, z)) {
                    return false;
                }
            }
            return true;
        }

        private boolean opaque(int x, int y, int z) {
            long key = ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
            int slot = (int) (mix(key) & CACHE_MASK);
            if (this.stamps[slot] == this.stamp && this.keys[slot] == key) {
                return this.values[slot];
            }
            boolean value = this.view.isOpaque(x, y, z);
            this.stamps[slot] = this.stamp;
            this.keys[slot] = key;
            this.values[slot] = value;
            return value;
        }

        private static long mix(long k) {
            k ^= k >>> 33;
            k *= 0xff51afd7ed558ccdL;
            k ^= k >>> 33;
            return k;
        }
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
