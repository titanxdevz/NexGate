package com.nexgate.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * HMAC utilities for proxy<->backend message signing.
 * Uses HMAC-SHA256 with constant-time comparison and replay-resistant nonce signing.
 */
public class HmacUtils {
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
     * Constant-time comparison of two hex strings.
     * Prevents timing attacks.
     */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
