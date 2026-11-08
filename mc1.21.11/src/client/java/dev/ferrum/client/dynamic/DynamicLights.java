package dev.ferrum.client.dynamic;

import dev.ferrum.client.FerrumClient;
import dev.ferrum.client.compat.ModCompat;
import dev.ferrum.client.config.FerrumConfig;
import dev.ferrum.client.state.FerrumState;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;

public final class DynamicLights {
    private static final int UPDATE_INTERVAL_TICKS = 8;
    private static final int ENTITY_SCAN_INTERVAL_TICKS = 20;
    private static final int MAX_LIGHT_CELLS = 65536;
    private static final int MAX_DIRTY_SECTIONS = 160;
    private static final double ENTITY_SCAN_RANGE = 32.0;
    private static final java.util.Map<Item, Integer> ITEM_LIGHTS = java.util.Map.of(
        Items.LAVA_BUCKET, 15,
        Items.BLAZE_ROD, 7,
        Items.GLOWSTONE_DUST, 7,
        Items.MAGMA_CREAM, 6,
        Items.GLOW_INK_SAC, 5
    );

    private static final Long2IntOpenHashMap DESIRED = new Long2IntOpenHashMap();
    private static final Long2LongOpenHashMap STATES = new Long2LongOpenHashMap();
    private static final Long2ObjectOpenHashMap<DataLayer> LAYERS = new Long2ObjectOpenHashMap<>();
    private static final LongOpenHashSet DIRTY_SECTIONS = new LongOpenHashSet();
    private static final LongArrayList REMOVALS = new LongArrayList();
    private static final List<LightSource> ENTITY_SOURCES = new ArrayList<>();
    private static final List<LightSource> SOURCES = new ArrayList<>();

    private static ClientLevel lastLevel;
    private static long tickCounter;
    private static long lastHash;
    private static boolean lastDisabled;

    private DynamicLights() {
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        FerrumConfig config = FerrumClient.CONFIG;

        if (level == null) {
            if (lastLevel != null) {
                clearState();
                lastLevel = null;
            }
            return;
        }

        if (level != lastLevel) {
            clearState();
            lastLevel = level;
            lastDisabled = false;
            lastHash = 0L;
        }

        boolean shaderActive = config.irisSafeMode && ModCompat.isShaderPackInUse();
        boolean disabled = !config.dynamicLights
            || FerrumState.dynamicLightRadius <= 0
            || ModCompat.OTHER_DYNAMIC_LIGHTS
            || shaderActive;

        if (disabled) {
            if (!lastDisabled) {
                restoreAll(minecraft, level);
            }
            lastDisabled = true;
            FerrumState.dynamicLightSources = 0;
            FerrumState.dynamicLightCells = 0;
            return;
        }
        lastDisabled = false;

        tickCounter++;
        if (tickCounter % UPDATE_INTERVAL_TICKS != 0L) {
            return;
        }

        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        if (tickCounter % ENTITY_SCAN_INTERVAL_TICKS == 0L) {
            scanEntitySources(level, player, config);
        }

        SOURCES.clear();
        addHeldLight(player);
        if (FerrumState.dynamicLightEntities) {
            SOURCES.addAll(ENTITY_SOURCES);
        }
        int maxSources = FerrumState.dynamicLightMaxSources;
        if (SOURCES.size() > maxSources) {
            SOURCES.subList(maxSources, SOURCES.size()).clear();
        }

        long hash = hashSources();
        if (hash == lastHash && tickCounter % 20L != 0L) {
            return;
        }

        buildDesiredField();
        applyField(minecraft, level);
        lastHash = hash;
        FerrumState.dynamicLightSources = SOURCES.size();
        FerrumState.dynamicLightCells = STATES.size();
    }

    public static void restoreNow(Minecraft minecraft) {
        if (minecraft != null && minecraft.level != null) {
            restoreAll(minecraft, minecraft.level);
        }
        clearState();
    }

    public static void onConfigChanged() {
        lastHash = 0L;
        lastDisabled = false;
    }

    private static void addHeldLight(LocalPlayer player) {
        if (!FerrumState.dynamicLightItems) {
            return;
        }
        int level = Math.max(itemLight(player.getMainHandItem()), itemLight(player.getOffhandItem()));
        if (level > 0) {
            BlockPos pos = BlockPos.containing(player.getX(), player.getEyeY() - 0.25, player.getZ());
            SOURCES.add(new LightSource(pos.getX(), pos.getY(), pos.getZ(), level, -1.0));
        }
    }

    private static void scanEntitySources(ClientLevel level, LocalPlayer player, FerrumConfig config) {
        ENTITY_SOURCES.clear();
        if (!FerrumState.dynamicLightEntities || !config.dynamicLights) {
            return;
        }

        double scanRangeSq = ENTITY_SCAN_RANGE * ENTITY_SCAN_RANGE;
        int max = Math.max(4, FerrumState.dynamicLightMaxSources);
        for (Entity entity : level.entitiesForRendering()) {
            if (entity == player) {
                continue;
            }
            double distanceSq = entity.distanceToSqr(player);
            if (distanceSq > scanRangeSq) {
                continue;
            }

            int light = entityLight(entity);
            if (light <= 0) {
                continue;
            }

            BlockPos pos = BlockPos.containing(
                entity.getX(),
                entity.getY() + Math.max(0.1, entity.getBbHeight() * 0.5),
                entity.getZ()
            );
            ENTITY_SOURCES.add(new LightSource(pos.getX(), pos.getY(), pos.getZ(), light, distanceSq));
        }

        if (ENTITY_SOURCES.size() > max) {
            ENTITY_SOURCES.sort(Comparator.comparingDouble(source -> source.distanceSq));
            ENTITY_SOURCES.subList(max, ENTITY_SOURCES.size()).clear();
        }
    }

    private static int entityLight(Entity entity) {
        int light = 0;
        if (entity.getType() == EntityType.BLAZE) {
            light = 7;
        } else if (entity.getType() == EntityType.MAGMA_CUBE) {
            light = 6;
        } else if (entity.getType() == EntityType.GLOW_SQUID) {
            light = 5;
        } else if (entity.getType() == EntityType.TNT) {
            light = 10;
        } else if (entity instanceof ItemEntity itemEntity) {
            light = itemLight(itemEntity.getItem());
        }

        if (entity.isOnFire() && !entity.isInWater()) {
            light = Math.max(light, 6);
        }
        return Math.min(15, light);
    }

    private static int itemLight(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        int light = ITEM_LIGHTS.getOrDefault(stack.getItem(), 0);
        if (stack.getItem() instanceof BlockItem blockItem) {
            light = Math.max(light, blockItem.getBlock().defaultBlockState().getLightEmission());
        }
        return Math.min(15, light);
    }

    private static long hashSources() {
        long hash = 1469598103934665603L;
        hash = mix(hash, FerrumState.dynamicLightRadius);
        hash = mix(hash, FerrumState.dynamicLightMaxSources);
        for (LightSource source : SOURCES) {
            hash = mix(hash, BlockPos.asLong(source.x, source.y, source.z));
            hash = mix(hash, source.level);
        }
        return hash;
    }

    private static long mix(long hash, long value) {
        hash ^= value;
        hash *= 1099511628211L;
        return hash;
    }

    private static void buildDesiredField() {
        DESIRED.clear();
        int maxRadius = Math.min(15, FerrumState.dynamicLightRadius);
        for (LightSource source : SOURCES) {
            int radius = Math.min(maxRadius, source.level);
            if (radius <= 0) {
                continue;
            }
            int radiusSq = radius * radius;
            for (int dy = -radius; dy <= radius; dy++) {
                int y = source.y + dy;
                for (int dz = -radius; dz <= radius; dz++) {
                    int z = source.z + dz;
                    for (int dx = -radius; dx <= radius; dx++) {
                        int distanceSq = dx * dx + dy * dy + dz * dz;
                        if (distanceSq > radiusSq) {
                            continue;
                        }
                        int value = source.level - (int) Math.sqrt(distanceSq);
                        if (value <= 0) {
                            continue;
                        }
                        int x = source.x + dx;
                        long key = BlockPos.asLong(x, y, z);
                        int previous = DESIRED.get(key);
                        if (value > previous) {
                            DESIRED.put(key, Math.min(15, value));
                        }
                        if (DESIRED.size() >= MAX_LIGHT_CELLS) {
                            return;
                        }
                    }
                }
            }
        }
    }

    private static void applyField(Minecraft minecraft, ClientLevel level) {
        LevelLightEngine lightEngine = level.getLightEngine();
        LayerLightEventListener listener = lightEngine.getLayerListener(LightLayer.BLOCK);
        LAYERS.clear();
        DIRTY_SECTIONS.clear();
        REMOVALS.clear();

        LongIterator iterator = STATES.keySet().iterator();
        while (iterator.hasNext()) {
            long key = iterator.nextLong();
            long packed = STATES.get(key);
            int base = (int) (packed & 15L);
            int dynamic = (int) ((packed >> 4) & 15L);

            DataLayer layer = layerFor(listener, key);
            if (layer == null) {
                REMOVALS.add(key);
                continue;
            }

            int x = BlockPos.getX(key);
            int y = BlockPos.getY(key);
            int z = BlockPos.getZ(key);
            int localX = x & 15;
            int localY = y & 15;
            int localZ = z & 15;
            int current = layer.get(localX, localY, localZ);
            int expected = Math.max(base, dynamic);
            if (current != expected) {
                base = current;
                dynamic = 0;
            }

            int desired = Math.max(0, Math.min(15, DESIRED.get(key)));
            if (desired <= base) {
                if (current != base) {
                    layer.set(localX, localY, localZ, base);
                    markDirty(x, y, z);
                }
                REMOVALS.add(key);
            } else {
                if (current != desired) {
                    layer.set(localX, localY, localZ, desired);
                    markDirty(x, y, z);
                }
                STATES.put(key, pack(base, desired));
            }
        }

        for (int i = 0; i < REMOVALS.size(); i++) {
            STATES.remove(REMOVALS.getLong(i));
        }

        for (Long2IntMap.Entry entry : DESIRED.long2IntEntrySet()) {
            long key = entry.getLongKey();
            if (STATES.containsKey(key)) {
                continue;
            }
            DataLayer layer = layerFor(listener, key);
            if (layer == null) {
                continue;
            }

            int x = BlockPos.getX(key);
            int y = BlockPos.getY(key);
            int z = BlockPos.getZ(key);
            int localX = x & 15;
            int localY = y & 15;
            int localZ = z & 15;
            int current = layer.get(localX, localY, localZ);
            int desired = Math.max(0, Math.min(15, entry.getIntValue()));
            if (desired > current) {
                layer.set(localX, localY, localZ, desired);
                STATES.put(key, pack(current, desired));
                markDirty(x, y, z);
            }
        }

        int dirtyCount = 0;
        LongIterator dirtyIterator = DIRTY_SECTIONS.iterator();
        while (dirtyIterator.hasNext() && dirtyCount < MAX_DIRTY_SECTIONS) {
            long section = dirtyIterator.nextLong();
            int sx = SectionPos.x(section);
            int sy = SectionPos.y(section);
            int sz = SectionPos.z(section);
            minecraft.levelRenderer.setSectionRangeDirty(sx, sy, sz, sx, sy, sz);
            dirtyCount++;
        }
    }

    private static DataLayer layerFor(LayerLightEventListener listener, long blockKey) {
        int x = BlockPos.getX(blockKey);
        int y = BlockPos.getY(blockKey);
        int z = BlockPos.getZ(blockKey);
        long section = SectionPos.asLong(x >> 4, y >> 4, z >> 4);
        if (!LAYERS.containsKey(section)) {
            LAYERS.put(section, listener.getDataLayerData(SectionPos.of(x >> 4, y >> 4, z >> 4)));
        }
        return LAYERS.get(section);
    }

    private static void markDirty(int x, int y, int z) {
        int sectionX = x >> 4;
        int sectionY = y >> 4;
        int sectionZ = z >> 4;
        DIRTY_SECTIONS.add(SectionPos.asLong(sectionX, sectionY, sectionZ));

        int localX = x & 15;
        int localY = y & 15;
        int localZ = z & 15;
        if (localX == 0) {
            DIRTY_SECTIONS.add(SectionPos.asLong(sectionX - 1, sectionY, sectionZ));
        } else if (localX == 15) {
            DIRTY_SECTIONS.add(SectionPos.asLong(sectionX + 1, sectionY, sectionZ));
        }
        if (localY == 0) {
            DIRTY_SECTIONS.add(SectionPos.asLong(sectionX, sectionY - 1, sectionZ));
        } else if (localY == 15) {
            DIRTY_SECTIONS.add(SectionPos.asLong(sectionX, sectionY + 1, sectionZ));
        }
        if (localZ == 0) {
            DIRTY_SECTIONS.add(SectionPos.asLong(sectionX, sectionY, sectionZ - 1));
        } else if (localZ == 15) {
            DIRTY_SECTIONS.add(SectionPos.asLong(sectionX, sectionY, sectionZ + 1));
        }
    }

    private static long pack(int base, int dynamic) {
        return (base & 15L) | ((dynamic & 15L) << 4);
    }

    private static void restoreAll(Minecraft minecraft, ClientLevel level) {
        if (STATES.isEmpty()) {
            return;
        }
        DESIRED.clear();
        applyField(minecraft, level);
        STATES.clear();
        DESIRED.clear();
        LAYERS.clear();
        DIRTY_SECTIONS.clear();
        FerrumState.dynamicLightCells = 0;
    }

    private static void clearState() {
        STATES.clear();
        DESIRED.clear();
        LAYERS.clear();
        DIRTY_SECTIONS.clear();
        REMOVALS.clear();
        ENTITY_SOURCES.clear();
        SOURCES.clear();
        FerrumState.dynamicLightSources = 0;
        FerrumState.dynamicLightCells = 0;
        lastHash = 0L;
    }

    private record LightSource(int x, int y, int z, int level, double distanceSq) {
    }
}
