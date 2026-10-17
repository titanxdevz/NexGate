package com.nexgate.ratelimit;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise rate limiter supporting windowed attempts, temporary security lockouts,
 * bounded LRU capacity caps to prevent DoS memory exhaustion, and IPv6 /64 prefix aggregation.
 */
public class RateLimiter {
    private static final int DEFAULT_MAX_CAPACITY = 25_000;

    private final Map<String, RateLimitBucket> buckets;
    private final Map<String, Long> lockouts;
    private final int maxAttempts;
    private final long windowMillis;
    private final int maxCapacity;
    private long lastCleanup = System.currentTimeMillis();

    public RateLimiter(int maxAttempts, long windowSeconds) {
        this(maxAttempts, windowSeconds, DEFAULT_MAX_CAPACITY);
    }

    public RateLimiter(int maxAttempts, long windowSeconds, int maxCapacity) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.windowMillis = TimeUnit.SECONDS.toMillis(Math.max(1, windowSeconds));
        this.maxCapacity = Math.max(100, maxCapacity);

        this.buckets = new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, RateLimitBucket> eldest) {
                return size() > RateLimiter.this.maxCapacity;
            }
        };

        this.lockouts = new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > RateLimiter.this.maxCapacity;
            }
        };
    }

    /**
     * Normalizes an IP address key, aggregating IPv6 addresses to their /64 subnet prefix.
     */
    public static String normalizeIpKey(String ip) {
        if (ip == null || ip.isEmpty()) return "unknown";
        String cleanIp = ip.trim();
        // Remove leading slash or trailing port if present
        if (cleanIp.startsWith("/")) {
            cleanIp = cleanIp.substring(1);
        }
        int colonPort = cleanIp.lastIndexOf(':');
        int dotIdx = cleanIp.indexOf('.');
        if (dotIdx != -1 && colonPort != -1) {
            // IPv4 with port like 1.2.3.4:5678
            cleanIp = cleanIp.substring(0, colonPort);
        }

        try {
            InetAddress addr = InetAddress.getByName(cleanIp);
            if (addr instanceof Inet6Address) {
                byte[] bytes = addr.getAddress();
                // Mask to /64 (first 8 bytes)
                return String.format("%02x%02x:%02x%02x:%02x%02x:%02x%02x::/64",
                        bytes[0], bytes[1], bytes[2], bytes[3],
                        bytes[4], bytes[5], bytes[6], bytes[7]);
            }
            return addr.getHostAddress();
        } catch (UnknownHostException e) {
            return cleanIp;
        }
    }

    public synchronized boolean isLockedOut(String key) {
        if (key == null) return false;
        Long lockUntil = lockouts.get(key);
        if (lockUntil == null) return false;
        if (System.currentTimeMillis() > lockUntil) {
            lockouts.remove(key);
            return false;
        }
        return true;
    }

    public synchronized long getRemainingLockTimeSeconds(String key) {
        if (key == null) return 0;
        Long lockUntil = lockouts.get(key);
        if (lockUntil == null) return 0;
        long diff = lockUntil - System.currentTimeMillis();
        return diff > 0 ? (diff / 1000) : 0;
    }

    public synchronized void lockout(String key, long durationSeconds) {
        if (key != null) {
            lockouts.put(key, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(durationSeconds));
        }
    }

    public synchronized boolean tryAcquire(String key) {
        if (key == null) {
            return false;
        }
        if (isLockedOut(key)) {
            return false;
        }

        long now = System.currentTimeMillis();
        maybeCleanup(now);

        RateLimitBucket bucket = buckets.get(key);
        if (bucket == null || now - bucket.firstAttempt > windowMillis) {
            buckets.put(key, new RateLimitBucket(1, now));
            return true;
        }

        int newAttempts = bucket.attempts + 1;
        buckets.put(key, new RateLimitBucket(newAttempts, bucket.firstAttempt));
        return newAttempts <= maxAttempts;
    }

    public synchronized void reset(String key) {
        if (key != null) {
            buckets.remove(key);
            lockouts.remove(key);
        }
    }

    public synchronized void resetAttempts(String key) {
        if (key != null) {
            buckets.remove(key);
        }
    }

    public synchronized int getAttempts(String key) {
        if (key == null) return 0;
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

    public synchronized int size() {
        return buckets.size();
    }

    public synchronized void clear() {
        buckets.clear();
        lockouts.clear();
    }

    private static class RateLimitBucket {
        final int attempts;
        final long firstAttempt;

        RateLimitBucket(int attempts, long firstAttempt) {
            this.attempts = attempts;
            this.firstAttempt = firstAttempt;
        }
    }
}
