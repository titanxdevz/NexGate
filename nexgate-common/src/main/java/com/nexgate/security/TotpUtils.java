package com.nexgate.security;

import com.nexgate.crypto.HashingUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;

/**
 * Enterprise RFC 6238 Time-Based One-Time Password (TOTP) and recovery code service.
 * Supports configurable time drift windows, Base32 secret encoding, and cryptographically hashed single-use recovery codes.
 */
public class TotpUtils {

    private static final String HMAC_ALGO = "HmacSHA1";
    private static final int TIME_STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int MODULUS = (int) Math.pow(10, DIGITS);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // Base32 character set (RFC 4648)
    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final char[] RECOVERY_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray(); // Ambiguous chars omitted (I, O, 0, 1)

    private TotpUtils() {
    }

    /**
     * Generates a new 160-bit (20-byte) cryptographically secure Base32-encoded secret.
     */
    public static String generateSecret() {
        byte[] buffer = new byte[20];
        SECURE_RANDOM.nextBytes(buffer);
        return encodeBase32(buffer);
    }

    /**
     * Verifies a 6-digit TOTP code against a secret with a configurable time-step drift window.
     *
     * @param base32Secret the user's Base32 TOTP secret
     * @param codeString the 6-digit code string entered by the user
     * @param windowSteps number of time steps to allow before/after current time (e.g. 1 allows -30s to +30s)
     * @return true if valid
     */
    public static boolean verifyCode(String base32Secret, String codeString, int windowSteps) {
        if (base32Secret == null || codeString == null) {
            return false;
        }

        String cleanedCode = codeString.trim();
        if (cleanedCode.length() != DIGITS) {
            return false;
        }

        int inputCode;
        try {
            inputCode = Integer.parseInt(cleanedCode);
        } catch (NumberFormatException e) {
            return false;
        }

        byte[] keyBytes;
        try {
            keyBytes = decodeBase32(base32Secret);
        } catch (IllegalArgumentException e) {
            return false;
        }

        long currentStep = System.currentTimeMillis() / 1000L / TIME_STEP_SECONDS;

        for (int i = -windowSteps; i <= windowSteps; i++) {
            int expectedCode = generateCodeForStep(keyBytes, currentStep + i);
            if (expectedCode == inputCode) {
                return true;
            }
        }

        return false;
    }

    /**
     * Generates the current TOTP code as a formatted 6-digit string.
     */
    public static String generateCurrentCode(String base32Secret) {
        byte[] key = decodeBase32(base32Secret);
        long currentStep = System.currentTimeMillis() / 1000L / TIME_STEP_SECONDS;
        int code = generateCodeForStep(key, currentStep);
        return String.format("%06d", code);
    }

    /**
     * Formats an otpauth:// URI compatible with Google Authenticator, Aegis, Authy, etc.
     */
    public static String formatTotpUri(String issuer, String username, String secret) {
        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedUser = URLEncoder.encode(username, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + encodedIssuer + ":" + encodedUser +
                "?secret=" + secret +
                "&issuer=" + encodedIssuer +
                "&algorithm=SHA1&digits=6&period=30";
    }

    /**
     * Generates a batch of single-use plaintext recovery codes.
     */
    public static List<String> generatePlainRecoveryCodes(int count) {
        List<String> codes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            StringBuilder sb = new StringBuilder(10);
            for (int j = 0; j < 9; j++) {
                if (j == 4) {
                    sb.append('-');
                }
                sb.append(RECOVERY_CHARS[SECURE_RANDOM.nextInt(RECOVERY_CHARS.length)]);
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    /**
     * Hashes recovery codes using SHA-256 for safe database storage.
     */
    public static List<String> hashRecoveryCodes(List<String> plainCodes) {
        List<String> hashes = new ArrayList<>(plainCodes.size());
        for (String c : plainCodes) {
            hashes.add(HashingUtils.sha256Hex(normalizeRecoveryCode(c)));
        }
        return hashes;
    }

    /**
     * Verifies a recovery code against a list of stored SHA-256 hashes.
     * If valid, returns the matching hash so it can be consumed and removed.
     */
    public static Optional<String> verifyAndFindRecoveryCodeHash(String plainCode, List<String> storedHashes) {
        if (plainCode == null || storedHashes == null || storedHashes.isEmpty()) {
            return Optional.empty();
        }
        String inputHash = HashingUtils.sha256Hex(normalizeRecoveryCode(plainCode));
        for (String storedHash : storedHashes) {
            if (HmacUtils.constantTimeEquals(inputHash, storedHash)) {
                return Optional.of(storedHash);
            }
        }
        return Optional.empty();
    }

    private static String normalizeRecoveryCode(String code) {
        return code.trim().replace("-", "").toUpperCase();
    }

    private static int generateCodeForStep(byte[] key, long step) {
        byte[] data = ByteBuffer.allocate(8).putLong(step).array();
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(key, HMAC_ALGO));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            return binary % MODULUS;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed computing HMAC-SHA1 for TOTP", e);
        }
    }

    // Base32 implementation (RFC 4648)
    private static String encodeBase32(byte[] data) {
        StringBuilder result = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int next = 0;
        int bitsLeft = 0;

        while (next < data.length) {
            buffer = (buffer << 8) | (data[next++] & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >>> (bitsLeft - 5)) & 0x1F;
                bitsLeft -= 5;
                result.append(BASE32_CHARS.charAt(index));
            }
        }

        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            result.append(BASE32_CHARS.charAt(index));
        }

        return result.toString();
    }

    private static byte[] decodeBase32(String base32) {
        String clean = base32.trim().toUpperCase().replace("=", "");
        byte[] bytes = new byte[clean.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int count = 0;

        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            int val = BASE32_CHARS.indexOf(c);
            if (val < 0) {
                throw new IllegalArgumentException("Invalid Base32 character: " + c);
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bytes[count++] = (byte) ((buffer >>> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return bytes;
    }
}
