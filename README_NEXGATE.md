# NexGate - Original Auth Plugin Design

This workspace contains the design/spec for NexGate, an original Minecraft authentication plugin for Velocity proxy with Paper backends.

## Documents
- `PHASE1_DESIGN.md` - Study of references + login flow analysis
- `nexgate-spec.md` - Technical specification (architecture, messages, schema, security)
- (implementation in progress per Phase 3)

## Key Design Decisions (from study)
1. **Proxy-first auth gating**: Auth happens at Velocity (like LimboAuth) to prevent bypasses. Primary control point.
2. **HMAC-signed proxy→backend messages**: Reimplementing AuthMe's signed perform.login pattern with HMAC-SHA256, timestamp, one-time tokens.
3. **Defense-in-depth on backend**: Paper companion blocks movement/chat/commands for unauthenticated players.
4. **Race condition safety**: Check auth state before scheduling limbo/kick (learned from AuthMe's guard).
5. **Argon2id** for password hashing (modern, secure). No custom crypto.
6. **Async DB everywhere**: HikariCP + async queries, never block main thread.
7. **Per-IP and per-account rate limiting**: Prevent brute force/bot floods.
8. **Case-insensitive lookups with case validation**: Prevent case tricks.

## Next Steps (Phase 3)
Implement original multi-module Gradle project:
- `nexgate-common` - shared models, crypto, messages
- `nexgate-velocity` - proxy plugin
- `nexgate-paper` - backend companion

All code original. Dependencies pinned with versions/licenses.
```