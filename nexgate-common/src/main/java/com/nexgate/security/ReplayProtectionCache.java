package com.nexgate.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for preventing replay attacks on signed plugin messages.
 */
public class ReplayProtectionCache {

    private final Map<String, Long> seenNonces = new ConcurrentHashMap<>();
    private final long maxAgeMillis;
    private long lastCleanup = System.currentTimeMillis();

    public ReplayProtectionCache(long maxAgeMillis) {
        this.maxAgeMillis = maxAgeMillis;
    }

    /**
     * Checks if a nonce is fresh and has not been used before.
     * @param nonce unique message nonce/token
     * @param timestamp timestamp of the message
     * @return true if valid and accepted; false if expired or already seen
     */
    public synchronized boolean checkAndRecord(String nonce, long timestamp) {
        long now = System.currentTimeMillis();
        maybeCleanup(now);

        // Check age (allow up to 5 seconds of future clock skew)
        if (Math.abs(now - timestamp) > maxAgeMillis) {
            return false;
        }

        // Check if already seen
        if (seenNonces.putIfAbsent(nonce, timestamp) != null) {
            return false; // Replay attack detected!
        }

        return true;
    }

    private void maybeCleanup(long now) {
        if (now - lastCleanup > maxAgeMillis) {
            seenNonces.entrySet().removeIf(entry -> Math.abs(now - entry.getValue()) > maxAgeMillis);
            lastCleanup = now;
        }
    }
}
