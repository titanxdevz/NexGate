package com.nexgate.velocity.antibot;

import com.nexgate.ratelimit.RateLimiter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enterprise Sonar-class AntiBot and connection-flood defense manager.
 * Operates prior to authentication to protect proxy and backend server resources.
 */
public class AntiBotManager {
    private static final Logger logger = LoggerFactory.getLogger(AntiBotManager.class);
    private static final int MAX_TRACKED_ENTRIES = 25_000;

    public enum Mode {
        AUTO,
        ON,
        OFF
    }

    public record AdmissionResult(boolean allowed, String rejectionReason, Component component) {
        public static AdmissionResult allow() {
            return new AdmissionResult(true, null, null);
        }

        public static AdmissionResult deny(String reason) {
            return new AdmissionResult(false, reason, Component.text(reason).color(NamedTextColor.RED));
        }
    }

    public static class Metrics {
        public final long evaluated;
        public final long rejected;
        public final long rateLimited;
        public final long fastReconnects;
        public final long blacklisted;
        public final boolean mitigationActive;

        public Metrics(long evaluated, long rejected, long rateLimited, long fastReconnects, long blacklisted, boolean mitigationActive) {
            this.evaluated = evaluated;
            this.rejected = rejected;
            this.rateLimited = rateLimited;
            this.fastReconnects = fastReconnects;
            this.blacklisted = blacklisted;
            this.mitigationActive = mitigationActive;
        }
    }

    private volatile boolean enabled = true;
    private volatile Mode mode = Mode.AUTO;
    private volatile int globalRateLimit = 40;          // connections per second
    private volatile int perIpRateLimit = 4;             // connections per second per IP
    private volatile int maxConcurrentPerIp = 3;         // simultaneous connections
    private volatile long fastReconnectThresholdMs = 1200L; // fast disconnect/reconnect burst
    private volatile int mitigationThreshold = 15;       // triggers under-attack if conns/sec > 15
    private volatile long mitigationDurationMs = 60_000L; // 60s under-attack duration

    private volatile long mitigationUntil = 0L;

    // Rate limiters
    private final RateLimiter globalLimiter;
    private final RateLimiter perIpLimiter;
    private final RateLimiter subnet64Limiter;

    // CIDR rules
    private final Set<CidrMatcher> blacklistedCidrs = ConcurrentHashMap.newKeySet();
    private final Set<CidrMatcher> whitelistedCidrs = ConcurrentHashMap.newKeySet();

    // Bounded temporary bans (IP/Prefix -> expire timestamp)
    private final Map<String, Long> temporaryBans;

    // Fast reconnect tracking (IP -> last disconnect epoch)
    private final Map<String, Long> lastDisconnectMap;

    // Active connection tracking (IP -> concurrent count)
    private final Map<String, AtomicInteger> activeConnections;

    // Traffic sliding window for AUTO mitigation detection
    private final AtomicInteger currentSecondCount = new AtomicInteger(0);
    private volatile long currentSecondWindow = System.currentTimeMillis() / 1000L;

    // Statistics
    private final AtomicLong statsEvaluated = new AtomicLong(0);
    private final AtomicLong statsRejected = new AtomicLong(0);
    private final AtomicLong statsRateLimited = new AtomicLong(0);
    private final AtomicLong statsFastReconnects = new AtomicLong(0);
    private final AtomicLong statsBlacklisted = new AtomicLong(0);

    public AntiBotManager() {
        this.globalLimiter = new RateLimiter(globalRateLimit, 1, 100);
        this.perIpLimiter = new RateLimiter(perIpRateLimit, 1, MAX_TRACKED_ENTRIES);
        this.subnet64Limiter = new RateLimiter(perIpRateLimit * 2, 1, MAX_TRACKED_ENTRIES);

        this.temporaryBans = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > MAX_TRACKED_ENTRIES;
            }
        });

        this.lastDisconnectMap = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > MAX_TRACKED_ENTRIES;
            }
        });

        this.activeConnections = new ConcurrentHashMap<>();
    }

    /**
     * Evaluates a pre-login connection against all anti-bot rules.
     */
    public AdmissionResult evaluateConnection(String ip, String username, int protocolVersion, String virtualHost) {
        if (!enabled) {
            return AdmissionResult.allow();
        }

        statsEvaluated.incrementAndGet();
        long now = System.currentTimeMillis();
        String normalizedKey = RateLimiter.normalizeIpKey(ip);

        // 1. Whitelist exemption (Trusted proxies, staff, monitoring)
        if (isWhitelisted(ip)) {
            return AdmissionResult.allow();
        }

        // 2. Permanent and temporary blacklist checks
        if (isBlacklisted(ip)) {
            statsBlacklisted.incrementAndGet();
            statsRejected.incrementAndGet();
            return AdmissionResult.deny("Your IP address or network range is blocked by network security.");
        }

        Long banExpiry = temporaryBans.get(normalizedKey);
        if (banExpiry != null) {
            if (now < banExpiry) {
                statsBlacklisted.incrementAndGet();
                statsRejected.incrementAndGet();
                long remainingSec = Math.max(1, (banExpiry - now) / 1000L);
                return AdmissionResult.deny("Your network is temporarily throttled. Retry in " + remainingSec + "s.");
            } else {
                temporaryBans.remove(normalizedKey);
            }
        }

        // 3. Fast reconnect / bot loop detection
        Long lastDisc = lastDisconnectMap.get(normalizedKey);
        if (lastDisc != null && (now - lastDisc) < fastReconnectThresholdMs) {
            statsFastReconnects.incrementAndGet();
            statsRejected.incrementAndGet();
            // Apply a short 10-second penalty to stop tight connection loops
            temporaryBans.put(normalizedKey, now + 10_000L);
            return AdmissionResult.deny("Connection burst detected. Please wait a moment before reconnecting.");
        }

        // 4. Update sliding window & check for under-attack mitigation trigger
        long currentSec = now / 1000L;
        if (currentSec != currentSecondWindow) {
            currentSecondWindow = currentSec;
            currentSecondCount.set(1);
        } else {
            int countThisSec = currentSecondCount.incrementAndGet();
            if (mode == Mode.AUTO && countThisSec > mitigationThreshold && !isMitigationActive()) {
                activateMitigation("High connection surge: " + countThisSec + " conns/sec");
            }
        }

        boolean underAttack = isMitigationActive();

        // 5. Global rate limit
        int effectiveGlobalLimit = underAttack ? Math.max(10, globalRateLimit / 2) : globalRateLimit;
        if (!globalLimiter.tryAcquire("global")) {
            statsRateLimited.incrementAndGet();
            statsRejected.incrementAndGet();
            return AdmissionResult.deny("Server connection gateway is currently congested. Please retry in a few seconds.");
        }

        // 6. Per-IP and per-IPv6 /64 rate limit
        int effectiveIpLimit = underAttack ? 1 : perIpRateLimit;
        if (!perIpLimiter.tryAcquire(normalizedKey) || !subnet64Limiter.tryAcquire(normalizedKey)) {
            statsRateLimited.incrementAndGet();
            statsRejected.incrementAndGet();
            // Temporarily rate-limit IP for 15s
            temporaryBans.put(normalizedKey, now + 15_000L);
            return AdmissionResult.deny("Too many connections from your network. Please slow down.");
        }

        // 7. Concurrent connections per IP
        AtomicInteger activeCount = activeConnections.computeIfAbsent(normalizedKey, k -> new AtomicInteger(0));
        if (activeCount.get() >= maxConcurrentPerIp) {
            statsRejected.incrementAndGet();
            return AdmissionResult.deny("Maximum simultaneous connections from your IP reached (" + maxConcurrentPerIp + ").");
        }

        return AdmissionResult.allow();
    }

    /**
     * Records a successful client connection establishing active session.
     */
    public void onPlayerConnected(String ip) {
        String normalizedKey = RateLimiter.normalizeIpKey(ip);
        activeConnections.computeIfAbsent(normalizedKey, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * Records player disconnect, decrementing active count and tracking disconnect timestamp.
     */
    public void onPlayerDisconnected(String ip) {
        String normalizedKey = RateLimiter.normalizeIpKey(ip);
        AtomicInteger count = activeConnections.get(normalizedKey);
        if (count != null) {
            int remaining = count.decrementAndGet();
            if (remaining <= 0) {
                activeConnections.remove(normalizedKey);
            }
        }
        lastDisconnectMap.put(normalizedKey, System.currentTimeMillis());
    }

    public boolean isMitigationActive() {
        if (mode == Mode.ON) return true;
        if (mode == Mode.OFF) return false;
        return System.currentTimeMillis() < mitigationUntil;
    }

    public void activateMitigation(String triggerReason) {
        this.mitigationUntil = System.currentTimeMillis() + mitigationDurationMs;
        logger.warn("⚡ NexGate AntiBot [UNDER-ATTACK MITIGATION ACTIVATED] - {}", triggerReason);
    }

    public void deactivateMitigation() {
        this.mitigationUntil = 0L;
        logger.info("NexGate AntiBot Under-Attack Mitigation deactivated.");
    }

    public boolean isBlacklisted(String ip) {
        for (CidrMatcher matcher : blacklistedCidrs) {
            if (matcher.matches(ip)) {
                return true;
            }
        }
        return false;
    }

    public boolean isWhitelisted(String ip) {
        for (CidrMatcher matcher : whitelistedCidrs) {
            if (matcher.matches(ip)) {
                return true;
            }
        }
        return false;
    }

    public void addBlacklistCidr(String cidr) {
        blacklistedCidrs.add(new CidrMatcher(cidr));
    }

    public boolean removeBlacklistCidr(String cidr) {
        return blacklistedCidrs.removeIf(m -> m.toString().equalsIgnoreCase(cidr) || m.getNetworkAddress().equalsIgnoreCase(cidr));
    }

    public void addWhitelistCidr(String cidr) {
        whitelistedCidrs.add(new CidrMatcher(cidr));
    }

    public boolean removeWhitelistCidr(String cidr) {
        return whitelistedCidrs.removeIf(m -> m.toString().equalsIgnoreCase(cidr) || m.getNetworkAddress().equalsIgnoreCase(cidr));
    }

    public void addTemporaryBan(String ip, long durationSeconds) {
        String key = RateLimiter.normalizeIpKey(ip);
        temporaryBans.put(key, System.currentTimeMillis() + (durationSeconds * 1000L));
    }

    public boolean removeTemporaryBan(String ip) {
        String key = RateLimiter.normalizeIpKey(ip);
        return temporaryBans.remove(key) != null;
    }

    public Metrics getMetrics() {
        return new Metrics(
                statsEvaluated.get(),
                statsRejected.get(),
                statsRateLimited.get(),
                statsFastReconnects.get(),
                statsBlacklisted.get(),
                isMitigationActive()
        );
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = Objects.requireNonNull(mode);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setGlobalRateLimit(int limit) {
        this.globalRateLimit = limit;
    }

    public void setPerIpRateLimit(int limit) {
        this.perIpRateLimit = limit;
    }

    public void setMaxConcurrentPerIp(int max) {
        this.maxConcurrentPerIp = max;
    }

    public void setFastReconnectThresholdMs(long ms) {
        this.fastReconnectThresholdMs = ms;
    }
}
