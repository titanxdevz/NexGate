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
}
