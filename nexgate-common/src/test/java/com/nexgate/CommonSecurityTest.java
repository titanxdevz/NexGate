package com.nexgate;

import com.nexgate.color.ChatColorFormatter;
import com.nexgate.color.MessageColor;
import com.nexgate.color.MessageStyle;
import com.nexgate.crypto.HashingUtils;
import com.nexgate.crypto.HmacUtils;
import com.nexgate.model.SessionRecord;
import com.nexgate.model.UserAuth;
import com.nexgate.ratelimit.RateLimiter;
import com.nexgate.security.PasswordValidator;
import com.nexgate.security.ReplayProtectionCache;
import com.nexgate.security.UsernameValidator;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class CommonSecurityTest {

    @Test
    public void testArgon2HashingAndVerification() {
        String password = "SuperSecretPassword123!";
        String hash = HashingUtils.hashPassword(password);
        assertNotNull(hash);
        assertTrue(hash.startsWith("$argon2id$"));
        assertTrue(HashingUtils.verifyPassword(password, hash));
        assertFalse(HashingUtils.verifyPassword("WrongPassword", hash));
    }

    @Test
    public void testHmacSigningAndReplayProtection() {
        String secret = "test-secret-key-12345678901234567890";
        String player = "Steve";
        String nonce = "nonce-1";
        long now = System.currentTimeMillis();
        String uuid = UUID.randomUUID().toString();

        String hmac = HmacUtils.computePerformLoginHmac(secret, player, nonce, now, uuid, "GOLD", "BOLD");
        assertNotNull(hmac);
        assertEquals(64, hmac.length()); // SHA-256 hex is 64 chars

        // Constant time verification
        assertTrue(HmacUtils.constantTimeEquals(hmac, hmac));
        assertFalse(HmacUtils.constantTimeEquals(hmac, "wrong"));

        // Replay cache test
        ReplayProtectionCache cache = new ReplayProtectionCache(30000);
        assertTrue(cache.checkAndRecord(nonce, now));
        // Replaying same nonce must fail!
        assertFalse(cache.checkAndRecord(nonce, now));
        // Expired timestamp must fail
        assertFalse(cache.checkAndRecord("nonce-2", now - 40000));
    }

    @Test
    public void testPasswordValidator() {
        PasswordValidator validator = new PasswordValidator(8, 64, true, true, true);

        // Too short
        assertFalse(validator.validate("Ab1!", "Steve").valid());
        // Common password
        assertFalse(validator.validate("password", "Steve").valid());
        // Contains username
        assertFalse(validator.validate("Steve123!A", "Steve").valid());
        // Missing special char
        assertFalse(validator.validate("StrongPass123", "Steve").valid());
        // Missing digit
        assertFalse(validator.validate("StrongPass!@#", "Steve").valid());
        // Valid strong password
        assertTrue(validator.validate("SecureP@ssw0rd2026", "Steve").valid());
    }

    @Test
    public void testUsernameValidator() {
        assertTrue(UsernameValidator.isValidUsername("Alex"));
        assertTrue(UsernameValidator.isValidUsername("Notch_123"));
        assertFalse(UsernameValidator.isValidUsername("A")); // too short
        assertFalse(UsernameValidator.isValidUsername("Invalid Space"));
        assertFalse(UsernameValidator.isValidUsername("Player!"));

        assertTrue(UsernameValidator.isCaseMatch("Steve", "Steve"));
        assertFalse(UsernameValidator.isCaseMatch("steve", "Steve"));
    }

    @Test
    public void testMessageColorAndFormatting() {
        assertEquals("White", MessageColor.WHITE.getDisplayName());
        assertTrue(MessageColor.SUNSET.isGradient());
        assertTrue(MessageColor.isValidHex("#FF55AA"));
        assertFalse(MessageColor.isValidHex("invalid-hex"));

        String formatted = ChatColorFormatter.formatMessage("Hello world!", "GOLD", "BOLD");
        assertTrue(formatted.contains("<color:#FFAA00>"));
        assertTrue(formatted.contains("<bold>"));
        assertTrue(formatted.contains("Hello world!"));

        // Tag sanitization test
        String exploitAttempt = "<click:run_command:/op>Hacked";
        String sanitizedFormatted = ChatColorFormatter.formatMessage(exploitAttempt, "RED", "DEFAULT");
        assertFalse(sanitizedFormatted.contains("<click:run_command:/op>"));
    }

    @Test
    public void testSessionRecordSubnetValidation() {
        UUID uuid = UUID.randomUUID();
        SessionRecord session = new SessionRecord(uuid, "steve", "token123", "192.168.1.50",
                System.currentTimeMillis(), System.currentTimeMillis() + 60000);

        // Strict IP match
        assertTrue(session.isValid("192.168.1.50", true));
        assertFalse(session.isValid("192.168.1.51", true));

        // Subnet IP match (allowed on same /24)
        assertTrue(session.isValid("192.168.1.51", false));
        assertFalse(session.isValid("10.0.0.1", false));
    }

    @Test
    public void testRateLimiterLockout() {
        RateLimiter limiter = new RateLimiter(3, 60);
        String ip = "1.2.3.4";

        assertTrue(limiter.tryAcquire(ip)); // 1
        assertTrue(limiter.tryAcquire(ip)); // 2
        assertTrue(limiter.tryAcquire(ip)); // 3
        assertFalse(limiter.tryAcquire(ip)); // 4 - exceeded

        limiter.lockout(ip, 10);
        assertTrue(limiter.isLockedOut(ip));
        assertFalse(limiter.tryAcquire(ip));

        limiter.reset(ip);
        assertFalse(limiter.isLockedOut(ip));
        assertTrue(limiter.tryAcquire(ip));
    }

    @Test
    public void testDatabaseOperationsSQLite() throws Exception {
        java.io.File tempDb = java.io.File.createTempFile("nexgate-test-", ".db");
        tempDb.deleteOnExit();

        com.nexgate.db.DatabaseManager db = new com.nexgate.db.DatabaseManager(
                "sqlite", "localhost", 3306, "test", "test", "test", tempDb
        );

        // Wait brief moment for tables to initialize async
        Thread.sleep(300);

        UUID uuid = UUID.randomUUID();
        String passHash = HashingUtils.hashPassword("Secret123!");
        UserAuth user = UserAuth.builder()
                .username("TestPlayer")
                .uuid(uuid)
                .passwordHash(passHash)
                .ip("127.0.0.1")
                .lastIp("127.0.0.1")
                .messageColor("SUNSET")
                .messageStyle("BOLD")
                .premium(false)
                .build();

        // 1. Register user
        boolean registered = db.registerUser(user).get();
        assertTrue(registered);

        // 2. Fetch user case-insensitively
        var fetched = db.getUser("testplayer").get();
        assertTrue(fetched.isPresent());
        assertEquals("TestPlayer", fetched.get().getUsername());
        assertEquals("SUNSET", fetched.get().getMessageColor());
        assertEquals("BOLD", fetched.get().getMessageStyle());

        // 3. Count by IP
        int count = db.countAccountsByIp("127.0.0.1").get();
        assertEquals(1, count);

        // 4. Update message color
        db.updateMessageColor("testplayer", "AQUA", "ITALIC").get();
        var updated = db.getUser("testplayer").get();
        assertTrue(updated.isPresent());
        assertEquals("AQUA", updated.get().getMessageColor());
        assertEquals("ITALIC", updated.get().getMessageStyle());

        // 5. Save & get session
        SessionRecord session = new SessionRecord(uuid, "testplayer", "session-token-xyz", "127.0.0.1",
                System.currentTimeMillis(), System.currentTimeMillis() + 60000);
        db.saveSession(session).get();

        var sessFetched = db.getSession(uuid).get();
        assertTrue(sessFetched.isPresent());
        assertEquals("session-token-xyz", sessFetched.get().getSessionToken());

        // 6. Invalidate session
        db.invalidateSession(uuid).get();
        var sessAfter = db.getSession(uuid).get();
        assertTrue(sessAfter.isEmpty());

        // 7. Unregister user
        boolean unregistered = db.unregisterUser("testplayer").get();
        assertTrue(unregistered);
        var finalCheck = db.getUser("testplayer").get();
        assertTrue(finalCheck.isEmpty());

        db.close();
    }
}
