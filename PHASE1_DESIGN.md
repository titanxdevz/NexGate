# PHASE 1 - Study Results & Design Doc

## References Studied (read-only, open source)
- AuthMeReloaded (GPLv3) - https://github.com/AuthMe/AuthMeReloaded
- KlyroLogin (MPL 2.0) - https://github.com/Klyronodes/KlyroLogin  
- LimboAuth (AGPLv3) + LimboAPI - https://github.com/Elytrium/LimboAuth

All licenses noted. No closed-source/decompiled jars used.

## How each handles login flow

### AuthMeReloaded (Backend-first with BungeeCord)
1. **PreLogin validation** (AsyncPlayerPreLoginEvent LOWEST/HIGHEST): name checks, unresolved hostname, unrestricted bypass, name restrictions, kick non-registered (if forced), anti-bot, casing, country check. Returns early on disallow.
2. **Join phase** (PlayerJoinEvent): teleport, management, register flow. Has guard to prevent limbo if already authenticated (race condition fix).
3. **Proxy session support**: when proxy sends PERFORM_LOGIN (signed), `ProxySessionManager` queues it (15s TTL). On join, checks `shouldResumeSession()` and can auto-login via `forceLoginFromProxy()`. 
4. **Session takeover guard**: checks `playerCache.isAuthenticated(name)` before scheduling limbo - critical fix to avoid freezing player if perform.login arrives between async/sync.

### KlyroLogin (Common core + Velocity/Paper)
1. **Common abstraction**: `KlyroListeners.onPreLogin(username, address)` returns `PreLoginResult(state, message, user)`. States: DENIED, FORCE_ONLINE, FORCE_OFFLINE.
2. **Velocity**: PreLoginEvent - maps result to Velocity's `PreLoginComponentResult` (denied/forceOnlineMode/forceOfflineMode). 
3. **Paper**: AsyncPlayerPreLoginEvent handling with same logic. Also manages auth state on backend.
4. **Premium/cracked**: forces online mode for premium users at proxy level (affects UUID assignment). Cracked → offline mode.

### LimboAuth (Velocity + LimboAPI)
1. **PreLogin** (Velocity PreLoginEvent, LATE order): checks if already denied. If not forced offline by other plugins:
- If premium: force online mode; may track pending login for verification (Netty injection to detect disconnect)
- If not premium: force offline mode
2. **Auth phase**: uses `AuthSessionHandler` in Limbo. Players in limbo see bossbar with timeout, rate-limited chat commands.
3. **Commands in limbo**: /login, /register with validation (password repeat/length/strength), IP registration limits, case checking.
4. **Auth timeout**: after `authTime` ms, disconnect. Rate limited attempts.

## Proxy<->Backend state sync

### AuthMeReloaded
- Plugin messaging channel: `authme:main`
- Messages (enum): LOGIN, LOGOUT, PERFORM_LOGIN, PERFORM_LOGIN_ACK, PROXY_STARTED, PREMIUM_SET/UNSET/LIST/CHUNK/PENDING_SET
- **PROXY_STARTED**: backend receives from proxy, responds with premium user list (async DB fetch, sent sync from carrier player)
- **PERFORM_LOGIN** (proxy→backend): signed with HMAC-SHA256. Payload: `playerName:timestamp:verifiedPremiumUuidOrEmpty`. Max age 30s. Backend validates secret non-empty, timestamp fresh, HMAC matches via constant-time compare.
- **LOGIN/LOGOUT** (backend→proxy): broadcast auth state changes
- Secret shared: `proxySharedSecret` on proxy, `Hooks.proxySharedSecret` on backend. Must match across all instances.

### KlyroLogin
- Cross-platform with common core; uses plugin messaging between Velocity and Paper (inferred from structure). Both sides implement listeners. State synced to keep auth consistent.

### LimboAuth
- Primarily Velocity-side auth (uses LimboAPI). Auth happens in proxy limbo before forwarding to backend servers. Backends receive players after auth completes at proxy level (standard Velocity forwarding). Minimal backend<->proxy message passing for premium state.

## Sessions

### AuthMeReloaded
- `ProxySessionManager`: in-memory `ExpiringMap<String, ProxyLoginRequest>` (15s TTL). Stores perform.login requests from proxy.
- `SessionService`: local backend sessions (if enabled). Proxy sessions take precedence for auto-login on server switch.
- Session consumed on use (`consumeLoginRequest`). Cleanup removes expired.

### KlyroLogin
- Manages auth sessions per player. Common session state in core. Paper has local session handling.

### LimboAuth
- Per-player `AuthSessionHandler` in limbo with `authTime` timeout. Bossbar shows remaining time. Scheduled task checks expiry every second.
- `authenticatingPlayers` map tracks players in auth. On successful auth, player leaves limbo and proceeds.

## Premium/cracked detection

### AuthMeReloaded
- Premium verification via Mojang session servers (in packetevents integration). 
- Proxy tracks verified premium UUIDs (`premiumVerificationManager.getVerifiedPremiumUuid`).
- Premium list sync: backend pushes premium usernames to proxy on PROXY_STARTED.
- Config: `premium.keepOfflineUuidCompatibility` controls UUID policy.

### KlyroLogin
- On pre-login, determines premium status. Returns FORCE_ONLINE for premium → Velocity sets online mode (Mojang UUID). Cracked → FORCE_OFFLINE (offline UUID).
- Integrates with Floodgate.

### LimboAuth
- `isPremium(username)` checks cache/lookup. Caches via `premiumCache`.
- For premium: forces online mode at Velocity PreLoginEvent. 
- Has pending login tracking during premium verification (detects if client falls back to offline). Floodgate API integration.
- Sets premium cache on verification result.

## UUID policy

- **Premium**: typically keep Mojang UUID (online mode). AuthMe has compatibility toggle to keep offline UUID if needed.
- **Cracked**: offline UUID (server-generated). LimboAuth forces offline mode for non-premium.
- KlyroLogin forces online/offline at PreLoginEvent → determines UUID source.
- All implementations normalize names case-insensitively for lookups.

## DB schema

### AuthMeReloaded (DataSource abstraction)
Tables include `authme` (users) with fields: id, username, realname, password, ip, lastlogin, x, y, z, world, email, isLogged, regdate, regip, lastloc, yaw, pitch, totp, uuid, salt (depending on version). Flexible schema.

### KlyroLogin
User DAO stores RegisteredPlayer with: username, uuid, password hash, ip, registration date, last login, premium status, etc.

### LimboAuth (ORMlite)
`RegisteredPlayer` entity: nickname (lowercase?), password (hash), ip, regDate, UUID, maybe 2FA fields? Stores password hash, registration timestamp, IP.

Common pattern: users table with username (case-insensitive lookup), uuid, password_hash, ip, timestamps. Index on username/uuid.

## Known failure modes & security issues (from references)

### AuthMeReloaded
- **Session takeover race**: if perform.login arrives after join starts, need guard to not schedule limbo (fixed in logic - checks isAuthenticated before limbo). Also pendingKick handling.
- **HMAC bypass risks**: empty shared secret → reject all perform.login with warning. Expired messages rejected. Invalid HMAC rejected.
- **ProxyStarted timing**: backend may not have players online to send premium list → logs warning. Handles config-phase receiver.
- **Premium list sync**: empty list is authoritative (clears stale). Must only send if premium enabled.
- **Channel registration**: tracks own registration to avoid conflicts.

### KlyroLogin
- IP registration limits (max accounts per IP). Cleanup of old registrations based on time window.
- Username validation (illegal chars, length). Case mismatch detection.
- PreLogin state handling prevents invalid flows.

### LimboAuth
- IP limit with time-based valid window (removes old IP entries). 
- Rate limiting per IP via `RATELIMITER`.
- Auth timeout disconnects players.
- Case mismatch kick if username case differs from registered.
- Premium verification fallback: if client disconnects during verification, marks as offline.
- Floodgate integration edge cases.

**Read issues/advisories conceptually**: AuthMe had historical session handling concerns - the 6.0.x code shows improved guards (checking authenticated state before limbo/kick). Key lesson: state can change between async pre-login and sync join due to proxy messages arriving.

## PHASE 2 - Threat Model (detailed)

See nexgate-spec.md for full table. Key points:
1. Direct backend access → firewall + backend auth gate (restrict all actions until authed)
2. Server switch escape → proxy enforces auth before allowing switch to protected servers; deny commands/chat on auth servers if not authed
3. Forged proxy messages → HMAC-SHA256 + timestamp + constant-time compare + one-time consume
4. Replay/fixation → short TTL (30s), token consumption, session expiry
5. Name spoofing/case → normalization + case validation
6. UUID collisions → enforce premium/cracked UUID policy
7. Brute force/stuffing → per-IP and per-account rate limits
8. Bot floods → early PreLogin denial, connection throttling
9. Hash leakage → Argon2id, salt per user, no plain storage
10. SQL injection → prepared statements only, async
11. Race conditions → state checks before actions, atomic updates, proper event priorities
12. Supply chain → pin versions, minimal deps, license review
```