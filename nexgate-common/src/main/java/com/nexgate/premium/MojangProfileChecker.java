package com.nexgate.premium;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Robust service for querying official Mojang session servers to verify account existence.
 *
 * CRITICAL SECURITY PRINCIPLES:
 * 1. Mojang API lookup establishes existence, NOT ownership. Ownership is established ONLY
 *    via Velocity's official encryption handshake with sessionserver.mojang.com.
 * 2. API errors, rate limits, and timeouts MUST NEVER be treated as "account does not exist"
 *    to prevent offline impersonation bypasses during Mojang outages.
 * 3. Cache is strictly bounded with LRU eviction to prevent memory exhaustion attacks.
 */
public class MojangProfileChecker {
    private static final Logger logger = LoggerFactory.getLogger(MojangProfileChecker.class);
    private static final String MOJANG_API_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final int MAX_CACHE_ENTRIES = 10_000;

    private final HttpClient httpClient;
    private final Duration timeout;
    private final long cacheTtlMillis;
    private final long negativeCacheTtlMillis;

    // Thread-safe bounded LRU cache
    private final Map<String, CacheEntry> profileCache;

    public enum LookupStatus {
        EXISTS,
        NOT_FOUND,
        ERROR,
        RATE_LIMITED
    }

    public MojangProfileChecker(Duration timeout, Duration cacheTtl) {
        this(timeout, cacheTtl, Duration.ofMinutes(2), null);
    }

    public MojangProfileChecker(Duration timeout, Duration cacheTtl, Duration negativeCacheTtl, HttpClient customClient) {
        this.timeout = Objects.requireNonNull(timeout, "timeout cannot be null");
        this.cacheTtlMillis = Objects.requireNonNull(cacheTtl, "cacheTtl cannot be null").toMillis();
        this.negativeCacheTtlMillis = Objects.requireNonNull(negativeCacheTtl, "negativeCacheTtl cannot be null").toMillis();
        this.httpClient = customClient != null ? customClient : HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();

        // Bounded LRU cache synchronized map
        this.profileCache = java.util.Collections.synchronizedMap(
                new LinkedHashMap<>(256, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                        return size() > MAX_CACHE_ENTRIES;
                    }
                }
        );
    }

    public static MojangProfileChecker createDefault() {
        return new MojangProfileChecker(Duration.ofSeconds(4), Duration.ofMinutes(30));
    }

    /**
     * Query official Mojang API asynchronously for username existence.
     *
     * @param username Minecraft username
     * @return CompletableFuture resolving to MojangLookupResult
     */
    public CompletableFuture<MojangLookupResult> checkProfileAsync(String username) {
        if (username == null || username.trim().isEmpty()) {
            return CompletableFuture.completedFuture(MojangLookupResult.notFound(username));
        }

        String trimmed = username.trim();
        String lower = trimmed.toLowerCase();

        // Check bounded cache
        CacheEntry cached = profileCache.get(lower);
        long now = System.currentTimeMillis();
        if (cached != null) {
            long ttl = cached.result.status() == LookupStatus.EXISTS ? cacheTtlMillis : negativeCacheTtlMillis;
            if (now - cached.timestamp < ttl) {
                return CompletableFuture.completedFuture(cached.result);
            } else {
                profileCache.remove(lower);
            }
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(MOJANG_API_URL + trimmed))
                    .timeout(timeout)
                    .header("User-Agent", "NexGate-Auth/1.0")
                    .GET()
                    .build();
        } catch (Exception e) {
            logger.warn("Malformed URI for Mojang lookup of {}: {}", trimmed, e.getMessage());
            return CompletableFuture.completedFuture(MojangLookupResult.error(trimmed, "Invalid URI: " + e.getMessage()));
        }

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int status = response.statusCode();
                    MojangLookupResult result;

                    if (status == 200) {
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            String exactName = json.get("name").getAsString();
                            String rawId = json.get("id").getAsString();
                            UUID uuid = parseTrimmedUuid(rawId);
                            result = MojangLookupResult.exists(exactName, uuid);
                        } catch (Exception e) {
                            logger.warn("Failed to parse Mojang response for {}: {}", trimmed, e.getMessage());
                            result = MojangLookupResult.error(trimmed, "JSON parse failure: " + e.getMessage());
                        }
                    } else if (status == 204 || status == 404) {
                        // Official response: Username is definitely not registered with Mojang
                        result = MojangLookupResult.notFound(trimmed);
                    } else if (status == 429) {
                        logger.warn("Mojang API rate limit hit (HTTP 429) checking {}", trimmed);
                        result = MojangLookupResult.rateLimited(trimmed);
                    } else {
                        logger.warn("Mojang API unexpected HTTP status {} for {}", status, trimmed);
                        result = MojangLookupResult.error(trimmed, "HTTP " + status);
                    }

                    // Only cache definitive answers (EXISTS or NOT_FOUND)
                    if (result.status() == LookupStatus.EXISTS || result.status() == LookupStatus.NOT_FOUND) {
                        profileCache.put(lower, new CacheEntry(result, System.currentTimeMillis()));
                    }

                    return result;
                })
                .exceptionally(ex -> {
                    logger.warn("Mojang API network error for {}: {}", trimmed, ex.getMessage());
                    // Crucial: Return ERROR, NEVER notFound!
                    return MojangLookupResult.error(trimmed, ex.getMessage());
                });
    }

    public void invalidate(String username) {
        if (username != null) {
            profileCache.remove(username.trim().toLowerCase());
        }
    }

    public void clearCache() {
        profileCache.clear();
    }

    public int getCacheSize() {
        return profileCache.size();
    }

    public static UUID parseTrimmedUuid(String trimmed) {
        if (trimmed == null || trimmed.length() != 32) {
            throw new IllegalArgumentException("Invalid UUID string: " + trimmed);
        }
        String formatted = trimmed.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5"
        );
        return UUID.fromString(formatted);
    }

    public record MojangLookupResult(LookupStatus status, String exactName, UUID uuid, String errorDetail) {
        public boolean exists() {
            return status == LookupStatus.EXISTS;
        }

        public boolean isNotFound() {
            return status == LookupStatus.NOT_FOUND;
        }

        public boolean isError() {
            return status == LookupStatus.ERROR || status == LookupStatus.RATE_LIMITED;
        }

        public static MojangLookupResult exists(String exactName, UUID uuid) {
            return new MojangLookupResult(LookupStatus.EXISTS, exactName, uuid, null);
        }

        public static MojangLookupResult notFound(String name) {
            return new MojangLookupResult(LookupStatus.NOT_FOUND, name, null, null);
        }

        public static MojangLookupResult error(String name, String detail) {
            return new MojangLookupResult(LookupStatus.ERROR, name, null, detail);
        }

        public static MojangLookupResult rateLimited(String name) {
            return new MojangLookupResult(LookupStatus.RATE_LIMITED, name, null, "Rate limit exceeded (HTTP 429)");
        }
    }

    private static class CacheEntry {
        final MojangLookupResult result;
        final long timestamp;

        CacheEntry(MojangLookupResult result, long timestamp) {
            this.result = result;
            this.timestamp = timestamp;
        }
    }
}
