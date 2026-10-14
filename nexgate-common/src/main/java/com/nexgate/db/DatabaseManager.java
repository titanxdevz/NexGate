package com.nexgate.db;

import com.nexgate.model.AccountType;
import com.nexgate.model.SessionRecord;
import com.nexgate.model.UserAuth;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Production-grade database manager supporting SQLite, MySQL/MariaDB, and PostgreSQL.
 * Synchronous versioned migrations on startup with asynchronous runtime queries via HikariCP.
 */
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    public enum DatabaseType {
        SQLITE,
        MYSQL,
        POSTGRESQL;

        public static DatabaseType fromString(String type) {
            if (type == null) return SQLITE;
            String lower = type.trim().toLowerCase();
            if (lower.contains("postgre") || lower.contains("pg")) {
                return POSTGRESQL;
            } else if (lower.contains("mysql") || lower.contains("maria")) {
                return MYSQL;
            }
            return SQLITE;
        }
    }

    private final HikariDataSource dataSource;
    private final ExecutorService executorService;
    private final DatabaseType dbType;

    public DatabaseManager(String type, String host, int port, String database,
                           String username, String password, File sqliteFile) {
        this.dbType = DatabaseType.fromString(type);
        this.executorService = new ThreadPoolExecutor(
                2, 8, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                new ThreadFactory() {
                    private int count = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "NexGate-DB-" + (++count));
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        HikariConfig config = new HikariConfig();
        switch (dbType) {
            case SQLITE -> {
                if (sqliteFile.getParentFile() != null) {
                    sqliteFile.getParentFile().mkdirs();
                }
                config.setJdbcUrl("jdbc:sqlite:" + sqliteFile.getAbsolutePath());
                config.setDriverClassName("org.sqlite.JDBC");
                config.setMaximumPoolSize(1); // SQLite is single-writer
                config.setPoolName("NexGateSqlitePool");
                config.setConnectionTimeout(30000);
            }
            case POSTGRESQL -> {
                config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
                config.setDriverClassName("org.postgresql.Driver");
                config.setUsername(username);
                config.setPassword(password);
                config.setMaximumPoolSize(10);
                config.setMinimumIdle(2);
                config.setConnectionTimeout(30000);
                config.setIdleTimeout(600000);
                config.setMaxLifetime(1800000);
                config.setPoolName("NexGatePostgreSqlPool");
            }
            case MYSQL -> {
                config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database +
                        "?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8&allowPublicKeyRetrieval=true");
                config.setUsername(username);
                config.setPassword(password);
                config.setMaximumPoolSize(10);
                config.setMinimumIdle(2);
                config.setConnectionTimeout(30000);
                config.setIdleTimeout(600000);
                config.setMaxLifetime(1800000);
                config.setPoolName("NexGateMySqlPool");
            }
        }

        this.dataSource = new HikariDataSource(config);

        // Synchronously initialize schema and apply versioned migrations before accepting requests
        initializeTablesAndMigrations();
    }

    public DatabaseType getDatabaseType() {
        return dbType;
    }

    private void initializeTablesAndMigrations() {
        try (Connection conn = dataSource.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            try (Statement stmt = conn.createStatement()) {
                // 1. Create version tracking table
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS nexgate_schema_version (
                        version INT PRIMARY KEY,
                        description VARCHAR(255) NOT NULL,
                        applied_at BIGINT NOT NULL
                    );
                """);

                // 2. Base tables if not exists
                if (dbType == DatabaseType.SQLITE) {
                    stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS nexgate_users (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            username VARCHAR(16) NOT NULL,
                            username_lower VARCHAR(16) NOT NULL UNIQUE,
                            uuid VARCHAR(36),
                            password_hash VARCHAR(255) NOT NULL,
                            ip VARCHAR(45),
                            reg_date BIGINT NOT NULL,
                            last_login BIGINT,
                            last_ip VARCHAR(45),
                            is_premium INTEGER DEFAULT 0,
                            premium_uuid VARCHAR(36),
                            account_type VARCHAR(16) DEFAULT 'OFFLINE',
                            message_color VARCHAR(32) DEFAULT 'WHITE',
                            message_style VARCHAR(32) DEFAULT 'DEFAULT',
                            totp_secret VARCHAR(64),
                            totp_enabled INTEGER DEFAULT 0,
                            totp_recovery_codes TEXT,
                            locked INTEGER DEFAULT 0,
                            lock_reason VARCHAR(255),
                            pending_premium INTEGER DEFAULT 0
                        );
                    """);

                    stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS nexgate_sessions (
                            uuid VARCHAR(36) PRIMARY KEY,
                            username_lower VARCHAR(16) NOT NULL,
                            session_token VARCHAR(64) NOT NULL,
                            ip_address VARCHAR(45) NOT NULL,
                            created_at BIGINT NOT NULL,
                            expires_at BIGINT NOT NULL
                        );
                    """);

                    stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS nexgate_audit_logs (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            timestamp BIGINT NOT NULL,
                            actor VARCHAR(64) NOT NULL,
                            action VARCHAR(64) NOT NULL,
                            target VARCHAR(64),
                            ip VARCHAR(45),
                            detail TEXT
                        );
                    """);
                } else if (dbType == DatabaseType.POSTGRESQL) {
                    stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS nexgate_users (
                            id BIGSERIAL PRIMARY KEY,
                            username VARCHAR(16) NOT NULL,
                            username_lower VARCHAR(16) NOT NULL UNIQUE,
                            uuid VARCHAR(36),
                            password_hash VARCHAR(255) NOT NULL,
                            ip VARCHAR(45),
                            reg_date BIGINT NOT NULL,
                            last_login BIGINT,
                            last_ip VARCHAR(45),
                            is_premium SMALLINT DEFAULT 0,
                            premium_uuid VARCHAR(36),
                            account_type VARCHAR(16) DEFAULT 'OFFLINE',
                            message_color VARCHAR(32) DEFAULT 'WHITE',
                            message_style VARCHAR(32) DEFAULT 'DEFAULT',
                            totp_secret VARCHAR(64),
                            totp_enabled SMALLINT DEFAULT 0,
                            totp_recovery_codes TEXT,
                            locked SMALLINT DEFAULT 0,
                            lock_reason VARCHAR(255),
                            pending_premium SMALLINT DEFAULT 0
                        );
                    """);

                    stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS nexgate_sessions (
                            uuid VARCHAR(36) PRIMARY KEY,
                            username_lower VARCHAR(16) NOT NULL,
                            session_token VARCHAR(64) NOT NULL,
                            ip_address VARCHAR(45) NOT NULL,
                            created_at BIGINT NOT NULL,
                            expires_at BIGINT NOT NULL
                        );
                    """);

                    stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS nexgate_audit_logs (
                            id BIGSERIAL PRIMARY KEY,
                            timestamp BIGINT NOT NULL,
                            actor VARCHAR(64) NOT NULL,
                            action VARCHAR(64) NOT NULL,
                            target VARCHAR(64),
                            ip VARCHAR(45),
                            detail TEXT
                        );
                    """);
                } else {
                    // MySQL
                    stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS nexgate_users (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            username VARCHAR(16) NOT NULL,
                            username_lower VARCHAR(16) NOT NULL UNIQUE,
                            uuid VARCHAR(36),
                            password_hash VARCHAR(255) NOT NULL,
                            ip VARCHAR(45),
                            reg_date BIGINT NOT NULL,
                            last_login BIGINT,
                            last_ip VARCHAR(45),
                            is_premium TINYINT(1) DEFAULT 0,
                            premium_uuid VARCHAR(36),
                            account_type VARCHAR(16) DEFAULT 'OFFLINE',
                            message_color VARCHAR(32) DEFAULT 'WHITE',
                            message_style VARCHAR(32) DEFAULT 'DEFAULT',
                            totp_secret VARCHAR(64),
                            totp_enabled TINYINT(1) DEFAULT 0,
                            totp_recovery_codes TEXT,
                            locked TINYINT(1) DEFAULT 0,
                            lock_reason VARCHAR(255),
                            pending_premium TINYINT(1) DEFAULT 0,
                            INDEX idx_username_lower (username_lower),
                            INDEX idx_uuid (uuid),
                            INDEX idx_ip (ip)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                    """);

                    stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS nexgate_sessions (
                            uuid VARCHAR(36) PRIMARY KEY,
                            username_lower VARCHAR(16) NOT NULL,
                            session_token VARCHAR(64) NOT NULL,
                            ip_address VARCHAR(45) NOT NULL,
                            created_at BIGINT NOT NULL,
                            expires_at BIGINT NOT NULL,
                            INDEX idx_sess_user (username_lower),
                            INDEX idx_sess_expiry (expires_at)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                    """);

                    stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS nexgate_audit_logs (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            timestamp BIGINT NOT NULL,
                            actor VARCHAR(64) NOT NULL,
                            action VARCHAR(64) NOT NULL,
                            target VARCHAR(64),
                            ip VARCHAR(45),
                            detail TEXT,
                            INDEX idx_audit_time (timestamp),
                            INDEX idx_audit_actor (actor)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                    """);
                }
            }

            // Apply versioned schema migrations
            applyVersionedMigrations(conn);

            logger.info("Database initialized successfully ({})", dbType);
        } catch (SQLException e) {
            logger.error("Failed to initialize database tables", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private void applyVersionedMigrations(Connection conn) throws SQLException {
        int currentVersion = getLatestSchemaVersion(conn);

        // Migration V1: Base version
        if (currentVersion < 1) {
            recordMigration(conn, 1, "Initial schema creation");
        }

        // Migration V2: Styling columns
        if (currentVersion < 2) {
            addColumnIfNotExists(conn, "nexgate_users", "premium_uuid", "VARCHAR(36)");
            addColumnIfNotExists(conn, "nexgate_users", "message_color", "VARCHAR(32) DEFAULT 'WHITE'");
            addColumnIfNotExists(conn, "nexgate_users", "message_style", "VARCHAR(32) DEFAULT 'DEFAULT'");
            recordMigration(conn, 2, "Add chat customization columns");
        }

        // Migration V3: Account security, MFA, and Pending Premium
        if (currentVersion < 3) {
            addColumnIfNotExists(conn, "nexgate_users", "account_type", "VARCHAR(16) DEFAULT 'OFFLINE'");
            addColumnIfNotExists(conn, "nexgate_users", "totp_recovery_codes", "TEXT");
            addColumnIfNotExists(conn, "nexgate_users", "locked", "INTEGER DEFAULT 0");
            addColumnIfNotExists(conn, "nexgate_users", "lock_reason", "VARCHAR(255)");
            addColumnIfNotExists(conn, "nexgate_users", "pending_premium", "INTEGER DEFAULT 0");
            recordMigration(conn, 3, "Add account type, 2FA recovery codes, and pending premium");
        }

        // Migration V4: Audit logging
        if (currentVersion < 4) {
            try (Statement stmt = conn.createStatement()) {
                if (dbType == DatabaseType.POSTGRESQL) {
                    stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS nexgate_audit_logs (
                            id BIGSERIAL PRIMARY KEY,
                            timestamp BIGINT NOT NULL,
                            actor VARCHAR(64) NOT NULL,
                            action VARCHAR(64) NOT NULL,
                            target VARCHAR(64),
                            ip VARCHAR(45),
                            detail TEXT
                        );
                    """);
                } else if (dbType == DatabaseType.SQLITE) {
                    stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS nexgate_audit_logs (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            timestamp BIGINT NOT NULL,
                            actor VARCHAR(64) NOT NULL,
                            action VARCHAR(64) NOT NULL,
                            target VARCHAR(64),
                            ip VARCHAR(45),
                            detail TEXT
                        );
                    """);
                } else {
                    stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS nexgate_audit_logs (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            timestamp BIGINT NOT NULL,
                            actor VARCHAR(64) NOT NULL,
                            action VARCHAR(64) NOT NULL,
                            target VARCHAR(64),
                            ip VARCHAR(45),
                            detail TEXT,
                            INDEX idx_audit_time (timestamp),
                            INDEX idx_audit_actor (actor)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                    """);
                }
            }
            recordMigration(conn, 4, "Create audit logging table");
        }
    }

    private int getLatestSchemaVersion(Connection conn) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM nexgate_schema_version")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException ignored) {
        }
        return 0;
    }

    private void recordMigration(Connection conn, int version, String description) throws SQLException {
        String sql = "INSERT INTO nexgate_schema_version (version, description, applied_at) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, version);
            ps.setString(2, description);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
            logger.info("Applied schema migration V{}: {}", version, description);
        }
    }

    private void addColumnIfNotExists(Connection conn, String table, String column, String colDef) {
        String sql = "ALTER TABLE " + table + " ADD COLUMN " + column + " " + colDef;
        try (Statement s = conn.createStatement()) {
            s.executeUpdate(sql);
        } catch (SQLException ignored) {
            // Column already exists
        }
    }

    public CompletableFuture<Optional<UserAuth>> getUser(String username) {
        if (username == null || username.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String lower = username.toLowerCase();
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM nexgate_users WHERE username_lower = ? LIMIT 1";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, lower);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapUser(rs));
                    }
                }
            } catch (SQLException e) {
                logger.error("Error fetching user " + username, e);
            }
            return Optional.empty();
        }, executorService);
    }

    public CompletableFuture<Optional<UserAuth>> getUserByUuid(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM nexgate_users WHERE uuid = ? LIMIT 1";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapUser(rs));
                    }
                }
            } catch (SQLException e) {
                logger.error("Error fetching user by uuid " + uuid, e);
            }
            return Optional.empty();
        }, executorService);
    }

    public CompletableFuture<Boolean> registerUser(UserAuth user) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO nexgate_users (username, username_lower, uuid, password_hash, ip, reg_date,
                    last_login, last_ip, is_premium, premium_uuid, account_type, message_color, message_style,
                    totp_secret, totp_enabled, totp_recovery_codes, locked, lock_reason, pending_premium)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, user.getUsername());
                ps.setString(2, user.getUsernameLower());
                ps.setString(3, user.getUuid() != null ? user.getUuid().toString() : null);
                ps.setString(4, user.getPasswordHash());
                ps.setString(5, user.getIp());
                ps.setLong(6, user.getRegDate());
                ps.setLong(7, user.getLastLogin());
                ps.setString(8, user.getLastIp());
                ps.setInt(9, user.isPremium() ? 1 : 0);
                ps.setString(10, user.getPremiumUuid() != null ? user.getPremiumUuid().toString() : null);
                ps.setString(11, user.getAccountType() != null ? user.getAccountType().name() : AccountType.OFFLINE.name());
                ps.setString(12, user.getMessageColor());
                ps.setString(13, user.getMessageStyle());
                ps.setString(14, user.getTotpSecret());
                ps.setInt(15, user.isTotpEnabled() ? 1 : 0);
                ps.setString(16, user.getTotpRecoveryCodes());
                ps.setInt(17, user.isLocked() ? 1 : 0);
                ps.setString(18, user.getLockReason());
                ps.setInt(19, user.isPendingPremium() ? 1 : 0);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                logger.error("Error registering user " + user.getUsername(), e);
                return false;
            }
        }, executorService);
    }

    public CompletableFuture<Boolean> updatePassword(String username, String newHash) {
        String lower = username.toLowerCase();
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE nexgate_users SET password_hash = ? WHERE username_lower = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newHash);
                ps.setString(2, lower);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                logger.error("Error updating password for " + username, e);
                return false;
            }
        }, executorService);
    }

    public CompletableFuture<Void> updateLastLogin(String username, String ip, long timestamp) {
        String lower = username.toLowerCase();
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE nexgate_users SET last_login = ?, last_ip = ? WHERE username_lower = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, timestamp);
                ps.setString(2, ip);
                ps.setString(3, lower);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.error("Error updating last login for " + username, e);
            }
        }, executorService);
    }

    public CompletableFuture<Void> updatePremiumStatus(String username, boolean premium, UUID premiumUuid, AccountType accountType) {
        String lower = username.toLowerCase();
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE nexgate_users SET is_premium = ?, premium_uuid = ?, account_type = ?, pending_premium = 0 WHERE username_lower = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, premium ? 1 : 0);
                ps.setString(2, premiumUuid != null ? premiumUuid.toString() : null);
                ps.setString(3, accountType != null ? accountType.name() : (premium ? "PREMIUM" : "OFFLINE"));
                ps.setString(4, lower);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.error("Error updating premium status for " + username, e);
            }
        }, executorService);
    }

    public CompletableFuture<Void> updatePremiumStatus(String username, boolean premium, UUID premiumUuid) {
        return updatePremiumStatus(username, premium, premiumUuid, premium ? AccountType.PREMIUM : AccountType.OFFLINE);
    }

    public CompletableFuture<Void> setPendingPremium(String username, boolean pending) {
        String lower = username.toLowerCase();
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE nexgate_users SET pending_premium = ? WHERE username_lower = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, pending ? 1 : 0);
                ps.setString(2, lower);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.error("Error setting pending premium for " + username, e);
            }
        }, executorService);
    }

    public CompletableFuture<Void> updateTotp(String username, String secret, boolean enabled, String recoveryCodes) {
        String lower = username.toLowerCase();
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE nexgate_users SET totp_secret = ?, totp_enabled = ?, totp_recovery_codes = ? WHERE username_lower = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, secret);
                ps.setInt(2, enabled ? 1 : 0);
                ps.setString(3, recoveryCodes);
                ps.setString(4, lower);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.error("Error updating TOTP for " + username, e);
            }
        }, executorService);
    }

    public CompletableFuture<Void> updateAccountLock(String username, boolean locked, String reason) {
        String lower = username.toLowerCase();
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE nexgate_users SET locked = ?, lock_reason = ? WHERE username_lower = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, locked ? 1 : 0);
                ps.setString(2, reason);
                ps.setString(3, lower);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.error("Error updating lock status for " + username, e);
            }
        }, executorService);
    }

    public CompletableFuture<Void> updateMessageColor(String username, String color, String style) {
        String lower = username.toLowerCase();
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE nexgate_users SET message_color = ?, message_style = ? WHERE username_lower = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, color);
                ps.setString(2, style);
                ps.setString(3, lower);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.error("Error updating message color for " + username, e);
            }
        }, executorService);
    }

    public CompletableFuture<Boolean> unregisterUser(String username) {
        String lower = username.toLowerCase();
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM nexgate_users WHERE username_lower = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, lower);
                boolean deleted = ps.executeUpdate() > 0;
                if (deleted) {
                    try (PreparedStatement sessPs = conn.prepareStatement("DELETE FROM nexgate_sessions WHERE username_lower = ?")) {
                        sessPs.setString(1, lower);
                        sessPs.executeUpdate();
                    }
                }
                return deleted;
            } catch (SQLException e) {
                logger.error("Error unregistering user " + username, e);
                return false;
            }
        }, executorService);
    }

    public CompletableFuture<Integer> countAccountsByIp(String ip) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM nexgate_users WHERE ip = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ip);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                logger.error("Error counting accounts for IP " + ip, e);
            }
            return 0;
        }, executorService);
    }

    public CompletableFuture<Void> saveSession(SessionRecord session) {
        return CompletableFuture.runAsync(() -> {
            String sql;
            if (dbType == DatabaseType.SQLITE) {
                sql = "INSERT OR REPLACE INTO nexgate_sessions (uuid, username_lower, session_token, ip_address, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)";
            } else if (dbType == DatabaseType.POSTGRESQL) {
                sql = """
                    INSERT INTO nexgate_sessions (uuid, username_lower, session_token, ip_address, created_at, expires_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (uuid) DO UPDATE SET session_token = EXCLUDED.session_token,
                        ip_address = EXCLUDED.ip_address, created_at = EXCLUDED.created_at, expires_at = EXCLUDED.expires_at
                """;
            } else {
                sql = """
                    INSERT INTO nexgate_sessions (uuid, username_lower, session_token, ip_address, created_at, expires_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE session_token = VALUES(session_token), ip_address = VALUES(ip_address),
                        created_at = VALUES(created_at), expires_at = VALUES(expires_at)
                """;
            }
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, session.getUuid().toString());
                ps.setString(2, session.getUsernameLower());
                ps.setString(3, session.getSessionToken());
                ps.setString(4, session.getIpAddress());
                ps.setLong(5, session.getCreatedAt());
                ps.setLong(6, session.getExpiresAt());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.error("Error saving session for " + session.getUsernameLower(), e);
            }
        }, executorService);
    }

    public CompletableFuture<Optional<SessionRecord>> getSession(UUID uuid) {
        if (uuid == null) return CompletableFuture.completedFuture(Optional.empty());
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM nexgate_sessions WHERE uuid = ? LIMIT 1";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String userLower = rs.getString("username_lower");
                        String token = rs.getString("session_token");
                        String ip = rs.getString("ip_address");
                        long created = rs.getLong("created_at");
                        long expires = rs.getLong("expires_at");
                        return Optional.of(new SessionRecord(uuid, userLower, token, ip, created, expires));
                    }
                }
            } catch (SQLException e) {
                logger.error("Error fetching session for " + uuid, e);
            }
            return Optional.empty();
        }, executorService);
    }

    public CompletableFuture<Void> invalidateSession(UUID uuid) {
        if (uuid == null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM nexgate_sessions WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.error("Error invalidating session for " + uuid, e);
            }
        }, executorService);
    }

    public CompletableFuture<Void> cleanExpiredSessions() {
        return CompletableFuture.runAsync(() -> {
            long now = System.currentTimeMillis();
            String sql = "DELETE FROM nexgate_sessions WHERE expires_at < ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, now);
                int removed = ps.executeUpdate();
                if (removed > 0) {
                    logger.debug("Cleaned {} expired sessions", removed);
                }
            } catch (SQLException e) {
                logger.error("Error cleaning expired sessions", e);
            }
        }, executorService);
    }

    public CompletableFuture<Void> recordAuditLog(String actor, String action, String target, String ip, String detail) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO nexgate_audit_logs (timestamp, actor, action, target, ip, detail) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, System.currentTimeMillis());
                ps.setString(2, actor);
                ps.setString(3, action);
                ps.setString(4, target);
                ps.setString(5, ip);
                ps.setString(6, detail);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.error("Error recording audit log for " + action, e);
            }
        }, executorService);
    }

    public CompletableFuture<List<String>> getAllUsernames() {
        return CompletableFuture.supplyAsync(() -> {
            List<String> list = new ArrayList<>();
            String sql = "SELECT username FROM nexgate_users ORDER BY username ASC";
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    list.add(rs.getString("username"));
                }
            } catch (SQLException e) {
                logger.error("Error fetching all usernames", e);
            }
            return list;
        }, executorService);
    }

    private UserAuth mapUser(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String username = rs.getString("username");
        String usernameLower = rs.getString("username_lower");
        String uuidStr = rs.getString("uuid");
        UUID uuid = uuidStr != null ? UUID.fromString(uuidStr) : null;
        String passHash = rs.getString("password_hash");
        String ip = rs.getString("ip");
        long regDate = rs.getLong("reg_date");
        long lastLogin = rs.getLong("last_login");
        String lastIp = rs.getString("last_ip");
        boolean premium = rs.getInt("is_premium") == 1;
        String premUuidStr = rs.getString("premium_uuid");
        UUID premiumUuid = premUuidStr != null ? UUID.fromString(premUuidStr) : null;

        String accountTypeStr = null;
        try {
            accountTypeStr = rs.getString("account_type");
        } catch (SQLException ignored) {}
        AccountType accountType = AccountType.fromString(accountTypeStr != null ? accountTypeStr : (premium ? "PREMIUM" : "OFFLINE"));

        String color = rs.getString("message_color");
        String style = rs.getString("message_style");
        String totpSecret = rs.getString("totp_secret");
        boolean totp = rs.getInt("totp_enabled") == 1;

        String totpCodes = null;
        try {
            totpCodes = rs.getString("totp_recovery_codes");
        } catch (SQLException ignored) {}

        boolean locked = false;
        try {
            locked = rs.getInt("locked") == 1;
        } catch (SQLException ignored) {}

        String lockReason = null;
        try {
            lockReason = rs.getString("lock_reason");
        } catch (SQLException ignored) {}

        boolean pendingPremium = false;
        try {
            pendingPremium = rs.getInt("pending_premium") == 1;
        } catch (SQLException ignored) {}

        return new UserAuth(id, username, usernameLower, uuid, passHash, ip, regDate,
                lastLogin, lastIp, premium, premiumUuid, accountType, color, style,
                totpSecret, totp, totpCodes, locked, lockReason, pendingPremium);
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
