# NexGate - Enterprise Proxy Authentication & Security Suite

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Java Version](https://img.shields.io/badge/Java-21%2B-blue)]()
[![Velocity](https://img.shields.io/badge/Velocity-3.4.0-purple)]()
[![Paper](https://img.shields.io/badge/Paper-1.21.1-red)]()

**NexGate** is an enterprise-grade Minecraft authentication and defense-in-depth security suite designed for Velocity proxies and Paper backends. It features modern Kyori Adventure UI/UX, player chat message color personalization, industrial Argon2id cryptography, anti-replay HMAC-signed messaging, auto-login session persistence, and hybrid Mojang Premium auto-verification.

---

## 🌟 Key Features

### 🎨 In-Game UI / UX (Modern Adventure & MiniMessage)
- **Interactive Clickable Chat Prompts**:
  - `[ 🔑 CLICK TO LOGIN ]` suggests `/login ` with hover tooltips.
  - `[ 📝 CLICK TO REGISTER ]` suggests `/register ` with hover tooltips.
- **Dynamic Countdown BossBar**:
  - Live timer ticking down remaining authentication time.
  - Automatically shifts color dynamically: Green -> Yellow -> Red (< 15s).
- **Animated Titles & Subtitles**:
  - Gradient branded headers (`<gradient:#00C9FF:#92FE9D>NexGate</gradient>`).
  - Contextual subtitles indicating whether to `/login` or `/register`.
- **Action Bar Heartbeat**:
  - Subtle warnings informing unauthenticated players of movement and chat locks.
- **Auditory Cues**:
  - Level-up chime on successful login and session restore.
  - Bass error buzz on wrong password or security rejections.
  - Subtle click sound on prompts.

---

### 🌈 User Chat Message Color & Styling Customization
Players can customize their in-game chat message appearance across proxy and backend servers:
- **Interactive Chat Palette Menu**: Running `/color` opens an interactive chat palette where players can click any color swatch or gradient to instantly apply it.
- **Vibrant Solid Colors**: `WHITE`, `GRAY`, `RED`, `CRIMSON`, `CORAL`, `GOLD`, `YELLOW`, `GREEN`, `EMERALD`, `MINT`, `AQUA`, `CYAN`, `BLUE`, `LIGHT_PURPLE`, `PURPLE`, `PINK`, `ROSE`.
- **Premium Gradients**:
  - `SUNSET`: `#FF512F` → `#DD2476`
  - `CYBERPUNK`: `#00F260` → `#0575E6`
  - `AURORA`: `#7F00FF` → `#E100FF`
  - `OCEAN`: `#2B5876` → `#4E4376`
  - `NEON`: `#12C2E9` → `#C471ED` → `#F64F59`
  - `FLAME`: `#F12711` → `#F5AF19`
  - `RAINBOW`: Full spectrum rainbow text.
- **Custom Hex Codes**: Direct input support like `/color #FF55AA`.
- **Text Styles**: `/color style BOLD`, `/color style ITALIC`, `/color style UNDERLINED`, `/color style DEFAULT`.
- **Live Preview**: `/color preview <message>` to test formatting before chatting.
- **Persistent Storage**: Saved in the database and automatically synced between Velocity and Paper backends.

---

### 🛡️ Deep Security Suite
- **Argon2id Password Hashing**:
  - Memory-hard cryptography (64MB RAM, 3 iterations) resistant to GPU/ASIC cracking.
  - Constant-time verification.
- **Enterprise Password Policy**:
  - Configurable minimum/maximum length.
  - Curated blacklist of over 100 commonly guessed passwords (`password`, `123456`, `qwerty`, `admin`, etc.).
  - Username containment check (disallows passwords containing the player's username).
  - Optional complexity checks for digits and special characters.
- **HMAC-SHA256 Inter-Server Communication with Replay Defense**:
  - Proxy-to-backend messages (`perform.login`, `sync.settings`) are signed using HMAC-SHA256 with a shared secret.
  - Each message includes a unique cryptographically secure nonce and millisecond timestamp.
  - Backend maintains a sliding replay protection cache (30s window) to reject replayed packets and stale messages.
- **Rate Limiting & Brute Force Lockouts**:
  - Per-IP connection and registration limits.
  - Per-account failed login attempt tracking with exponential cooldown lockouts.
  - Maximum accounts per IP limit (`maxAccountsPerIp`).
- **Anti-Spoofing & Username Integrity**:
  - Strict regex enforcement: `^[a-zA-Z0-9_]{3,16}$`.
  - Case validation prevents case-spoofing (e.g. `steve` vs `StEvE`).

---

### 🔄 Deep Session System
- **Auto-Login Sessions**:
  - Generates secure random session tokens stored in `nexgate_sessions`.
  - Configurable timeout (e.g. 120 minutes or custom).
  - Dual IP validation: strict IP match or subnet-friendly mode (`/24` CIDR match).
  - Seamless auto-login on rejoin without asking for password.
  - Automatic session invalidation on `/logout`, password change, or admin unregister.

---

### 🌐 Deep Mojang Premium Check (Hybrid Auth)
- **Automatic Mojang Profile Lookup**:
  - Asynchronously queries official Mojang session servers (`https://api.mojang.com/users/profiles/minecraft/<username>`).
  - Cached in-memory to prevent API rate-limiting.
- **Proxy-Level Encryption & Online Mode**:
  - Verified Mojang Java accounts are placed into Velocity's `forceOnlineMode()` during `PreLoginEvent`.
  - Authenticated directly by Mojang encryption keys—premium players never need to type passwords and cannot have their names stolen by cracked clients.
  - Non-premium / cracked players are handled via offline mode with Argon2id auth gating.
- **`/premium` Migration Command**:
  - Allows an existing player who bought official Minecraft to migrate their account to official Mojang auto-login.

---

### 🏰 Backend Defense-in-Depth (Paper Companion)
- **Strict Gating**: Blocks X/Y/Z movement, world interaction, block place/break, item pickup/drop, inventory clicks, and vehicle interaction for unauthenticated players.
- **Godmode**: Unauthenticated players cannot take damage from mobs or players, nor deal damage.
- **Blindness & Slowness Barriers**: Infinite blindness and slowness potion effects applied while unauthenticated, stripped immediately upon login.
- **Player Ghosting**: Unauthenticated players are hidden from other online players to prevent clutter around spawn gates.
- **Void Fallback**: Automatically teleports unauthenticated players back to spawn if they fall into the void.

---

## 📜 Commands & Permissions

### Player Commands
| Command | Aliases | Description | Permission |
| :--- | :--- | :--- | :--- |
| `/register <pass> <confirm>` | `/reg` | Register a new account | Default |
| `/login <password>` | `/l` | Sign in to an existing account | Default |
| `/changepassword <old> <new>` | `/cp` | Change account password | Default |
| `/logout` | — | Sign out and invalidate session | Default |
| `/color [color/style/preview]` | `/msgcolor`, `/chatcolor` | Open color GUI or choose message color | `nexgate.color` |
| `/premium` | — | Verify Mojang profile & enable auto-login | Default |

### Administrator Commands
| Command | Aliases | Description | Permission |
| :--- | :--- | :--- | :--- |
| `/nexgate info` | `/ng info` | View system health, sessions, and DB status | `nexgate.admin` |
| `/nexgate reload` | `/ng reload` | Reload configuration files | `nexgate.admin` |
| `/nexgate lookup <player>` | `/ng lookup` | Inspect account registration, IP, and premium state | `nexgate.admin` |
| `/nexgate unregister <player>` | `/ng unregister` | Delete user and wipe active sessions | `nexgate.admin` |
| `/nexgate forcelogin <player>` | `/ng forcelogin` | Force-authenticate an online player | `nexgate.admin` |
| `/nexgate setpremium <player> <bool>` | `/ng setpremium` | Toggle player's Mojang premium flag | `nexgate.admin` |
| `/nexgate setcolor <player> <color>` | `/ng setcolor` | Assign custom message color to player | `nexgate.admin` |

---

## ⚙️ Configuration

### Velocity (`velocity-config.json`)
```json
{
  "auth": {
    "authServers": ["auth"],
    "targetServer": "lobby",
    "loginTimeoutSeconds": 60,
    "sessionTimeoutMinutes": 120,
    "sessionStrictIp": false,
    "requireAuth": true,
    "showBossBar": true,
    "showTitle": true,
    "playSounds": true
  },
  "security": {
    "hmacSecret": "<auto-generated-32-byte-token>",
    "rateLimitIp": { "max": 5, "windowSeconds": 60 },
    "rateLimitAccount": { "max": 5, "windowSeconds": 300 },
    "maxAccountsPerIp": 3,
    "lockoutDurationSeconds": 300,
    "passwordMinLength": 6,
    "passwordMaxLength": 64,
    "requireNumbers": false,
    "requireSpecialChars": false
  },
  "premium": {
    "enabled": true,
    "autoDetect": true,
    "forceOnlineModeForPremium": true,
    "mojangTimeoutSeconds": 3,
    "cacheDurationMinutes": 60
  },
  "database": {
    "type": "sqlite",
    "sqliteFile": "plugins/nexgate/nexgate.db",
    "host": "localhost",
    "port": 3306,
    "database": "nexgate",
    "username": "nexgate",
    "password": "password"
  }
}
```

### Paper (`config.yml`)
```yaml
# Shared secret with Velocity proxy - MUST match velocity-config.json exactly!
hmac-secret: "<auto-generated-32-byte-token>"

strict-gating: true
apply-blindness: true
apply-slowness: true
hide-unauthenticated-players: true
allow-chat-color-formatting: true
chat-format: "<gray><player></gray> <dark_gray>»</dark_gray> <message>"
```

---

## 🔨 Building from Source

Requirements:
- JDK 21 or newer (compatible with JDK 21 through 27)

```bash
# Build all subprojects, run unit tests, and assemble shadow JARs
./gradlew build shadowJar
```

Artifacts will be produced at:
- `nexgate-velocity/build/libs/nexgate-velocity-1.0.0-SNAPSHOT.jar`
- `nexgate-paper/build/libs/nexgate-paper-1.0.0-SNAPSHOT.jar`
- `nexgate-common/build/libs/nexgate-common-1.0.0-SNAPSHOT.jar`