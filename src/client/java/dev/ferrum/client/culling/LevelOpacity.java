package dev.ferrum.client.culling;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Reads block opacity from the client level for {@link OcclusionCuller}'s worker thread.
 * Only full opaque cubes ({@code isSolidRender}) block rays; glass, leaves, slabs, fences, water and
 * unloaded chunks let rays through, so culling errs on the side of drawing.
 *
 * <p>Confined to the worker thread: caches the last chunk because consecutive DDA steps almost
 * always stay in the same chunk.
 */
public final class LevelOpacity implements OcclusionCuller.BlockOpacity {
    private final ClientLevel level;
    private final int minY;
    private final int maxY;
    private LevelChunk lastChunk;
    private int lastX = Integer.MIN_VALUE;
    private int lastZ = Integer.MIN_VALUE;

    public LevelOpacity(ClientLevel level) {
        this.level = level;
        this.minY = level.getMinY();
        this.maxY = level.getMaxY();
    }

    @Override
    public boolean isOpaque(int x, int y, int z) {
        if (y < this.minY || y > this.maxY) {
            return false;
        }
        int cx = x >> 4;
        int cz = z >> 4;
        LevelChunk chunk;
        if (cx == this.lastX && cz == this.lastZ) {
            chunk = this.lastChunk;
        } else {
            chunk = this.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
            this.lastChunk = chunk;
            this.lastX = cx;
            this.lastZ = cz;
        }
        if (chunk == null) {
            return false;
        }
        LevelChunkSection[] sections = chunk.getSections();
        int index = this.level.getSectionIndex(y);
        if (index < 0 || index >= sections.length) {
            return false;
        }
        LevelChunkSection section = sections[index];
        if (section == null || section.hasOnlyAir()) {
            return false;
        }
        BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
        return state.isSolidRender();
    }
}
