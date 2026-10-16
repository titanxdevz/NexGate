package com.nexgate.security;

import java.util.regex.Pattern;

/**
 * Validates Minecraft username format and protects against casing and spoofing exploits.
 * Supports standard Java Edition usernames and Bedrock / Floodgate prefixed gamertags.
 */
public class UsernameValidator {

    // Standard Java Edition username: 3-16 alphanumeric or underscore characters
    private static final Pattern STANDARD_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

    // Floodgate / Bedrock pattern: allows leading prefix like '.', '*', or '+' followed by 1-20 valid characters
    private static final Pattern FLOODGATE_PATTERN = Pattern.compile("^[.*+][a-zA-Z0-9_ ]{1,20}$");

    private UsernameValidator() {
    }

    /**
     * Checks if a username conforms to official Minecraft Java naming conventions.
     */
    public static boolean isValidUsername(String username) {
        return isValidUsername(username, false);
    }

    /**
     * Checks if a username conforms to Minecraft naming conventions, optionally allowing Floodgate prefix.
     */
    public static boolean isValidUsername(String username, boolean allowFloodgate) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        if (STANDARD_PATTERN.matcher(username).matches()) {
            return true;
        }
        if (allowFloodgate && FLOODGATE_PATTERN.matcher(username).matches()) {
            return true;
        }
        return false;
    }

    /**
     * Checks if username is a Floodgate Bedrock formatted name.
     */
    public static boolean isFloodgateUsername(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        return FLOODGATE_PATTERN.matcher(username).matches();
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
