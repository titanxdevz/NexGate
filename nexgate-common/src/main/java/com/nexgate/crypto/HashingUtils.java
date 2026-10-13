package com.nexgate.crypto;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password hashing utilities using Argon2id.
 * Argon2id is memory-hard and provides resistance against GPU/ASIC attacks.
 * License: Apache 2.0 for argon2-jvm dependency.
 */
public class HashingUtils {
    private static final Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
    private static final SecureRandom secureRandom = new SecureRandom();
    
    // Argon2id parameters - tuned for security while remaining fast enough
    private static final int ITERATIONS = 3;
    private static final int MEMORY = 65536; // 64 MB
    private static final int PARALLELISM = 1;
    
    private HashingUtils() {
    }
    
    /**
     * Hash a password using Argon2id.
     * @param password the plain text password
     * @return the encoded hash string (includes salt and parameters)
     */
    public static String hashPassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        return argon2.hash(ITERATIONS, MEMORY, PARALLELISM, password.toCharArray());
    }
    
    /**
     * Verify a password against an Argon2id hash.
     * Uses constant-time comparison internally via Argon2 library.
     * @param password the plain text password
     * @param hash the encoded hash from hashPassword()
     * @return true if password matches
     */
    public static boolean verifyPassword(String password, String hash) {
        if (password == null || hash == null || hash.isEmpty()) {
            return false;
        }
        try {
            return argon2.verify(hash, password.toCharArray());
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Generate a cryptographically secure random token.
     * @param length the number of random bytes
     * @return base64url encoded token
     */
    public static String generateSecureToken(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    /**
     * Compute SHA-256 hash of input.
     * @param input input string
     * @return hex-encoded SHA-256
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```