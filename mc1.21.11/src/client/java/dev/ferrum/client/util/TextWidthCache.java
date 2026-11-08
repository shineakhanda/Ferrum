package dev.ferrum.client.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.Font;

public final class TextWidthCache {
    private static final int MAX_CACHE_SIZE = 512;
    private static final int MIN_STRING_LENGTH = 8;
    private static final int MAX_STRING_LENGTH = 192;
    private static final Map<Font, LruCache> CACHES = new WeakHashMap<>();

    private TextWidthCache() {
    }

    public static Integer get(Font font, String text) {
        if (!isCacheable(text)) {
            return null;
        }
        synchronized (CACHES) {
            LruCache cache = CACHES.get(font);
            return cache == null ? null : cache.get(text);
        }
    }

    public static void put(Font font, String text, int width) {
        if (!isCacheable(text)) {
            return;
        }
        synchronized (CACHES) {
            CACHES.computeIfAbsent(font, ignored -> new LruCache()).put(text, width);
        }
    }

    public static void clear() {
        synchronized (CACHES) {
            CACHES.clear();
        }
    }

    private static boolean isCacheable(String text) {
        return text != null && text.length() >= MIN_STRING_LENGTH && text.length() <= MAX_STRING_LENGTH;
    }

    private static final class LruCache extends LinkedHashMap<String, Integer> {
        private LruCache() {
            super(64, 0.75F, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return this.size() > MAX_CACHE_SIZE;
        }
    }
}
