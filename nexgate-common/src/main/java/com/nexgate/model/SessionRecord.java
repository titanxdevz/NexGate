package com.nexgate.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents an active authenticated session for auto-login.
 */
public class SessionRecord {
    private final UUID uuid;
    private final String usernameLower;
    private final String sessionToken;
    private final String ipAddress;
    private final long createdAt;
    private final long expiresAt;

    public SessionRecord(UUID uuid, String usernameLower, String sessionToken, String ipAddress, long createdAt, long expiresAt) {
        this.uuid = Objects.requireNonNull(uuid, "uuid cannot be null");
        this.usernameLower = Objects.requireNonNull(usernameLower, "usernameLower cannot be null").toLowerCase();
        this.sessionToken = Objects.requireNonNull(sessionToken, "sessionToken cannot be null");
        this.ipAddress = Objects.requireNonNull(ipAddress, "ipAddress cannot be null");
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getUsernameLower() {
        return usernameLower;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    /**
     * Verifies whether the session is valid for the given IP address.
     * @param currentIp current connecting IP
     * @param strictIp if true, requires exact IP match; if false, allows subnet match for IPv4
     * @return true if session is valid and not expired
     */
    public boolean isValid(String currentIp, boolean strictIp) {
        if (isExpired()) {
            return false;
        }
        if (currentIp == null) {
            return false;
        }
        if (strictIp) {
            return ipAddress.equalsIgnoreCase(currentIp.trim());
        }
        // Subnet check: if both are IPv4, allow /24 subnet match
        if (isSubnetMatch(ipAddress, currentIp)) {
            return true;
        }
        return ipAddress.equalsIgnoreCase(currentIp.trim());
    }

    private static boolean isSubnetMatch(String ip1, String ip2) {
        if (ip1 == null || ip2 == null) return false;
        String[] parts1 = ip1.split("\\.");
        String[] parts2 = ip2.split("\\.");
        if (parts1.length == 4 && parts2.length == 4) {
            return parts1[0].equals(parts2[0]) && parts1[1].equals(parts2[1]) && parts1[2].equals(parts2[2]);
        }
        return false;
    }
}
