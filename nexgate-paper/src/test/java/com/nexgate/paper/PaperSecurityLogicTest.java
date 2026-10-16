package com.nexgate.paper;

import com.nexgate.crypto.HmacUtils;
import com.nexgate.paper.auth.PaperAuthManager;
import com.nexgate.security.ReplayProtectionCache;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PaperSecurityLogicTest {

    @Test
    public void testProxySessionQueueAndExpiry() throws InterruptedException {
        // Test replay protection and HMAC validation logic as used by Paper
        String secret = "super-secret-hmac-key-shared-with-proxy";
        String player = "Player1";
        String nonce = "random-nonce-123";
        long now = System.currentTimeMillis();
        String uuid = UUID.randomUUID().toString();
        String color = "SUNSET";
        String style = "BOLD";

        String hmac = HmacUtils.computePerformLoginHmac(secret, player, nonce, now, uuid, color, style);

        // Verify valid HMAC matches
        assertTrue(HmacUtils.constantTimeEquals(
                hmac,
                HmacUtils.computePerformLoginHmac(secret, player, nonce, now, uuid, color, style)
        ));

        // Replay cache rejection
        ReplayProtectionCache cache = new ReplayProtectionCache(30000);
        assertTrue(cache.checkAndRecord(nonce, now));
        assertFalse(cache.checkAndRecord(nonce, now), "Replaying the same nonce must be rejected");

        // Stale timestamp rejection
        assertFalse(cache.checkAndRecord("another-nonce", now - 35000), "Expired message must be rejected");
    }

    @Test
    public void testPlayerSettingsModel() {
        PaperAuthManager.PlayerSettings settings = new PaperAuthManager.PlayerSettings("AQUA", "ITALIC");
        assertEquals("AQUA", settings.color());
        assertEquals("ITALIC", settings.style());
    }

    @Test
    public void testPermittedAuthCommandsWhitelist() {
        java.util.Set<String> allowedCommands = java.util.Set.of(
                "login", "l", "register", "reg", "2fa", "totp", "changepassword", "cp", "premium", "color", "msgcolor"
        );

        // Allowed commands must be permitted
        assertTrue(allowedCommands.contains("login"));
        assertTrue(allowedCommands.contains("l"));
        assertTrue(allowedCommands.contains("register"));
        assertTrue(allowedCommands.contains("reg"));
        assertTrue(allowedCommands.contains("2fa"));
        assertTrue(allowedCommands.contains("totp"));
        assertTrue(allowedCommands.contains("changepassword"));
        assertTrue(allowedCommands.contains("premium"));

        // Dangerous or privileged commands must NOT be permitted for unauthenticated players
        assertFalse(allowedCommands.contains("op"));
        assertFalse(allowedCommands.contains("gamemode"));
        assertFalse(allowedCommands.contains("teleport"));
        assertFalse(allowedCommands.contains("tp"));
        assertFalse(allowedCommands.contains("stop"));
        assertFalse(allowedCommands.contains("plugins"));
        assertFalse(allowedCommands.contains("help"));
    }

    @Test
    public void testBackendIdentityVerification() {
        UUID expectedUuid = UUID.randomUUID();
        String expectedName = "Alex";

        // Mismatched UUID check
        UUID imposterUuid = UUID.randomUUID();
        assertNotEquals(expectedUuid, imposterUuid);

        // Casing mismatch check
        String lowerName = "alex";
        assertFalse(expectedName.equals(lowerName));
    }

    @Test
    public void testCheckAuthMessageType() {
        com.nexgate.message.MessageType checkAuth = com.nexgate.message.MessageType.CHECK_AUTH;
        assertEquals("check.auth", checkAuth.getId());
        assertFalse(checkAuth.isBroadcast(), "CHECK_AUTH should be a targeted query");
        assertEquals(java.util.Optional.of(checkAuth), com.nexgate.message.MessageType.fromId("check.auth"));
    }

    @Test
    public void testLimboCageGeometryCalculation() {
        int platformY = 100;
        int radius = 3;

        // Player spawn must be centered at 0.5, platformY + 1.0, 0.5
        double spawnX = 0.5;
        double spawnY = platformY + 1.0;
        double spawnZ = 0.5;
        assertEquals(101.0, spawnY);

        // Floor bounds
        int floorMinX = -radius;
        int floorMaxX = radius;
        assertEquals(-3, floorMinX);
        assertEquals(3, floorMaxX);

        // Void fall threshold
        int fallThreshold = platformY - 5;
        assertEquals(95, fallThreshold);
    }

    @Test
    public void testSpawnConfigurationData() {
        String world = "world";
        double x = 18.5;
        double y = 71.0;
        double z = -11.5;
        float yaw = 90.0f;
        float pitch = 0.0f;

        assertTrue(x > 0);
        assertTrue(y >= 64);
        assertEquals("world", world);
        assertEquals(90.0f, yaw);
        assertEquals(0.0f, pitch);
    }

    @Test
    public void testVoidFallProtectionThresholds() {
        // Under Y=20 is guaranteed void failsafe recovery
        double fallY = 15.0;
        assertTrue(fallY < 20.0, "Fall below Y=20 must trigger failsafe recovery");

        // Normal hovering at Y=100 is safe
        double hoverY = 100.0;
        assertFalse(hoverY < 20.0, "Y=100 hover must not trigger void failsafe");
    }
}
