package com.nexgate;

import com.nexgate.crypto.HashingUtils;
import com.nexgate.db.DatabaseManager;
import com.nexgate.model.AccountType;
import com.nexgate.model.UserAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseMigrationsAndSecurityTest {

    private File tempDb;
    private DatabaseManager db;

    @BeforeEach
    public void setup() throws Exception {
        tempDb = File.createTempFile("nexgate-migr-", ".db");
        tempDb.deleteOnExit();
        db = new DatabaseManager("sqlite", "localhost", 3306, "test", "test", "test", tempDb);
        // Allow schema to initialize
        Thread.sleep(200);
    }

    @AfterEach
    public void tearDown() {
        if (db != null) {
            db.close();
        }
        if (tempDb != null && tempDb.exists()) {
            tempDb.delete();
        }
    }

    @Test
    public void testAccountLockAndUnlock() throws Exception {
        UserAuth user = UserAuth.builder()
                .username("LockTarget")
                .uuid(UUID.randomUUID())
                .passwordHash(HashingUtils.hashPassword("Pass123!"))
                .ip("10.0.0.1")
                .premium(false)
                .build();
        db.registerUser(user).get();

        // Initially not locked
        Optional<UserAuth> fetched = db.getUser("locktarget").get();
        assertTrue(fetched.isPresent());
        assertFalse(fetched.get().isLocked());
        assertNull(fetched.get().getLockReason());

        // Administratively lock account
        db.updateAccountLock("LockTarget", true, "Suspicious bot activity").get();
        UserAuth locked = db.getUser("locktarget").get().orElseThrow();
        assertTrue(locked.isLocked());
        assertEquals("Suspicious bot activity", locked.getLockReason());

        // Unlock account
        db.updateAccountLock("LockTarget", false, null).get();
        UserAuth unlocked = db.getUser("locktarget").get().orElseThrow();
        assertFalse(unlocked.isLocked());
        assertNull(unlocked.getLockReason());
    }

    @Test
    public void testPendingPremiumLifecycle() throws Exception {
        UserAuth user = UserAuth.builder()
                .username("PremiumCandidate")
                .uuid(UUID.randomUUID())
                .passwordHash(HashingUtils.hashPassword("Pass123!"))
                .ip("10.0.0.2")
                .premium(false)
                .pendingPremium(false)
                .build();
        db.registerUser(user).get();

        // 1. Mark pending premium
        db.setPendingPremium("PremiumCandidate", true).get();
        UserAuth pending = db.getUser("PremiumCandidate").get().orElseThrow();
        assertTrue(pending.isPendingPremium());
        assertFalse(pending.isPremium());

        // 2. Cryptographic verification succeeds: update to Premium
        UUID officialMojangUuid = UUID.randomUUID();
        db.updatePremiumStatus("PremiumCandidate", true, officialMojangUuid, AccountType.PREMIUM).get();

        UserAuth verified = db.getUser("PremiumCandidate").get().orElseThrow();
        assertTrue(verified.isPremium());
        assertFalse(verified.isPendingPremium(), "Pending flag should be cleared once verified");
        assertEquals(officialMojangUuid, verified.getPremiumUuid());
        assertEquals(AccountType.PREMIUM, verified.getAccountType());
    }

    @Test
    public void testAuditLoggingAndQuery() throws Exception {
        long t1 = System.currentTimeMillis();
        db.recordAuditLog("admin", "PASSWORD_RESET", "targetUser", "192.168.1.1", "Admin reset via command").get();
        db.recordAuditLog("system", "PREMIUM_IMPERSONATION_BLOCKED", "targetUser", "1.2.3.4", "Blocked offline join").get();
        db.recordAuditLog("targetUser", "LOGIN", "targetUser", "192.168.1.1", "Normal login").get();

        List<DatabaseManager.AuditLogEntry> logs = db.getRecentAuditLogs("targetUser", 10).get();
        assertNotNull(logs);
        assertTrue(logs.size() >= 3);

        boolean foundBlock = logs.stream().anyMatch(l -> "PREMIUM_IMPERSONATION_BLOCKED".equals(l.action()));
        assertTrue(foundBlock, "Should record impersonation blocked event");

        boolean foundReset = logs.stream().anyMatch(l -> "PASSWORD_RESET".equals(l.action()));
        assertTrue(foundReset, "Should record password reset event");
    }

    @Test
    public void testTotpDatabasePersistence() throws Exception {
        UserAuth user = UserAuth.builder()
                .username("TotpUser")
                .uuid(UUID.randomUUID())
                .passwordHash(HashingUtils.hashPassword("Pass123!"))
                .ip("10.0.0.3")
                .premium(false)
                .build();
        db.registerUser(user).get();

        String secret = "JBSWY3DPEHPK3PXP";
        String hashedCodes = "hash1,hash2,hash3";

        // Enable TOTP
        db.updateTotp("TotpUser", secret, true, hashedCodes).get();
        UserAuth updated = db.getUser("totpuser").get().orElseThrow();
        assertTrue(updated.isTotpEnabled());
        assertEquals(secret, updated.getTotpSecret());
        assertEquals(hashedCodes, updated.getTotpRecoveryCodes());

        // Disable TOTP
        db.updateTotp("TotpUser", null, false, null).get();
        UserAuth disabled = db.getUser("totpuser").get().orElseThrow();
        assertFalse(disabled.isTotpEnabled());
        assertNull(disabled.getTotpSecret());
        assertNull(disabled.getTotpRecoveryCodes());
    }
}
