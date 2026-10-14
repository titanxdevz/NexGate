# NexGate Administrative Commands & Permissions Manual

## 1. Permission Hierarchy

NexGate enforces granular permission nodes for all administrative actions:

```
nexgate.admin.*                      (Master wildcard for all administrative features)
  ├── nexgate.admin.lookup           (View detailed player account data and IP logs)
  ├── nexgate.admin.reload           (Reload configuration file at runtime)
  ├── nexgate.admin.lock             (Suspend and terminate player accounts)
  ├── nexgate.admin.unlock           (Restore account access and clear lockouts)
  ├── nexgate.admin.resetpassword    (Force reset player password and revoke sessions)
  ├── nexgate.admin.sessions         (List active authenticated sessions)
  ├── nexgate.admin.audit            (Query structured security audit trails)
  ├── nexgate.admin.antibot          (Manage AntiBot mitigation, mode, bans, and whitelist)
  ├── nexgate.admin.unregister       (Purge player account and active sessions)
  ├── nexgate.admin.forcelogin       (Bypass auth gating for target player)
  └── nexgate.admin.setcolor         (Assign custom chat message colors to players)
```

---

## 2. Command Reference

### Player Commands
| Command | Aliases | Description |
| :--- | :--- | :--- |
| `/login <password>` | `/l` | Authenticates existing registered account. |
| `/register <password> <confirm>`| `/reg` | Registers a new account with Argon2id hashing. |
| `/changepassword <old> <new>` | `/cp` | Updates password and revokes all other active sessions. |
| `/logout` | — | Terminates active session and returns player to auth limbo. |
| `/premium` | — | Initiates cryptographic migration to official Mojang auto-login. |
| `/2fa setup` | `/totp setup` | Initiates RFC 6238 TOTP enrollment and displays secret/URI. |
| `/2fa confirm <code>` | `/totp confirm` | Confirms enrollment with 6-digit code and outputs 8 recovery codes. |
| `/2fa verify <code>` | `/totp verify` | Submits 6-digit authenticator code during login challenge. |
| `/2fa recovery <code>` | `/totp recovery`| Redeems a single-use backup recovery code. |
| `/2fa disable <pass> <code>` | `/totp disable` | Disables 2FA on account after dual authentication. |
| `/color` | `/msgcolor` | Opens interactive chat palette menu for color/style customization. |
| `/color <name\|#hex>` | — | Sets message text color or gradient. |
| `/color style <style>` | — | Sets text style (`BOLD`, `ITALIC`, `UNDERLINED`, `DEFAULT`). |
| `/color preview <message>` | — | Previews message formatting before chatting. |

### Administrative Commands (`/nexgate` or `/ng`)
| Command | Usage | Description |
| :--- | :--- | :--- |
| `/nexgate info` | `/ng info` | View system health, online session metrics, and DB type. |
| `/nexgate reload` | `/ng reload` | Reloads `velocity-config.json` without proxy restart. |
| `/nexgate lookup <player>` | `/ng lookup Steve` | Inspect player registration, last login, IP history, and MFA state. |
| `/nexgate lock <player> [reason]`| `/ng lock Steve Botting` | Administratively suspends account, disconnects player, and revokes sessions. |
| `/nexgate unlock <player>` | `/ng unlock Steve` | Restores account access and clears all rate-limit lockout counters. |
| `/nexgate resetpassword <player> <pass>` | `/ng resetpassword Steve NewP@ss123` | Force resets password and writes audit entry. |
| `/nexgate sessions` | `/ng sessions` | Lists all active authenticated sessions with expiration timers. |
| `/nexgate audit [player] [limit]` | `/ng audit Steve 15` | Displays recent security audit logs (lockouts, 2FA, password changes). |
| `/nexgate antibot <subcommand>`| `/ng antibot status` | AntiBot management (see AntiBot Tuning Guide). |
| `/nexgate unregister <player>`| `/ng unregister Steve` | Purges account from database and kicks player back to registration. |
| `/nexgate forcelogin <player>`| `/ng forcelogin Steve` | Bypasses auth gate for an online player. |
| `/nexgate setpremium <player> <true\|false>` | `/ng setpremium Steve true` | Toggles player's premium status. |
| `/nexgate setcolor <player> <color>` | `/ng setcolor Steve GOLD` | Sets player's chat color preference. |
