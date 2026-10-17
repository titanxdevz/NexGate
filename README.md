# NexAuth

NexAuth (internal artifact prefix: `NexGate`) is an authentication plugin suite for Minecraft networks. It consists of a Velocity proxy gateway (`NexGate-Velocity`) and a Paper backend companion (`NexGate-Paper`).

## Description

NexAuth handles player authentication and network access control at the proxy layer before connections reach gameplay servers.

### What It Does

- Enforces authentication on the Velocity proxy before passing players to target backend servers.
- Hashes passwords using Argon2id with bounded concurrency to prevent CPU exhaustion attacks.
- Supports RFC 6238 TOTP two-factor authentication (`/2fa`) with single-use backup recovery codes.
- Provides an inventory-based numeric PIN pad interface (`/pin`) for GUI authentication.
- Automatically detects verified Mojang Java Edition accounts in `HYBRID` mode and enforces official online-mode encryption (`forceOnlineMode()`) to prevent username impersonation.
- Supports Bedrock Edition clients connecting via Geyser and Floodgate.
- Protects inter-server proxy-to-backend plugin messages using HMAC-SHA256 signatures with nonces and sliding-window replay protection.
- Filters inbound connections through an AntiBot admission pipeline supporting global rate limits, per-IP rate limits, IPv6 `/64` subnets, and automated mitigation mode.
- Provides player chat color and gradient formatting (`/color`) stored in SQLite, MySQL/MariaDB, or PostgreSQL.
- Enforces defense-in-depth restrictions on Paper backend servers (movement lock, blindness/slowness effects, godmode, and command suggestion filtering).

### What It Does Not Do

- Does not support standalone single-server Paper or Spigot deployments without a Velocity proxy.
- Does not support legacy BungeeCord or Waterfall proxies.
- Does not provide email, web portal, or out-of-band password reset mechanisms (recovery relies on TOTP recovery codes or operator commands).
- Does not promote offline accounts to Mojang Premium status based solely on username claims.
- Does not store passwords in plaintext or reversible formats.

## Supported Platforms

- **Proxy**: Velocity 3.3.0 or newer
- **Backend**: Paper / Purpur 1.20.4 or newer
- **Java Runtime**: OpenJDK / Temurin 21 or newer
- **Databases**: SQLite (default), MySQL / MariaDB (8.0+), PostgreSQL (12+)
- **Bedrock Integration**: Floodgate 2.0+ / Geyser (optional)

## Installation

### Velocity Proxy Setup

NexAuth operates on a network architecture where the proxy authenticates incoming connections and Paper backend servers enforce localized player restrictions.

1. **Configure Velocity Modern Forwarding**  
   In `velocity.toml`, set:
   ```toml
   player-info-forwarding-mode = "modern"
   forwarding-secret-file = "forwarding.secret"
   ```
   Start Velocity once to generate `forwarding.secret`.

2. **Configure Backend Paper Servers**  
   In each Paper server's `config/paper-global.yml`:
   ```yaml
   proxies:
     velocity:
       enabled: true
       online-mode: true
       secret: "<paste-forwarding-secret-here>"
   ```
   In `server.properties`, set:
   ```properties
   online-mode=false
   ```

3. **Install the Velocity Plugin**  
   Copy `NexGate-Velocity-1.0.0.jar` into your Velocity `plugins/` directory. Start the proxy to generate `plugins/nexgate/velocity-config.json`.

4. **Configure Secrets and Server Routing**  
   Open `plugins/nexgate/velocity-config.json` and configure a 32+ character `hmacSecret`, target server, and auth server:
   ```json
   {
     "auth": {
       "authServers": ["auth"],
       "targetServer": "lobby"
     },
     "security": {
       "hmacSecret": "your-random-shared-secret-minimum-32-chars"
     }
   }
   ```

5. **Install the Paper Companion**  
   Copy `NexGate-Paper-1.0.0.jar` into each backend Paper server's `plugins/` directory. Start each server to generate `plugins/NexGate/config.yml`. Configure the exact same `hmacSecret` in `plugins/NexGate/config.yml`:
   ```yaml
   security:
     hmacSecret: "your-random-shared-secret-minimum-32-chars"
   ```
   On authentication limbo servers, set `limbo.enabled: true`. On gameplay and lobby servers, set `limbo.enabled: false`.

6. **Firewall Backend Ports**  
   Block external traffic to backend Paper ports (e.g., using `ufw` or `iptables`) so clients cannot bypass the Velocity proxy:
   ```bash
   sudo ufw allow from <proxy-ip> to any port 25566 proto tcp
   ```

### Standalone Server Setup

NexAuth does **not** support standalone single-server deployments without Velocity. The authentication engine, database drivers, session store, rate limiters, and cryptographic subsystems execute within the Velocity proxy process. Backend Paper servers only run the companion plugin to receive signed verification messages from Velocity.

For full installation details, see [docs/install.md](docs/install.md).

## Configuration

NexAuth uses two primary configuration files:
- Proxy configuration: `plugins/nexgate/velocity-config.json`
- Backend configuration: `plugins/NexGate/config.yml`

### Velocity Configuration Summary (`velocity-config.json`)

| Section | Key Settings | Default | Description |
| :--- | :--- | :--- | :--- |
| `auth` | `authMode` | `"HYBRID"` | `"HYBRID"` (Mojang online mode for paid accounts, offline for others), `"ONLINE_ONLY"`, or `"OFFLINE_AUTH"`. |
| `auth` | `loginTimeoutSeconds` | `60` | Time in seconds before an unauthenticated player is kicked. |
| `auth` | `sessionTimeoutMinutes` | `120` | Duration an authenticated session remains valid across reconnects. |
| `security` | `hmacSecret` | (generated) | 32+ character shared secret for signing inter-server messages. |
| `security` | `rateLimitIp.max` | `5` | Maximum connections per IP within the rate limit window. |
| `security` | `ipCooldownEnabled` | `true` | Temporarily blocks IPs after repeated failed login kicks. |
| `security` | `passwordMinLength` | `6` | Minimum password length. |
| `antibot` | `enabled` | `true` | Enables connection admission filtering. |
| `antibot` | `mode` | `"AUTO"` | Operating mode: `"AUTO"`, `"ON"`, or `"OFF"`. |
| `totp` | `enabled` | `true` | Enables RFC 6238 Two-Factor Authentication. |
| `argon2` | `memoryKb` | `65536` | Memory parameter for Argon2id hashing (64 MiB). |
| `database` | `type` | `"sqlite"` | Storage backend: `"sqlite"` or `"mysql"`. |

For the complete parameter reference, see [docs/config.md](docs/config.md).

## Commands and Permissions

### Player Commands

| Command | Aliases | Description | Permission |
| :--- | :--- | :--- | :--- |
| `/register <password> <confirm>` | `/reg` | Register a new account. | None |
| `/login <password>` | `/l` | Authenticate an existing account. | None |
| `/changepassword <old> <new>` | `/changepass`, `/cp` | Change account password. | None |
| `/logout` | — | End session and return to auth server. | None |
| `/premium` | — | Initiate cryptographic ownership migration to Mojang online mode. | None |
| `/pin` | — | Open numeric inventory PIN pad for login. | None |
| `/2fa <subcommand>` | — | Manage 2FA (`setup`, `confirm`, `verify`, `recovery`, `disable`). | None |
| `/color <color\|gradient>` | — | Set chat message color or gradient formatting. | `nexgate.color` |

### Administrator Commands

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/nexgate reload` | Reload configuration files and message bundles. | `nexgate.admin.reload` |
| `/nexgate info <player>` | Inspect account status, registration date, IP, and 2FA status. | `nexgate.admin.info` |
| `/nexgate unregister <player>` | Delete account registration and revoke active sessions. | `nexgate.admin.unregister` |
| `/nexgate lock <player> [reason]` | Suspend an account from connecting. | `nexgate.admin.lock` |
| `/nexgate unlock <player>` | Restore access to a suspended account. | `nexgate.admin.lock` |
| `/nexgate forcelogin <player>` | Authenticate an online player without a password. | `nexgate.admin.forcelogin` |
| `/nexgate resetpassword <player> <new>` | Reset a player's password. | `nexgate.admin.resetpass` |
| `/nexgate audit [limit]` | View recent authentication audit logs. | `nexgate.admin.audit` |
| `/nexgate sessions` | List active sessions across the network. | `nexgate.admin.sessions` |
| `/nexgate antibot <status\|mode\|ban\|unban\|whitelist>` | Manage AntiBot mitigation rules. | `nexgate.admin.antibot` |
| `/setspawn` | Set Paper backend spawn position. | `nexgate.admin.setspawn` |
| `/spawn` | Teleport to configured backend spawn. | `nexgate.admin.setspawn` |

For full permission hierarchy and details, see [docs/commands-permissions.md](docs/commands-permissions.md).

## Building from Source

### Prerequisites

- Java Development Kit (JDK) 21 or newer
- Git 2.30 or newer

The repository includes the Gradle wrapper (`./gradlew`).

### Build Commands

```bash
# Clone the repository
git clone https://github.com/titanxdevz/NexGate.git
cd NexGate

# Run test suite
./gradlew test

# Run verification and compile shaded JARs
./gradlew check shadowJar
```

### Output Artifacts

Compiled JAR files are produced in the respective module build directories:
- Velocity Plugin: `nexgate-velocity/build/libs/NexGate-Velocity-1.0.0.jar`
- Paper Backend: `nexgate-paper/build/libs/NexGate-Paper-1.0.0.jar`
- Common Library: `nexgate-common/build/libs/NexGate-Common-1.0.0.jar`

For detailed build and test cluster instructions, see [docs/build.md](docs/build.md).

## Security

- **Password Hashing**: Passwords are hashed with Argon2id using an isolated, bounded executor pool (`maxConcurrent: 2`) to eliminate thread and memory starvation under registration bursts.
- **Inter-Server Verification**: Proxy-to-backend messages contain HMAC-SHA256 signatures, timestamps, and nonces evaluated against a sliding-window replay protection cache. Constant-time comparisons (`MessageDigest.isEqual`) prevent timing side channels.
- **Connection Filtering**: AntiBot filters incoming connection spikes, enforces per-IP and per-subnet thresholds, and drops abusive connections before processing handshakes.
- **Reporting Vulnerabilities**: For instructions on responsible disclosure, see [SECURITY.md](SECURITY.md).

## License

NexAuth is open-source software licensed under the [MIT License](LICENSE).