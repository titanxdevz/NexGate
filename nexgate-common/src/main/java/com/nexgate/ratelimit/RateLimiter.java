package com.nexgate.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise rate limiter supporting windowed attempts and temporary security lockouts.
 * Thread-safe implementation with automatic cleanup.
 */
public class RateLimiter {
    private final Map<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, Long> lockouts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final long windowMillis;
    private long lastCleanup = System.currentTimeMillis();

    public RateLimiter(int maxAttempts, long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowMillis = TimeUnit.SECONDS.toMillis(windowSeconds);
    }

    public boolean isLockedOut(String key) {
        if (key == null) return false;
        Long lockUntil = lockouts.get(key);
        if (lockUntil == null) return false;
        if (System.currentTimeMillis() > lockUntil) {
            lockouts.remove(key);
            return false;
        }
        return true;
    }

    public long getRemainingLockTimeSeconds(String key) {
        if (key == null) return 0;
        Long lockUntil = lockouts.get(key);
        if (lockUntil == null) return 0;
        long diff = lockUntil - System.currentTimeMillis();
        return diff > 0 ? (diff / 1000) : 0;
    }

    public void lockout(String key, long durationSeconds) {
        if (key != null) {
            lockouts.put(key, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(durationSeconds));
        }
    }

    public boolean tryAcquire(String key) {
        if (key == null) {
            return false;
        }
        if (isLockedOut(key)) {
            return false;
        }

        long now = System.currentTimeMillis();
        maybeCleanup(now);

        RateLimitBucket bucket = buckets.compute(key, (k, b) -> {
            if (b == null || now - b.firstAttempt > windowMillis) {
                return new RateLimitBucket(1, now);
            }
            return new RateLimitBucket(b.attempts + 1, b.firstAttempt);
        });

        return bucket.attempts <= maxAttempts;
    }

    public void reset(String key) {
        if (key != null) {
            buckets.remove(key);
            lockouts.remove(key);
        }
    }

    public int getAttempts(String key) {
        RateLimitBucket bucket = buckets.get(key);
        return bucket == null ? 0 : bucket.attempts;
    }

    private void maybeCleanup(long now) {
        if (now - lastCleanup > windowMillis * 2) {
            buckets.entrySet().removeIf(entry -> now - entry.getValue().firstAttempt > windowMillis);
            lockouts.entrySet().removeIf(entry -> now > entry.getValue());
            lastCleanup = now;
        }
    }

    private record RateLimitBucket(int attempts, long firstAttempt) {
    }
}
