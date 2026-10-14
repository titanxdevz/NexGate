package com.nexgate.model;

/**
 * Account identity type in NexGate storage.
 */
public enum AccountType {
    /**
     * Officially verified Mojang Java account.
     */
    PREMIUM,

    /**
     * Locally registered offline/cracked account.
     */
    OFFLINE,

    /**
     * Bedrock Edition player authenticated via Floodgate.
     */
    BEDROCK;

    public static AccountType fromString(String type) {
        if (type == null) return OFFLINE;
        try {
            return AccountType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OFFLINE;
        }
    }
}
