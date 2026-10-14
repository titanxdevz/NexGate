# NexGate AntiBot & Flood Mitigation Tuning Guide

## 1. Overview

NexGate incorporates a Sonar-class multi-stage connection admission pipeline to protect Minecraft networks from Layer 7 bot attacks, TCP connection floods, and authentication resource starvation.

---

## 2. Admission Pipeline Mechanics

```
Incoming Connection (PreLogin)
              │
              ▼
   [ 1. Is Whitelisted? ] ──► Yes ──► ALLOW (Bypass all limits)
              │ No
              ▼
   [ 2. Is Blacklisted? ] ──► Yes ──► DROP & DENY
              │ No
              ▼
   [ 3. Under Temporary Ban? ] ──► Yes ──► DENY (Show remaining seconds)
              │ No
              ▼
   [ 4. Fast Reconnect Burst? ] ──► Yes ──► APPLY 10s BAN & DENY
        (<1200ms disconnect/reconnect)
              │ No
              ▼
   [ 5. Conns/Sec > Threshold? ] ──► Yes ──► ENGAGE UNDER-ATTACK MODE
              │
              ▼
   [ 6. Global Rate Limit OK? ] ──► No ──► DENY ("Gateway Congested")
              │ Yes
              ▼
   [ 7. Per-IP & /64 Rate Limit? ] ──► No ──► APPLY 15s BAN & DENY
              │ Yes
              ▼
   [ 8. Concurrent Per-IP OK? ] ──► No ──► DENY ("Max Simultaneous Connections")
              │ Yes
              ▼
        ADMIT TO PRE-LOGIN
```

---

## 3. Operator Commands

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/nexgate antibot status` | `nexgate.admin.antibot` | Inspect real-time traffic metrics, evaluated/rejected counts, and Under-Attack status. |
| `/nexgate antibot mode <auto\|on\|off>` | `nexgate.admin.antibot` | Set defense mode. `auto` engages mitigation dynamically during surges; `on` forces strict limits; `off` disables mitigation. |
| `/nexgate antibot ban <ip/cidr> [secs]` | `nexgate.admin.antibot` | Ban an individual IP (temporary duration) or permanent CIDR subnet. |
| `/nexgate antibot unban <ip/cidr>` | `nexgate.admin.antibot` | Remove temporary ban or unban CIDR subnet. |
| `/nexgate antibot whitelist <cidr>` | `nexgate.admin.antibot` | Whitelist trusted proxy IPs or monitoring nodes. |
| `/nexgate antibot unwhitelist <cidr>` | `nexgate.admin.antibot` | Remove subnet from whitelist. |

---

## 4. Tuning Recommendations by Server Scale

### Small to Medium Servers (1–200 Players)
```json
"antibot": {
  "globalRateLimit": 30,
  "perIpRateLimit": 3,
  "maxConcurrentPerIp": 3,
  "fastReconnectThresholdMs": 1200,
  "mitigationThreshold": 12,
  "mitigationDurationSeconds": 60
}
```

### Large Multi-Lobby Networks (200–2,000+ Players)
```json
"antibot": {
  "globalRateLimit": 100,
  "perIpRateLimit": 5,
  "maxConcurrentPerIp": 4,
  "fastReconnectThresholdMs": 1000,
  "mitigationThreshold": 40,
  "mitigationDurationSeconds": 120
}
```

### Event / Streamer Host Defense (Under Active Flood)
When an active bot flood is identified:
```bash
# Force immediate mitigation defense mode:
/nexgate antibot mode on

# Or ban the attacking proxy subnet directly:
/nexgate antibot ban 198.51.100.0/24
```
