package com.nexgate.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents an authenticated user in the NexGate security system.
 */
public class UserAuth {
    private final long id;
    private final String username;
    private final String usernameLower;
    private final UUID uuid;
    private final String passwordHash;
    private final String ip;
    private final long regDate;
    private final long lastLogin;
    private final String lastIp;
    private final boolean premium;
    private final UUID premiumUuid;
    private final AccountType accountType;
    private final String messageColor;
    private final String messageStyle;
    private final String totpSecret;
    private final boolean totpEnabled;
    private final String totpRecoveryCodes;
    private final boolean locked;
    private final String lockReason;
    private final boolean pendingPremium;

    public UserAuth(long id, String username, String usernameLower, UUID uuid, String passwordHash,
                    String ip, long regDate, long lastLogin, String lastIp, boolean premium,
                    UUID premiumUuid, AccountType accountType, String messageColor, String messageStyle,
                    String totpSecret, boolean totpEnabled, String totpRecoveryCodes,
                    boolean locked, String lockReason, boolean pendingPremium) {
        this.id = id;
        this.username = Objects.requireNonNull(username, "username cannot be null");
        this.usernameLower = usernameLower != null ? usernameLower : username.toLowerCase();
        this.uuid = uuid;
        this.passwordHash = passwordHash;
        this.ip = ip;
        this.regDate = regDate;
        this.lastLogin = lastLogin;
        this.lastIp = lastIp;
        this.premium = premium;
        this.premiumUuid = premiumUuid;
        this.accountType = accountType != null ? accountType : (premium ? AccountType.PREMIUM : AccountType.OFFLINE);
        this.messageColor = messageColor != null ? messageColor : "WHITE";
        this.messageStyle = messageStyle != null ? messageStyle : "DEFAULT";
        this.totpSecret = totpSecret;
        this.totpEnabled = totpEnabled;
        this.totpRecoveryCodes = totpRecoveryCodes;
        this.locked = locked;
        this.lockReason = lockReason;
        this.pendingPremium = pendingPremium;
    }

    // Convenience constructor for backwards compatibility
    public UserAuth(long id, String username, String usernameLower, UUID uuid, String passwordHash,
                    String ip, long regDate, long lastLogin, String lastIp, boolean premium,
                    UUID premiumUuid, AccountType accountType, String messageColor, String messageStyle,
                    String totpSecret, boolean totpEnabled, String totpRecoveryCodes,
                    boolean locked, String lockReason) {
        this(id, username, usernameLower, uuid, passwordHash, ip, regDate, lastLogin, lastIp,
                premium, premiumUuid, accountType, messageColor, messageStyle,
                totpSecret, totpEnabled, totpRecoveryCodes, locked, lockReason, false);
    }

    // Convenience constructor for legacy callers
    public UserAuth(long id, String username, String usernameLower, UUID uuid, String passwordHash,
                    String ip, long regDate, long lastLogin, String lastIp, boolean premium,
                    UUID premiumUuid, String messageColor, String messageStyle,
                    String totpSecret, boolean totpEnabled) {
        this(id, username, usernameLower, uuid, passwordHash, ip, regDate, lastLogin, lastIp,
                premium, premiumUuid, premium ? AccountType.PREMIUM : AccountType.OFFLINE,
                messageColor, messageStyle, totpSecret, totpEnabled, null, false, null, false);
    }

    public long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getUsernameLower() {
        return usernameLower;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getIp() {
        return ip;
    }

    public long getRegDate() {
        return regDate;
    }

    public long getLastLogin() {
        return lastLogin;
    }

    public String getLastIp() {
        return lastIp;
    }

    public boolean isPremium() {
        return premium;
    }

    public UUID getPremiumUuid() {
        return premiumUuid;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public String getMessageColor() {
        return messageColor;
    }

    public String getMessageStyle() {
        return messageStyle;
    }

    public String getTotpSecret() {
        return totpSecret;
    }

    public boolean isTotpEnabled() {
        return totpEnabled;
    }

    public String getTotpRecoveryCodes() {
        return totpRecoveryCodes;
    }

    public boolean isLocked() {
        return locked;
    }

    public String getLockReason() {
        return lockReason;
    }

    public boolean isPendingPremium() {
        return pendingPremium;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long id;
        private String username;
        private String usernameLower;
        private UUID uuid;
        private String passwordHash;
        private String ip;
        private long regDate = System.currentTimeMillis();
        private long lastLogin;
        private String lastIp;
        private boolean premium;
        private UUID premiumUuid;
        private AccountType accountType;
        private String messageColor = "WHITE";
        private String messageStyle = "DEFAULT";
        private String totpSecret;
        private boolean totpEnabled;
        private String totpRecoveryCodes;
        private boolean locked;
        private String lockReason;
        private boolean pendingPremium;

        public Builder id(long id) {
            this.id = id;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            if (this.usernameLower == null && username != null) {
                this.usernameLower = username.toLowerCase();
            }
            return this;
        }

        public Builder usernameLower(String usernameLower) {
            this.usernameLower = usernameLower;
            return this;
        }

        public Builder uuid(UUID uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder passwordHash(String passwordHash) {
            this.passwordHash = passwordHash;
            return this;
        }

        public Builder ip(String ip) {
            this.ip = ip;
            return this;
        }

        public Builder regDate(long regDate) {
            this.regDate = regDate;
            return this;
        }

        public Builder lastLogin(long lastLogin) {
            this.lastLogin = lastLogin;
            return this;
        }

        public Builder lastIp(String lastIp) {
            this.lastIp = lastIp;
            return this;
        }

        public Builder premium(boolean premium) {
            this.premium = premium;
            if (this.accountType == null) {
                this.accountType = premium ? AccountType.PREMIUM : AccountType.OFFLINE;
            }
            return this;
        }

        public Builder premiumUuid(UUID premiumUuid) {
            this.premiumUuid = premiumUuid;
            return this;
        }

        public Builder accountType(AccountType accountType) {
            this.accountType = accountType;
            return this;
        }

        public Builder messageColor(String messageColor) {
            this.messageColor = messageColor;
            return this;
        }

        public Builder messageStyle(String messageStyle) {
            this.messageStyle = messageStyle;
            return this;
        }

        public Builder totpSecret(String totpSecret) {
            this.totpSecret = totpSecret;
            return this;
        }

        public Builder totpEnabled(boolean totpEnabled) {
            this.totpEnabled = totpEnabled;
            return this;
        }

        public Builder totpRecoveryCodes(String totpRecoveryCodes) {
            this.totpRecoveryCodes = totpRecoveryCodes;
            return this;
        }

        public Builder locked(boolean locked) {
            this.locked = locked;
            return this;
        }

        public Builder lockReason(String lockReason) {
            this.lockReason = lockReason;
            return this;
        }

        public Builder pendingPremium(boolean pendingPremium) {
            this.pendingPremium = pendingPremium;
            return this;
        }

        public UserAuth build() {
            return new UserAuth(id, username, usernameLower, uuid, passwordHash, ip,
                    regDate, lastLogin, lastIp, premium, premiumUuid, accountType,
                    messageColor, messageStyle, totpSecret, totpEnabled, totpRecoveryCodes,
                    locked, lockReason, pendingPremium);
        }
    }
}
