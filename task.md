# NexGate Production-Grade Overhaul Task Plan

## Phase A — Audit & Baseline
- [x] Inspect complete codebase, Gradle build, subprojects, dependencies, configs, test suites, and scripts.
- [x] Identify confirmed vulnerabilities, missing features, and architectural risks.
- [x] Document vulnerability inventory with reproduction scenarios and root causes.
- [x] Create baseline implementation plan in `task.md`.

## Phase B — Premium Authentication & Ownership Verification (Highest Priority)
- [ ] Implement explicit authentication modes: `ONLINE_ONLY`, `OFFLINE_AUTH`, `HYBRID` with strict routing policies.
- [ ] Overhaul `PreLoginEvent` to use asynchronous handling (Velocity `EventTask.withContinuation`) — never block Netty event loop with `.join()`.
- [ ] Strict ownership verification:
  - Never promote player to Premium based on Mojang username lookup, client profile claim, or UUID resemblance.
  - In `HYBRID` mode: Check registration and ownership binding.
  - Ensure failed Premium authentication NEVER falls back to offline authentication for the same account.
  - Fail closed on Mojang API / session server errors and timeouts — never allow outages to become offline bypasses.
  - Prevent offline accounts from hijacking existing Premium identities.
  - Secure account linking: `/premium` must require cryptographic proof via official online mode re-authentication, never an unauthenticated username lookup.
- [ ] Add Floodgate / Geyser compatibility: recognize Bedrock players, allow Floodgate prefix, never classify Bedrock accounts as Java Premium.
- [ ] Build comprehensive unit and integration tests for all Premium verification flows, race conditions, case changes, and failure modes.

## Phase C — Core Authentication & Password Security
- [ ] Upgrade password hashing architecture:
  - Bounded Argon2id executor with concurrency throttle / semaphore to prevent CPU/memory exhaustion DoS.
  - Constant-time verification, secure salt generation, parameter validation.
  - Configurable Argon2id parameters (iterations, memory, parallelism).
- [ ] Implement RFC 6238 TOTP Two-Factor Authentication (2FA):
  - Secret generation, QR/URI generation, TOTP verification with time drift window.
  - Cryptographically secure hashed backup recovery codes (single-use).
  - Commands: `/2fa setup`, `/2fa confirm <code>`, `/2fa verify <code>`, `/2fa disable <password> <code>`, `/2fa recovery <code>`.
  - Rate limiting on TOTP and recovery code attempts.
- [ ] Secure password change and recovery:
  - Atomic password updates.
  - Invalidate all active sessions on password change or account reset.
  - Administrator password reset with one-time tokens and audit logging.
- [ ] Username validation & case-sensitivity enforcement:
  - Support configurable username regex (including Floodgate prefix).
  - Strict case matching against registered username to prevent case-variation attacks.
- [ ] Account lockout with exponential delay and bounded tracking.

## Phase D — Proxy-to-Paper Trust & Secure Messaging
- [ ] Overhaul message authentication protocol:
  - Include protocol version, message type, sender identity, player UUID, player username, nonce, timestamp, and payload in HMAC-SHA256 signature.
  - Constant-time MAC comparison (`MessageDigest.isEqual`).
  - Strict nonce deduplication with bounded sliding window `ReplayProtectionCache`.
  - Clock skew enforcement (configurable max age, e.g. 15-30 seconds).
  - Authenticate ALL privileged messages: `PERFORM_LOGIN`, `SYNC_SETTINGS`, `LOGOUT`, `FORCE_DISCONNECT`.
- [ ] Fix Paper-side authentication verification:
  - Check player UUID AND exact username match against backend entity.
  - Verify that the connection came from trusted Velocity modern forwarding.
- [ ] Complete Paper-side gating:
  - Comprehensive event listener intercepting: block break/place, interact, entity interact, armor stands, drops, pickups, inventories, containers, crafting, damage, combat, books, signs, vehicles, portals, bed enter, mob targeting.
  - Filter command suggestions and tab completion (`PlayerCommandSendEvent`) for unauthenticated players.
  - Whitelist only permitted authentication commands (`/login`, `/register`, `/l`, `/reg`, `/2fa`, etc.).

## Phase E — Production-Grade AntiBot & Connection-Flood Mitigation
- [ ] Design and implement multi-stage connection admission pipeline:
  1. Connection acceptance & handshake validation (protocol version checks, hostname sanity, malformed packet rejection).
  2. Bounded rate limiters: global rate, per-IP rate, and IPv6 /64 prefix rate limiting.
  3. Concurrent connections per IP / prefix limits.
  4. Connection admission queue / throttle during bursts.
  5. Automatic Under-Attack / Mitigation mode with operator manual override.
  6. Fast reconnect / bot loop detection.
  7. IP and CIDR allowlist and blocklist management.
- [ ] Bounded data structures with LRU eviction and memory caps to prevent OutOfMemory under flood attacks.
- [ ] Operator AntiBot management commands: `/nexgate antibot status`, `/nexgate antibot mode <auto|on|off>`, `/nexgate antibot ban <ip/cidr>`, `/nexgate antibot unban <ip>`, `/nexgate antibot whitelist <ip/cidr>`.
- [ ] Structured security metrics and attack counters (connections rejected, packets dropped, rate-limited, active challenge).

## Phase F — Feature Parity (LimboAuth & AuthMeReloaded) & Administration
- [ ] Administrative tools:
  - Account inspection, lock/unlock, unregister, force-login, reset-password, sessions list, audit logs.
  - Strict permission nodes (`nexgate.admin.*`).
  - Audit logger writing structured security events to log file and administrative console.
- [ ] Multi-language / localization support:
  - Configurable message bundles with MiniMessage formatting.
- [ ] Quality-of-life:
  - Configurable titles, bossbars, actionbars, chat colors, and sounds.

## Phase G — Database Architecture, Concurrency & Reliability
- [ ] Versioned database migrations (`nexgate_schema_version` table) with safe transactional DDL/DML.
- [ ] Support SQLite, MySQL/MariaDB, and PostgreSQL.
- [ ] Concurrency safety: atomic database updates, connection pooling timeout configuration, resource cleanup.
- [ ] Multi-proxy session coordination: shared database sessions with consistent revocation.
- [ ] Synchronous table initialization at plugin start before registering network listeners.
- [ ] Graceful shutdown with bounded executor termination.

## Phase H — Verification, Release Quality & Documentation
- [ ] Comprehensive unit and integration test suite covering:
  - Premium login verification, offline fallback rejection, forged claims.
  - Password hashing, rate limiting, TOTP, recovery codes.
  - HMAC signing, replay attacks, timestamp skew, cross-message tampering.
  - AntiBot pipeline, IPv6 /64 CIDR rate limiting, admission queue.
  - Paper-side event gating.
  - Database migrations and concurrency.
- [ ] Local test network validation (`minecraft-server/start.sh`).
- [ ] Complete documentation:
  - Architecture and security specification.
  - Configuration reference.
  - Migration guide.
  - AntiBot tuning guide.
  - Administrator command & permission manual.
- [ ] Final engineering report.
