# Commands and Permissions Reference

## Player Commands

All player commands are available to standard players without additional permissions.

### Authentication Commands

| Command | Aliases | Description |
| :--- | :--- | :--- |
| `/register <password> <confirm>` | `/reg` | Register a new offline account with a password. |
| `/login <password>` | `/l` | Authenticate an existing account. |
| `/changepassword <old> <new>` | `/changepass`, `/cp` | Change account password. |
| `/logout` | — | Terminate the current active session and return to the authentication server. |
| `/premium` | — | Initiate cryptographic ownership migration for accounts with an official paid Minecraft profile. |
| `/pin` | — | Open the numeric inventory PinPad for password or PIN entry. |

### Two-Factor Authentication (`/2fa`)

| Subcommand | Description |
| :--- | :--- |
| `/2fa setup` | Generate a new TOTP secret key and QR/OTP configuration link. |
| `/2fa confirm <code>` | Verify a 6-digit code to complete 2FA setup and receive backup recovery codes. |
| `/2fa verify <code>` | Authenticate using a 6-digit code during login. |
| `/2fa recovery <code>` | Authenticate using a single-use backup recovery code. |
| `/2fa disable <password> <code>` | Disable Two-Factor Authentication on your account. |

### Chat Styling (`/color`)

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/color` | `nexgate.color` | Opens the interactive chat color palette and style picker. |
| `/color <color>` | `nexgate.color` | Sets message text color (e.g. `/color cyan` or `/color #FF55AA`). |
| `/color gradient <name>` | `nexgate.color` | Applies a gradient style (e.g. `sunset`, `cyberpunk`, `aurora`, `rainbow`). |

---

## Administrator Commands (`/nexgate`)

Administrator commands require the `nexgate.admin` base permission or specific sub-permissions.

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/nexgate reload` | `nexgate.admin.reload` | Reloads configuration files and message bundles. |
| `/nexgate info <player>` | `nexgate.admin.info` | View detailed account data, registration date, IP, and 2FA status. |
| `/nexgate unregister <player>` | `nexgate.admin.unregister` | Deletes an account registration and invalidates active sessions. |
| `/nexgate lock <player> [reason]` | `nexgate.admin.lock` | Suspends an account from authenticating or connecting. |
| `/nexgate unlock <player>` | `nexgate.admin.lock` | Restores a suspended account. |
| `/nexgate forcelogin <player>` | `nexgate.admin.forcelogin` | Bypasses password prompt and authenticates an online player. |
| `/nexgate resetpassword <player> <new>` | `nexgate.admin.resetpass` | Overwrites a player's password with a new hash. |
| `/nexgate audit [limit]` | `nexgate.admin.audit` | Displays recent security audit logs (logins, kicks, admin actions). |
| `/nexgate sessions` | `nexgate.admin.sessions` | Displays active authenticated sessions across the network. |

### AntiBot Management

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/nexgate antibot status` | `nexgate.admin.antibot` | Displays live traffic counters, active attacks, and bot filter statistics. |
| `/nexgate antibot mode <auto\|on\|off>` | `nexgate.admin.antibot` | Overrides AntiBot mitigation mode. |
| `/nexgate antibot whitelist <ip/cidr>` | `nexgate.admin.antibot` | Adds an IP or CIDR subnet to the connection allowlist. |
| `/nexgate antibot ban <ip/cidr>` | `nexgate.admin.antibot` | Permanently blocks an IP or CIDR subnet from connecting. |
| `/nexgate antibot unban <ip>` | `nexgate.admin.antibot` | Removes an IP from the blacklist. |

### Backend Spawn Commands

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/setspawn` | `nexgate.admin.setspawn` | Sets the spawn point on the current Paper server for login routing. |
| `/spawn` | `nexgate.admin.setspawn` | Teleports player to the configured backend spawn location. |

---

## Permission Nodes

| Permission Node | Default | Description |
| :--- | :--- | :--- |
| `nexgate.color` | `true` | Allows players to use the `/color` chat styling command. |
| `nexgate.admin` | `op` | Master permission granting access to all administrator commands. |
| `nexgate.admin.reload` | `op` | Allows reloading plugin configurations. |
| `nexgate.admin.info` | `op` | Allows inspecting player account profiles. |
| `nexgate.admin.unregister` | `op` | Allows deleting player registrations. |
| `nexgate.admin.lock` | `op` | Allows administratively locking and unlocking accounts. |
| `nexgate.admin.forcelogin` | `op` | Allows forcing authentication on online players. |
| `nexgate.admin.resetpass` | `op` | Allows resetting player passwords. |
| `nexgate.admin.audit` | `op` | Allows reading security audit logs. |
| `nexgate.admin.sessions` | `op` | Allows viewing network session tables. |
| `nexgate.admin.antibot` | `op` | Allows managing the AntiBot connection filter. |
| `nexgate.admin.setspawn` | `op` | Allows setting and teleporting to backend spawn coordinates. |
