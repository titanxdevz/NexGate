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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for querying official Mojang session servers to detect premium accounts.
 */
public class MojangProfileChecker {
    private static final Logger logger = LoggerFactory.getLogger(MojangProfileChecker.class);
    private static final String MOJANG_API_URL = "https://api.mojang.com/users/profiles/minecraft/";

    private final HttpClient httpClient;
    private final Duration timeout;
    private final Map<String, CacheEntry> profileCache = new ConcurrentHashMap<>();
    private final long cacheTtlMillis;

    public MojangProfileChecker(Duration timeout, Duration cacheTtl) {
        this.timeout = timeout;
        this.cacheTtlMillis = cacheTtl.toMillis();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    public static MojangProfileChecker createDefault() {
        return new MojangProfileChecker(Duration.ofSeconds(3), Duration.ofMinutes(15));
    }

    public CompletableFuture<MojangProfile> checkProfileAsync(String username) {
        if (username == null || username.isEmpty()) {
            return CompletableFuture.completedFuture(MojangProfile.nonExistent(username));
        }

        String lower = username.toLowerCase();
        CacheEntry cached = profileCache.get(lower);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < cacheTtlMillis) {
            return CompletableFuture.completedFuture(cached.profile);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MOJANG_API_URL + username))
                .timeout(timeout)
                .header("User-Agent", "NexGate-Auth/1.0")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    MojangProfile profile;
                    int status = response.statusCode();
                    if (status == 200) {
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            String exactName = json.get("name").getAsString();
                            String rawId = json.get("id").getAsString();
                            UUID uuid = parseTrimmedUuid(rawId);
                            profile = new MojangProfile(true, exactName, uuid);
                        } catch (Exception e) {
                            logger.warn("Failed to parse Mojang response for {}: {}", username, e.getMessage());
                            profile = MojangProfile.nonExistent(username);
                        }
                    } else {
                        // 204 or 404 means username doesn't exist on Mojang
                        profile = MojangProfile.nonExistent(username);
                    }
                    profileCache.put(lower, new CacheEntry(profile, System.currentTimeMillis()));
                    return profile;
                })
                .exceptionally(ex -> {
                    logger.debug("Mojang API lookup failed for {}: {}", username, ex.getMessage());
                    // In case of timeout or network error, return non-existent or check cache
                    return MojangProfile.nonExistent(username);
                });
    }

    private static UUID parseTrimmedUuid(String trimmed) {
        if (trimmed == null || trimmed.length() != 32) {
            throw new IllegalArgumentException("Invalid UUID string: " + trimmed);
        }
        String formatted = trimmed.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5"
        );
        return UUID.fromString(formatted);
    }

    public record MojangProfile(boolean exists, String exactName, UUID uuid) {
        public static MojangProfile nonExistent(String name) {
            return new MojangProfile(false, name, null);
        }
    }

    private record CacheEntry(MojangProfile profile, long timestamp) {
    }
}
