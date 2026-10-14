package com.nexgate.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Enterprise HMAC utilities for proxy<->backend inter-server message authentication.
 * Uses HMAC-SHA256 with constant-time verification, canonical payload encoding,
 * replay-resistant nonces, and secret rotation support.
 */
public class HmacUtils {
    public static final int CURRENT_PROTOCOL_VERSION = 1;
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private HmacUtils() {
    }

    /**
     * Compute HMAC-SHA256 hex digest.
     * @param secret shared secret
     * @param data data to sign
     * @return hex encoded HMAC
     */
    public static String computeHmac(String secret, String data) {
        if (secret == null || data == null) {
            throw new IllegalArgumentException("Secret and data cannot be null");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HEX_FORMAT.formatHex(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256", e);
        }
    }

    /**
     * Computes canonical HMAC for all inter-server message types.
     */
    public static String computeCanonicalHmac(String secret, int version, String messageType,
                                              String sender, String playerUuid, String playerName,
                                              String nonce, long timestamp, String payload) {
        String canonical = version + "|" +
                (messageType == null ? "" : messageType) + "|" +
                (sender == null ? "" : sender) + "|" +
                (playerUuid == null ? "" : playerUuid) + "|" +
                (playerName == null ? "" : playerName.toLowerCase()) + "|" +
                (nonce == null ? "" : nonce) + "|" +
                timestamp + "|" +
                (payload == null ? "" : payload);
        return computeHmac(secret, canonical);
    }

    /**
     * Verifies canonical HMAC supporting primary and optional fallback secret (for rotation).
     */
    public static boolean verifyCanonicalHmac(List<String> secrets, int version, String messageType,
                                             String sender, String playerUuid, String playerName,
                                             String nonce, long timestamp, String payload, String hmac) {
        if (secrets == null || secrets.isEmpty() || hmac == null) {
            return false;
        }
        for (String secret : secrets) {
            if (secret != null && !secret.isEmpty()) {
                String expected = computeCanonicalHmac(secret, version, messageType, sender, playerUuid, playerName, nonce, timestamp, payload);
                if (constantTimeEquals(expected, hmac)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Compute HMAC for secure perform.login message with nonce, color and style.
     */
    public static String computePerformLoginHmac(String secret, String playerName, String nonce,
                                                 long timestamp, String uuid, String color, String style) {
        String payload = playerName.toLowerCase() + ":" +
                (nonce == null ? "" : nonce) + ":" +
                timestamp + ":" +
                (uuid == null ? "" : uuid) + ":" +
                (color == null ? "WHITE" : color) + ":" +
                (style == null ? "DEFAULT" : style);
        return computeHmac(secret, payload);
    }

    /**
     * Legacy compute HMAC for perform.login message: playerName:timestamp:uuidOrEmpty
     */
    public static String computePerformLoginHmac(String secret, String playerName, long timestamp, String uuid) {
        String payload = playerName.toLowerCase() + ":" + timestamp + ":" + (uuid == null ? "" : uuid);
        return computeHmac(secret, payload);
    }

    /**
     * Cryptographically secure constant-time comparison of two strings.
     * Prevents timing attacks using MessageDigest.isEqual.
     */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }
}
