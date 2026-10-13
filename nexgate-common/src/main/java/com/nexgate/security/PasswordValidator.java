package com.nexgate.security;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Enterprise password strength and security validator.
 */
public class PasswordValidator {

    private static final Set<String> COMMON_PASSWORDS = new HashSet<>(List.of(
            "123456", "password", "12345678", "qwerty", "123456789", "12345", "1234", "111111",
            "1234567", "dragon", "welcome", "ninja", "monkey", "solo", "minecraft", "login123",
            "secret", "pass1234", "qwertyuiop", "abc123", "master", "football", "chelsea",
            "superman", "killer", "shadow", "sunshine", "charlie", "iloveyou", "princess",
            "rockstar", "starwars", "password123", "admin", "admin123", "root", "toor", "guest"
    ));

    private final int minLength;
    private final int maxLength;
    private final boolean requireDigit;
    private final boolean requireMixedCase;
    private final boolean requireSpecial;

    public PasswordValidator(int minLength, int maxLength, boolean requireDigit, boolean requireMixedCase, boolean requireSpecial) {
        this.minLength = Math.max(4, minLength);
        this.maxLength = Math.max(minLength, maxLength);
        this.requireDigit = requireDigit;
        this.requireMixedCase = requireMixedCase;
        this.requireSpecial = requireSpecial;
    }

    public static PasswordValidator createDefault() {
        return new PasswordValidator(6, 64, false, false, false);
    }

    /**
     * Validates a password against security rules.
     * @param password plain text password
     * @param username username of the user (to verify similarity)
     * @return ValidationResult indicating success or failure reason
     */
    public ValidationResult validate(String password, String username) {
        if (password == null || password.isEmpty()) {
            return ValidationResult.fail("Password cannot be empty.");
        }

        if (password.length() < minLength) {
            return ValidationResult.fail("Password must be at least " + minLength + " characters long.");
        }

        if (password.length() > maxLength) {
            return ValidationResult.fail("Password must not exceed " + maxLength + " characters.");
        }

        String passLower = password.toLowerCase();

        if (COMMON_PASSWORDS.contains(passLower)) {
            return ValidationResult.fail("This password is too common and easily guessed.");
        }

        if (username != null && !username.isEmpty()) {
            String userLower = username.toLowerCase();
            if (passLower.equals(userLower)) {
                return ValidationResult.fail("Password cannot be identical to your username.");
            }
            if (userLower.length() >= 4 && passLower.contains(userLower)) {
                return ValidationResult.fail("Password cannot contain your username.");
            }
        }

        if (requireDigit && !password.matches(".*\\d.*")) {
            return ValidationResult.fail("Password must contain at least one digit (0-9).");
        }

        if (requireMixedCase && (!password.matches(".*[a-z].*") || !password.matches(".*[A-Z].*"))) {
            return ValidationResult.fail("Password must contain both uppercase and lowercase letters.");
        }

        if (requireSpecial && !password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            return ValidationResult.fail("Password must contain at least one special character.");
        }

        return ValidationResult.ok();
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean valid() {
            return valid;
        }

        public String message() {
            return message;
        }

        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult fail(String message) {
            return new ValidationResult(false, message);
        }
    }
}
