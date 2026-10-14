# NexGate Configuration Reference

This guide covers all configuration directives for both Velocity proxy (`velocity-config.json`) and Paper backend companion plugins (`config.yml`).

---

## 1. Velocity Proxy Configuration (`velocity-config.json`)

### `auth` Section
| Option | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `authMode` | String | `"HYBRID"` | Operating mode: `"HYBRID"`, `"ONLINE_ONLY"`, or `"OFFLINE_AUTH"`. |
| `authServers` | List[String] | `["auth"]` | List of server names designated as authentication limbo backends. |
| `targetServer` | String | `"lobby"` | Target server to forward authenticated players to. |
| `loginTimeoutSeconds` | Long | `60` | Max seconds a player has to register or log in before being kicked. |
| `sessionTimeoutMinutes`| Long | `120` | Duration in minutes that an authenticated session remains valid for auto-login. |
| `sessionStrictIp` | Boolean | `false` | When true, requires exact IP match for session restoration. When false, permits `/24` subnet roaming. |
| `requireAuth` | Boolean | `true` | Enables authentication gating. |
| `showBossBar` | Boolean | `true` | Shows animated countdown boss bar during authentication. |
| `showTitle` | Boolean | `true` | Shows title/subtitle prompts upon join. |
| `playSounds` | Boolean | `true` | Plays auditory chimes and error cues. |

### `security` Section
| Option | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `hmacSecret` | String | (Generated) | Base64-encoded 32-byte secret key shared with Paper backends. |
| `rateLimitIp.max` | Integer | `5` | Maximum connections per window per IP. |
| `rateLimitIp.windowSeconds` | Long | `60` | Sliding window in seconds for IP rate limiting. |
| `rateLimitAccount.max` | Integer | `5` | Maximum failed password attempts before account lockout. |
| `rateLimitAccount.windowSeconds` | Long | `300` | Window for tracking failed password attempts. |
| `maxAccountsPerIp` | Integer | `3` | Maximum number of registered accounts permitted from a single IP. |
| `lockoutDurationSeconds` | Long | `300` | Duration in seconds an account is locked out following failed attempts. |
| `passwordMinLength` | Integer | `6` | Minimum password length. |
| `passwordMaxLength` | Integer | `64` | Maximum password length. |
| `requireNumbers` | Boolean | `false` | Requires at least one numeric character. |
| `requireSpecialChars` | Boolean | `false` | Requires at least one symbol character. |

### `premium` Section
| Option | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `enabled` | Boolean | `true` | Master switch for official Mojang premium checking. |
| `autoDetect` | Boolean | `true` | Automatically checks Mojang session servers for unregistered players. |
| `forceOnlineModeForPremium` | Boolean | `true` | Forces official Mojang online mode encryption for verified premium accounts. |
| `mojangTimeoutSeconds` | Integer | `3` | Timeout in seconds for HTTP requests to Mojang API. |
| `cacheDurationMinutes` | Integer | `60` | In-memory cache TTL for Mojang UUID lookups. |

### `antibot` Section
| Option | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `enabled` | Boolean | `true` | Master switch for Sonar-class AntiBot defense. |
| `mode` | String | `"AUTO"` | Operating mode: `"AUTO"`, `"ON"`, or `"OFF"`. |
| `globalRateLimit` | Integer | `40` | Maximum overall connections per second proxy will accept. |
| `perIpRateLimit` | Integer | `4` | Maximum connections per second allowed per IP / IPv6 `/64` subnet. |
| `maxConcurrentPerIp` | Integer | `3` | Simultaneous active connections allowed from the same IP. |
| `fastReconnectThresholdMs`| Long | `1200` | Disconnect-to-reconnect duration that triggers burst suppression. |
| `mitigationThreshold` | Integer | `15` | Connections/sec threshold that triggers Under-Attack mode. |
| `mitigationDurationSeconds`| Long | `60` | Duration Under-Attack mitigation stays active after triggered. |
| `blacklistedCidrs` | List[String] | `[]` | Subnets permanently dropped at gateway (e.g. `["192.0.2.0/24"]`). |
| `whitelistedCidrs` | List[String] | `["127.0.0.1/32"]`| Subnets that bypass all AntiBot rate limits. |

### `totp` Section
| Option | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `enabled` | Boolean | `true` | Enables RFC 6238 TOTP two-factor authentication. |
| `issuer` | String | `"NexGate Network"`| Display name shown in Google Authenticator / Aegis. |
| `windowSteps` | Integer | `1` | Time drift steps permitted (1 step = ±30 seconds). |
| `maxAttempts` | Integer | `5` | Maximum invalid 2FA code attempts before temporary lockout. |
| `lockoutDurationSeconds` | Long | `300` | Cooldown duration when locked out of 2FA verification. |

### `argon2` Section
| Option | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `maxConcurrent` | Integer | `2` | Semaphore permit count for concurrent hashing workers. |
| `iterations` | Integer | `3` | Time cost parameter for Argon2id. |
| `memoryKb` | Integer | `65536` | Memory cost in KB (65536 KB = 64 MB). |
| `parallelism` | Integer | `1` | Thread lanes for Argon2id hashing. |

### `database` Section
Supports SQLite, MySQL/MariaDB, and PostgreSQL:

```json
// SQLite Example:
"database": {
  "type": "sqlite",
  "sqliteFile": "plugins/nexgate/nexgate.db"
}

// PostgreSQL Example:
"database": {
  "type": "postgresql",
  "host": "localhost",
  "port": 5432,
  "database": "nexgate",
  "username": "nexgate_user",
  "password": "secure_db_password"
}

// MySQL / MariaDB Example:
"database": {
  "type": "mysql",
  "host": "localhost",
  "port": 3306,
  "database": "nexgate",
  "username": "nexgate_user",
  "password": "secure_db_password"
}
```

---

## 2. Paper Backend Companion (`config.yml`)

```yaml
# Shared secret with Velocity proxy - MUST match velocity-config.json exactly!
hmac-secret: "nexgate_hmac_secret_9f8a3c2e1b4d5e6f7a8b9c0d1e2f3a4b"

# Defense-in-depth: Gate all player interactions until authenticated at proxy
strict-gating: true

# Apply visual and movement restrictions while unauthenticated
apply-blindness: true
apply-slowness: true

# Hide unauthenticated players from other online players
hide-unauthenticated-players: true

# Personalization: Allow chat formatting based on player's chosen color and style
allow-chat-color-formatting: true

# Chat display format template
chat-format: "<gray><player></gray> <dark_gray>»</dark_gray> <message>"
```
