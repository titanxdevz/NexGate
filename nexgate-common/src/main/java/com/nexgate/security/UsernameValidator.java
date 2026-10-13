package com.nexgate.security;

import java.util.regex.Pattern;

/**
 * Validates Minecraft username format and protects against casing and spoofing exploits.
 */
public class UsernameValidator {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

    private UsernameValidator() {
    }

    /**
     * Checks if a username conforms to official Minecraft naming conventions.
     */
    public static boolean isValidUsername(String username) {
        if (username == null) {
            return false;
        }
        return USERNAME_PATTERN.matcher(username).matches();
    }

    /**
     * Checks if the connecting username matches the registered username's case.
     * Prevents case-spoofing attacks (e.g. "player" vs "Player").
     */
    public static boolean isCaseMatch(String connectingUsername, String registeredUsername) {
        if (connectingUsername == null || registeredUsername == null) {
            return false;
        }
        return connectingUsername.equals(registeredUsername);
    }
}
