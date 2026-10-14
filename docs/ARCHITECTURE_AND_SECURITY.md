# NexGate Architecture and Security Specification

## 1. System Overview

NexGate is an enterprise-grade Minecraft authentication and defense-in-depth security suite engineered for high-concurrency Velocity proxy networks and Paper backend servers.

```
                           +------------------------+
                           |  Minecraft Client(s)  |
                           +-----------+------------+
                                       |
                               (Port 25565)
                                       v
                    +--------------------------------------+
                    |       Velocity Proxy Gateway         |
                    |  - AntiBot Connection Pipeline       |
                    |  - Async PreLogin (EventTask)        |
                    |  - Mojang Online Mode Verification   |
                    |  - Bounded Argon2id Hashing Worker   |
                    |  - RFC 6238 TOTP 2FA Engine          |
                    |  - HikariCP Database Connection Pool |
                    +------------------+-------------------+
                                       |
                   Canonical HMAC-SHA256 Inter-Server Protocol
                   (nexgate:auth Channel, Nonce & Timestamp)
                                       |
                    +------------------+-------------------+
                    |                                      |
                    v                                      v
     +-----------------------------+        +-----------------------------+
     |   Paper Auth Limbo Server   |        |     Paper Lobby Server      |
     |  - Strict Interaction Gate  |        |  - Strict Interaction Gate  |
     |  - Movement & Inventory Lock|        |  - Chat Color & Styling     |
     |  - Potion Barriers & Blind  |        |  - Session Consumption      |
     +-----------------------------+        +-----------------------------+
```

---

## 2. Authentication Modes & Routing Policies

NexGate supports three explicit authentication modes configured via `auth.authMode`:

| Mode | Routing & Enforcement Policy |
| :--- | :--- |
| **`HYBRID`** (Recommended) | Verified official Mojang accounts undergo cryptographic online mode encryption and are authenticated automatically. Offline / Bedrock players authenticate through NexGate passwords and optional TOTP. Impersonation of verified premium accounts from offline mode is strictly prohibited. |
| **`ONLINE_ONLY`** | Strict online mode encryption is enforced for all connecting Java accounts. Bedrock/Floodgate and cracked accounts are rejected at PreLogin. |
| **`OFFLINE_AUTH`** | All players (regardless of official Mojang status) authenticate through NexGate's internal database. Online mode auto-login is disabled. |

### Strict Ownership Verification Guarantees
1. **Never Trust Unauthenticated Claims**: NexGate never marks an account as Premium based on username lookup, client handshake claims, or UUID resemblance.
2. **Cryptographic Proof via `/premium`**: When an existing offline player runs `/premium`, their account is flagged with `pending_premium = true`. Upon their next reconnect, Velocity forces official Mojang online encryption. Only when `player.isOnlineMode()` is verified is the account upgraded to `PREMIUM`.
3. **Fail-Closed Session Outages**: If the Mojang API / session server times out or is unreachable during profile resolution, the login attempt is denied with `mojangUnavailable`. It NEVER falls back to an unauthenticated offline session.
4. **Offline Impersonation Blockade**: If an account has been verified as Premium, any connection arriving via offline mode is immediately disconnected with `premiumImpersonationKick` and logged as a security alert in `nexgate_audit_logs`.

---

## 3. Cryptographic Primitives

### 3.1 Bounded Argon2id Password Hashing
- **Algorithm**: Argon2id (RFC 9106)
- **Parameters**: 64 MB RAM (`memoryKb = 65536`), 3 iterations (`iterations = 3`), 1 parallelism thread.
- **Resource Protection**: A strict counting `Semaphore` limits concurrent hashing workers to prevent CPU starvation and JVM `OutOfMemoryError` during authentication spikes.
- **Transparent Parameter Re-Hashing**: When login succeeds, NexGate inspects the encoded hash. If historical parameters differ from current security policy, the hash is seamlessly re-computed with updated parameters.

### 3.2 RFC 6238 TOTP Two-Factor Authentication
- **Secret Generation**: Cryptographically secure 160-bit (20-byte) Base32 secrets.
- **Time Drift Window**: Configurable step drift window (default ±30s) to account for client device clock inaccuracies.
- **Single-Use Recovery Codes**: Upon setup, 8 single-use recovery codes (`XXXX-XXXX`) are generated, SHA-256 hashed, and stored in the database. When redeemed, the specific hash is permanently consumed.
- **Brute-Force Lockout**: 5 failed 2FA attempts lock the account's TOTP verification for 300 seconds.

### 3.3 Proxy-to-Backend Authenticated Messaging (HMAC-SHA256)
All inter-server plugin messages sent across `nexgate:auth` use canonical HMAC-SHA256 signing:

```
canonical_data = protocol_version + "|" +
                 message_type + "|" +
                 sender_id + "|" +
                 player_uuid + "|" +
                 player_username.toLowerCase() + "|" +
                 nonce + "|" +
                 timestamp_millis + "|" +
                 payload
```

- **Constant-Time Verification**: Comparison uses `java.security.MessageDigest.isEqual` to eliminate timing side-channel attacks.
- **Sliding-Window Replay Cache**: Backends reject any message older than 30 seconds or containing a previously observed nonce.
- **Secret Rotation**: Backends support a list of active secrets, allowing zero-downtime key rotation.

---

## 4. Multi-Stage AntiBot Pipeline

Operates during `PreLoginEvent` before database lookups or password processing:

1. **CIDR Whitelist**: Trusted subnets and proxy addresses bypass rate limiting.
2. **CIDR & IP Blacklist**: Drops malformed or blocked subnets instantly.
3. **Temporary Network Throttling**: Banned IPs are rejected with remaining cooldown display.
4. **Fast Reconnect / Bot Loop Suppression**: Reconnecting within `<1200ms` of disconnecting triggers an immediate connection burst block and temporary ban.
5. **Global & Per-IP Rate Limits**: Bounded token buckets protect both global gateway capacity and per-IP slots. IPv6 addresses are aggregated to their `/64` prefix.
6. **Automatic Under-Attack Mitigation**: When connections per second surpass the threshold (default: 15 conns/s), NexGate enters Under-Attack Mitigation mode, tightening limits across all ingress traffic.
