# Configuration Guide

NexAuth uses two configuration files:
1. `velocity-config.json`: The primary configuration located at `plugins/nexgate/velocity-config.json` on the Velocity proxy.
2. `config.yml`: The backend companion configuration located at `plugins/NexGate/config.yml` on each Paper server.

---

## Velocity Configuration (`velocity-config.json`)

### `auth` Section

Controls routing, timeouts, and authentication policies.

| Field | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `authMode` | String | `"HYBRID"` | Authentication mode: `HYBRID` (auto-detects Mojang online mode for paid accounts, offline auth for others), `ONLINE_ONLY` (Mojang accounts only), or `OFFLINE_AUTH` (NexAuth password for all). |
| `authServers` | Array | `["auth"]` | List of backend server names designated as authentication / limbo servers where unauthenticated players are held. |
| `targetServer` | String | `"lobby"` | Name of the destination server players are transferred to upon successful authentication. |
| `loginTimeoutSeconds` | Integer | `60` | Maximum time in seconds an unauthenticated player has to log in or register before being disconnected. |
| `sessionTimeoutMinutes` | Integer | `120` | Duration in minutes that an authenticated session remains valid across reconnects. |
| `sessionStrictIp` | Boolean | `false` | When `true`, session restore requires an exact IP address match. When `false`, allows session restore if the player connects from the same `/24` subnet. |
| `requireAuth` | Boolean | `true` | When `false`, authentication enforcement is globally bypassed (useful for debugging). |
| `showBossBar` | Boolean | `true` | Displays a live countdown bossbar to unauthenticated players. |
| `showTitle` | Boolean | `true` | Displays screen title and subtitle prompts on join. |
| `playSounds` | Boolean | `true` | Plays audio cues on login events, incorrect passwords, and session restoration. |

### `security` Section

Configures cryptographic settings, rate limiting, and brute-force defenses.

| Field | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `hmacSecret` | String | (generated) | 32+ character shared secret used to sign inter-server plugin messages. Must match backend `config.yml`. |
| `rateLimitIp.max` | Integer | `5` | Maximum connection handshakes permitted per IP within the window. |
| `rateLimitIp.windowSeconds` | Integer | `60` | Duration in seconds of the IP connection rate limit window. |
| `rateLimitAccount.max` | Integer | `5` | Maximum failed password attempts before kicking the connection. |
| `rateLimitAccount.windowSeconds` | Integer | `300` | Window in seconds over which failed password attempts accumulate. |
| `maxAccountsPerIp` | Integer | `3` | Maximum number of unique accounts that can be registered from a single IP address. |
| `ipCooldownEnabled` | Boolean | `true` | Enables temporary IP lockout after repeated failed login kicks. |
| `maxFailedKicksBeforeIpCooldown` | Integer | `3` | Number of failed login kicks required before the IP is placed on cooldown. |
| `ipCooldownMinutes` | Long | `5` | Duration in minutes that an IP remains blocked from reconnecting after triggering cooldown. |
| `passwordMinLength` | Integer | `6` | Minimum allowed password length. |
| `passwordMaxLength` | Integer | `64` | Maximum allowed password length. |
| `requireNumbers` | Boolean | `false` | When `true`, passwords must contain at least one numeric digit. |
| `requireSpecialChars` | Boolean | `false` | When `true`, passwords must contain at least one special character. |

### `premium` Section

Configures official Mojang online-mode auto-login for Java Edition accounts.

| Field | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `enabled` | Boolean | `true` | Enables the premium authentication system. |
| `autoDetect` | Boolean | `true` | Queries Mojang session servers during PreLogin to verify if a username is a registered paid Java account. |
| `forceOnlineModeForPremium` | Boolean | `true` | Enforces official Mojang encryption on verified premium accounts, preventing name theft via offline launchers. |
| `mojangTimeoutSeconds` | Integer | `3` | Network timeout for HTTP requests to Mojang session servers. |
| `cacheDurationMinutes` | Integer | `60` | Duration to cache Mojang profile lookup results in memory. |

### `antibot` Section

Configures connection admission filtering and flood mitigation.

| Field | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `enabled` | Boolean | `true` | Enables AntiBot connection admission checks. |
| `mode` | String | `"AUTO"` | Operating mode: `AUTO` (activates under attack), `ON` (always strict), or `OFF` (disabled). |
| `globalRateLimit` | Integer | `40` | Maximum connection handshakes per second allowed across the entire network. |
| `perIpRateLimit` | Integer | `4` | Maximum connections per second allowed from a single IP address or IPv6 `/64` prefix. |
| `maxConcurrentPerIp` | Integer | `3` | Maximum active simultaneous connections allowed per IP. |
| `fastReconnectThresholdMs` | Long | `1200` | Reconnect threshold in milliseconds below which a client is flagged as a bot loop. |
| `mitigationThreshold` | Integer | `15` | Handshakes/second threshold that triggers mitigation mode. |
| `mitigationDurationSeconds` | Long | `60` | Duration in seconds mitigation mode remains active before evaluating traffic. |
| `blacklistedCidrs` | Array | `[]` | List of IP addresses or CIDR subnets permanently blocked from connecting. |
| `whitelistedCidrs` | Array | `["127.0.0.1/32"]` | List of IP addresses or CIDR subnets exempt from AntiBot throttling. |

### `totp` Section

Two-Factor Authentication (RFC 6238) configuration.

| Field | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `enabled` | Boolean | `true` | Enables 2FA support via `/2fa`. |
| `issuer` | String | `"NexGate Network"` | Name displayed in authenticator applications. |
| `windowSteps` | Integer | `1` | Number of 30-second time steps allowed before/after current time to accommodate clock drift. |
| `maxAttempts` | Integer | `5` | Maximum failed 2FA verification attempts before kicking the player. |
| `lockoutDurationSeconds` | Long | `300` | Cooldown duration for repeated failed 2FA attempts. |

### `argon2` Section

Argon2id password hashing parameters.

| Field | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `maxConcurrent` | Integer | `2` | Maximum concurrent Argon2id hashing threads to prevent CPU exhaustion. |
| `iterations` | Integer | `3` | Time cost parameter (number of iterations). |
| `memoryKb` | Integer | `65536` | Memory cost parameter in KiB (65536 KiB = 64 MiB). |
| `parallelism` | Integer | `1` | Degree of parallelism. |

### `database` Section

Database storage backend settings.

| Field | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `type` | String | `"sqlite"` | Storage engine: `"sqlite"` or `"mysql"`. |
| `sqliteFile` | String | `"plugins/nexgate/nexgate.db"` | Path to the SQLite database file. |
| `host` | String | `"localhost"` | MySQL server hostname. |
| `port` | Integer | `3306` | MySQL server port. |
| `database` | String | `"nexgate"` | MySQL database name. |
| `username` | String | `"nexgate"` | MySQL user. |
| `password` | String | `"password"` | MySQL password. |

---

## Paper Configuration (`plugins/NexGate/config.yml`)

The backend companion configuration controls player restrictions while on limbo or lobby servers.

```yaml
security:
  # Shared HMAC secret matching velocity-config.json
  hmacSecret: "your-shared-secret-here"

limbo:
  # Set to true on auth/limbo servers, false on lobby/gameplay servers
  enabled: true
  enforceCage: true
  cageRadius: 3
  blindness: true
  slowness: true
  invulnerable: true

pinpad:
  enabled: true
  title: "Enter Authentication PIN"

spawn:
  enabled: true
  teleportOnJoin: true
  teleportOnLogin: true
  world: "world"
  x: 0.5
  y: 64.0
  z: 0.5
  yaw: 0.0
  pitch: 0.0
```
