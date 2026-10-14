package com.nexgate.model;

/**
 * Explicit authentication modes supported by NexGate.
 * Security correctness takes precedence over convenience.
 */
public enum AuthMode {
    /**
     * Require official cryptographically authenticated Java accounts via Mojang session servers.
     * Offline/cracked accounts are strictly denied admission.
     */
    ONLINE_ONLY,

    /**
     * Offline authentication mode. All players authenticate via NexGate's password/MFA system.
     * Official Mojang auto-login is disabled.
     */
    OFFLINE_AUTH,

    /**
     * Hybrid mode permitting both verified Mojang Premium accounts and password-authenticated offline accounts.
     * Strictly enforces identity separation:
     * - Verified Premium accounts must authenticate via official Mojang encryption.
     * - Offline accounts cannot impersonate Premium identities.
     * - Failed Premium authentication NEVER falls back to offline authentication for the same protected account.
     */
    HYBRID;

    public static AuthMode fromString(String mode) {
        if (mode == null) return HYBRID;
        try {
            return AuthMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return HYBRID;
        }
    }
}
