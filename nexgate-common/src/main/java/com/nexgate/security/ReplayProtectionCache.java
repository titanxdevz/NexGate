package com.nexgate.security;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded in-memory cache for preventing replay attacks on signed inter-server messages.
 * Uses bounded LRU eviction with a maximum capacity to prevent OutOfMemory under flood attacks.
 */
public class ReplayProtectionCache {

    private static final int DEFAULT_MAX_CAPACITY = 20_000;
    private final Map<String, Long> seenNonces;
    private final long maxAgeMillis;
    private final long maxClockSkewMillis = 5000L;
    private long lastCleanup = System.currentTimeMillis();

    public ReplayProtectionCache(long maxAgeMillis) {
        this(maxAgeMillis, DEFAULT_MAX_CAPACITY);
    }

    public ReplayProtectionCache(long maxAgeMillis, int maxCapacity) {
        this.maxAgeMillis = maxAgeMillis;
        this.seenNonces = new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > maxCapacity;
            }
        };
    }

    /**
     * Checks if a nonce is fresh and has not been used before.
     * @param nonce unique message nonce/token
     * @param timestamp timestamp of the message
     * @return true if valid and recorded; false if expired, in future, or replayed
     */
    public synchronized boolean checkAndRecord(String nonce, long timestamp) {
        if (nonce == null || nonce.isEmpty()) {
            return false;
        }

        long now = System.currentTimeMillis();
        maybeCleanup(now);

        // Check past age
        if (now - timestamp > maxAgeMillis) {
            return false;
        }

        // Check future clock skew
        if (timestamp - now > maxClockSkewMillis) {
            return false;
        }

        // Check if already seen
        if (seenNonces.containsKey(nonce)) {
            return false; // Replay attack detected!
        }

        seenNonces.put(nonce, timestamp);
        return true;
    }

    private void maybeCleanup(long now) {
        if (now - lastCleanup > maxAgeMillis) {
            seenNonces.entrySet().removeIf(entry -> now - entry.getValue() > maxAgeMillis);
            lastCleanup = now;
        }
    }

    public synchronized int size() {
        return seenNonces.size();
    }

    public synchronized void clear() {
        seenNonces.clear();
    }
}
