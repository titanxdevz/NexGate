# Installation Guide

NexAuth operates as a distributed Minecraft authentication suite designed for proxy networks. It consists of two components:
1. **Velocity Proxy Plugin (`NexGate-Velocity-1.0.0.jar`)**: Runs on your Velocity proxy and handles player connections, credentials, database operations, session tracking, and rate limiting.
2. **Paper Backend Plugin (`NexGate-Paper-1.0.0.jar`)**: Runs on all backend Paper servers (auth limbo, lobby, survival, etc.) to enforce movement restrictions, visual gates, and inter-server communication.

## System Requirements

- **Proxy**: Velocity 3.3.0 or newer.
- **Backend**: Paper / Purpur 1.20.4 or newer.
- **Java**: Java Runtime Environment (JRE) 21 or newer.
- **Database**: SQLite (built-in default) or MySQL/MariaDB 8.0+.

## Network Architecture

```
[ Minecraft Client ]
         │
         ▼
[ Velocity Proxy ] (NexGate-Velocity)
         │ (Modern Player Forwarding + HMAC Plugin Messages)
         ├───────────────────────────────┐
         ▼                               ▼
[ Paper Auth Limbo ]            [ Paper Lobby / Survival ]
 (NexGate-Paper)                 (NexGate-Paper)
```

## Step-by-Step Installation

### Step 1: Configure Velocity Modern Forwarding

Modern player forwarding is mandatory for security. It ensures backend servers receive the player's real UUID, skin data, and IP address.

1. Open your Velocity configuration file (`velocity.toml`):
   ```toml
   player-info-forwarding-mode = "modern"
   forwarding-secret-file = "forwarding.secret"
   ```
2. Start Velocity once to generate `forwarding.secret`.
3. Copy the secret from `forwarding.secret` to your Paper servers:
   - In Paper's `config/paper-global.yml`:
     ```yaml
     proxies:
       velocity:
         enabled: true
         online-mode: true
         secret: "<paste-forwarding-secret-here>"
     ```
   - In Paper's `server.properties`:
     ```properties
     online-mode=false
     ```

### Step 2: Install the Velocity Plugin

1. Download or compile `NexGate-Velocity-1.0.0.jar`.
2. Place the JAR file into your Velocity `plugins/` directory:
   ```bash
   cp NexGate-Velocity-1.0.0.jar /path/to/velocity/plugins/
   ```
3. Start or reload Velocity. This generates the default configuration file:
   `plugins/nexgate/velocity-config.json`
4. Set a unique, high-entropy HMAC secret in `velocity-config.json`:
   ```json
   "security": {
     "hmacSecret": "generate-a-secure-random-token-minimum-32-chars"
   }
   ```
5. Configure target servers and authentication servers:
   ```json
   "auth": {
     "authServers": [ "auth" ],
     "targetServer": "lobby"
   }
   ```

### Step 3: Install the Paper Companion Plugin

1. Place `NexGate-Paper-1.0.0.jar` into each backend Paper server's `plugins/` directory:
   ```bash
   cp NexGate-Paper-1.0.0.jar /path/to/paper-auth/plugins/
   cp NexGate-Paper-1.0.0.jar /path/to/paper-lobby/plugins/
   ```
2. Start or reload the Paper servers. This generates `plugins/NexGate/config.yml`.
3. Open `plugins/NexGate/config.yml` on every Paper server and configure the matching `hmacSecret`:
   ```yaml
   security:
     hmacSecret: "same-secret-as-in-velocity-config-json"
   ```
4. For the **Auth Limbo** server:
   ```yaml
   limbo:
     enabled: true
     enforceCage: true
     blindness: true
     slowness: true
   ```
5. For **Lobby / Game** servers:
   ```yaml
   limbo:
     enabled: false
     enforceCage: false
   ```

### Step 4: Firewall Configuration

Backend Paper servers must not accept direct connections from public IP addresses. Configure your firewall (such as `iptables`, `ufw`, or security groups) so that Paper server ports accept traffic **only** from the Velocity proxy IP address:

```bash
# Example UFW rule allowing only proxy IP (e.g. 10.0.0.1) on Paper port 25566
sudo ufw allow from 10.0.0.1 to any port 25566 proto tcp
```

---

## Standalone Server Setup (Single-Server)

NexAuth does **not** support running on standalone Spigot, Paper, or Purpur servers without a Velocity proxy.

### Why Standalone Is Not Supported

- **Proxy-Based Architecture**: The core authentication pipeline, password database connections, rate limiters, session tracking, and AntiBot mitigation are implemented exclusively inside the `NexGate-Velocity` proxy plugin.
- **Backend Role**: The `NexGate-Paper` plugin serves as a companion security barrier (gating movement, potion effects, and PinPad UI) that receives cryptographic authentication instructions from Velocity via modern plugin messaging.
- **Network Requirement**: If you run a single Minecraft server, you must still deploy a local Velocity proxy in front of it and connect the server via Velocity modern player forwarding.

