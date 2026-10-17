# Changelog

All notable changes to the NexGate project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-10-13

### Added
- **Proxy Authentication Suite (`nexgate-velocity`)**:
  - Full proxy-level gating of unauthenticated connections before reaching backend servers.
  - Interactive clickable chat prompts (`[ 🔑 CLICK TO LOGIN ]`, `[ 📝 CLICK TO REGISTER ]`).
  - Dynamic countdown BossBar with color transitions (Green ➔ Yellow ➔ Red).
  - Title and subtitle animations with MiniMessage gradients.
  - Action bar heartbeat notifications and sound effect cues on login, session restore, and errors.
  - Complete command suite: `/login`, `/register`, `/changepassword`, `/logout`, `/color`, `/premium`, `/nexgate`.
- **In-Game Chat Color Customization (`/color`)**:
  - Interactive clickable chat palette menu with hover tooltips and instant selection.
  - 18 solid colors and 7 premium gradients (Sunset, Cyberpunk, Aurora, Ocean, Neon, Flame, Rainbow).
  - Custom hex support (`#RRGGBB`).
  - Text formatting options: Bold, Italic, Underlined, and Default.
  - `/color preview <message>` command to preview formatting.
  - Persistent database storage and real-time synchronization between proxy and backend servers.
- **Deep Enterprise Security Engine**:
  - Modern memory-hard Argon2id password hashing (64MB RAM, 3 iterations, 1 parallelism).
  - Password strength validator with length bounds, common passwords blacklist (>100 common passwords), username containment checking, and entropy rules.
  - HMAC-SHA256 message signing with nonces and millisecond timestamps for all inter-server messages (`perform.login`, `sync.settings`).
  - Sliding-window replay protection cache on backend (30s TTL).
  - Per-IP and per-account rate limiters with progressive lockouts.
  - Maximum accounts per IP limit enforcement (`maxAccountsPerIp`).
  - Strict Mojang username regex (`^[a-zA-Z0-9_]{3,16}$`) and case mismatch prevention.
- **Deep Session System**:
  - Auto-login sessions stored securely in `nexgate_sessions`.
  - Dual IP validation: strict IP match or subnet-friendly mode (`/24` CIDR match).
  - Automatic session invalidation upon `/logout`, password change, or admin unregister.
- **Deep Mojang Premium Verification (Hybrid Auth)**:
  - Asynchronous Mojang session server profile checker.
  - Proxy online-mode encryption enforcement for verified accounts (`forceOnlineMode()`) preventing name theft and bypassing passwords.
  - `/premium` migration command for players upgrading to paid Minecraft accounts.
- **Backend Defense-in-Depth (`nexgate-paper`)**:
  - Complete event gating: blocks movement, interactions, attacks, inventory clicks, item drops/pickups, and commands.
  - Godmode: unauthenticated players neither take nor deal damage.
  - Potion barriers: applies blindness and slowness level 255; removed immediately on login.
  - Ghosting: hides unauthenticated players from other online players.
  - Void fall protection: teleports unauthenticated players falling into the void back to spawn.
  - Formats Paper chat messages (`AsyncChatEvent`) with player's active color and decoration.
- **Database Backend**:
  - Zero-config SQLite support (`nexgate.db`) out-of-the-box.
  - MySQL / MariaDB support with HikariCP connection pooling and automatic schema migrations.
- **GitHub Automation**:
  - GitHub Actions CI workflow (`.github/workflows/build.yml`) for building and testing.
  - GitHub Actions Release workflow (`.github/workflows/release.yml`) for packaging and publishing releases.
  - Issue templates, PR template, and Dependabot configuration.
