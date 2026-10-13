package com.nexgate.model;

import java.util.UUID;

/**
 * Represents an authenticated user.
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
    private final String totpSecret;
    private final boolean totpEnabled;
    
    public UserAuth(long id, String username, String usernameLower, UUID uuid, String passwordHash,
                    String ip, long regDate, long lastLogin, String lastIp, boolean premium,
                    String totpSecret, boolean totpEnabled) {
        this.id = id;
        this.username = username;
        this.usernameLower = usernameLower;
        this.uuid = uuid;
        this.passwordHash = passwordHash;
        this.ip = ip;
        this.regDate = regDate;
        this.lastLogin = lastLogin;
        this.lastIp = lastIp;
        this.premium = premium;
        this.totpSecret = totpSecret;
        this.totpEnabled = totpEnabled;
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
    
    public String getTotpSecret() {
        return totpSecret;
    }
    
    public boolean isTotpEnabled() {
        return totpEnabled;
    }
}
```