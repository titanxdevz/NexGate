# NexGate Database Migration & Platform Transition Guide

## 1. Schema Versioning System

NexGate employs an automated, transactional schema versioning mechanism managed through the `nexgate_schema_version` table. Every migration executes synchronously upon startup before network listeners register.

### Migration History

| Version | Description | Schema Mutations |
| :--- | :--- | :--- |
| **V1** | Initial Schema Creation | Creates `nexgate_users`, `nexgate_sessions`, and `nexgate_audit_logs`. |
| **V2** | Chat Appearance Preferences | Adds `premium_uuid`, `message_color`, and `message_style`. |
| **V3** | Account Security & MFA | Adds `account_type`, `totp_recovery_codes`, `locked`, `lock_reason`, and `pending_premium`. |
| **V4** | Structured Security Audit Logging | Creates indexed audit table with actor, action, target, IP, and detail tracking. |

All column additions are checked with `ALTER TABLE ... ADD COLUMN` idempotency safeguards across SQLite, MySQL, and PostgreSQL.

---

## 2. Migrating from AuthMeReloaded

### Step 1: Exporting AuthMe Data
AuthMe stores credentials in MySQL or SQLite (`authme.db`). To migrate accounts to NexGate:

```sql
-- Query existing AuthMe accounts:
SELECT realname AS username,
       password AS legacy_hash,
       ip,
       lastlogin,
       email
FROM authme;
```

### Step 2: Password Compatibility
If AuthMe was configured with SHA256 or BCrypt hashes:
1. NexGate's `Argon2Service.needsRehash(hash)` automatically detects legacy hash formats that do not conform to `$argon2id$...`.
2. When a player logs in with their password, NexGate validates the legacy hash (BCrypt or SHA256 fallback), re-hashes the password using Argon2id, and updates the record in `nexgate_users`.

---

## 3. Migrating from LimboAuth

1. **Session Schema**: LimboAuth session tokens are replaced by NexGate's encrypted UUID-keyed session records in `nexgate_sessions`.
2. **Online Mode Handling**: LimboAuth relies on external plugins for hybrid mode. NexGate handles official Mojang verification natively via asynchronous non-blocking Netty event tasks.
3. **Configuration**: Point Velocity's `authServers` list to your existing Limbo instance. Ensure `nexgate:auth` channels are registered.

---

## 4. Multi-Proxy Deployment Coordination

In multi-proxy network architectures (e.g. redundant Velocity proxies behind HAProxy or DNS Anycast):
- **Centralized Database**: Point all Velocity instances to a shared PostgreSQL or MySQL cluster.
- **Shared Sessions**: Sessions stored in `nexgate_sessions` are automatically respected across all proxy instances.
- **Consistent Invalidation**: When `/logout` or an administrative password reset occurs, the record is removed from `nexgate_sessions`, instantly terminating the session network-wide.
