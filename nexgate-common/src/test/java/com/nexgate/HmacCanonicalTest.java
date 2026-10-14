package com.nexgate;

import com.nexgate.crypto.HmacUtils;
import com.nexgate.message.MessageType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class HmacCanonicalTest {

    private final String primarySecret = "primary-secret-key-12345678901234567890";
    private final String fallbackSecret = "older-rotated-secret-key-09876543210987";

    @Test
    public void testCanonicalHmacAcrossMessageTypes() {
        String uuid = UUID.randomUUID().toString();
        String player = "PlayerOne";
        String nonce = "nonce-abc-123";
        long timestamp = System.currentTimeMillis();

        // 1. SYNC_SETTINGS
        String syncPayload = "SUNSET:BOLD";
        String syncHmac = HmacUtils.computeCanonicalHmac(
                primarySecret, HmacUtils.CURRENT_PROTOCOL_VERSION,
                MessageType.SYNC_SETTINGS.getId(), "velocity", uuid, player,
                nonce, timestamp, syncPayload
        );
        assertNotNull(syncHmac);
        assertEquals(64, syncHmac.length());

        assertTrue(HmacUtils.verifyCanonicalHmac(
                List.of(primarySecret), HmacUtils.CURRENT_PROTOCOL_VERSION,
                MessageType.SYNC_SETTINGS.getId(), "velocity", uuid, player,
                nonce, timestamp, syncPayload, syncHmac
        ));

        // 2. LOGOUT
        String logoutHmac = HmacUtils.computeCanonicalHmac(
                primarySecret, HmacUtils.CURRENT_PROTOCOL_VERSION,
                MessageType.LOGOUT.getId(), "velocity", uuid, player,
                nonce, timestamp, "logout"
        );
        assertTrue(HmacUtils.verifyCanonicalHmac(
                List.of(primarySecret), HmacUtils.CURRENT_PROTOCOL_VERSION,
                MessageType.LOGOUT.getId(), "velocity", uuid, player,
                nonce, timestamp, "logout", logoutHmac
        ));

        // 3. FORCE_DISCONNECT
        String disconnectReason = "Account logged in from another location";
        String disconnectHmac = HmacUtils.computeCanonicalHmac(
                primarySecret, HmacUtils.CURRENT_PROTOCOL_VERSION,
                MessageType.FORCE_DISCONNECT.getId(), "velocity", uuid, player,
                nonce, timestamp, disconnectReason
        );
        assertTrue(HmacUtils.verifyCanonicalHmac(
                List.of(primarySecret), HmacUtils.CURRENT_PROTOCOL_VERSION,
                MessageType.FORCE_DISCONNECT.getId(), "velocity", uuid, player,
                nonce, timestamp, disconnectReason, disconnectHmac
        ));
    }

    @Test
    public void testSecretRotationSupport() {
        String uuid = UUID.randomUUID().toString();
        String player = "PlayerTwo";
        String nonce = "nonce-rotate";
        long timestamp = System.currentTimeMillis();

        // Signed with fallback (older) secret
        String olderHmac = HmacUtils.computeCanonicalHmac(
                fallbackSecret, HmacUtils.CURRENT_PROTOCOL_VERSION,
                MessageType.LOGOUT.getId(), "velocity", uuid, player,
                nonce, timestamp, "logout"
        );

        // Verification with both [primary, fallback] must succeed
        assertTrue(HmacUtils.verifyCanonicalHmac(
                List.of(primarySecret, fallbackSecret), HmacUtils.CURRENT_PROTOCOL_VERSION,
                MessageType.LOGOUT.getId(), "velocity", uuid, player,
                nonce, timestamp, "logout", olderHmac
        ));

        // Verification with ONLY primary secret must fail
        assertFalse(HmacUtils.verifyCanonicalHmac(
                List.of(primarySecret), HmacUtils.CURRENT_PROTOCOL_VERSION,
                MessageType.LOGOUT.getId(), "velocity", uuid, player,
                nonce, timestamp, "logout", olderHmac
        ));
    }

    @Test
    public void testAntiTamperingResistance() {
        String uuid = UUID.randomUUID().toString();
        String player = "LegitPlayer";
        String nonce = "nonce-secure";
        long timestamp = System.currentTimeMillis();
        String payload = "data";

        String validHmac = HmacUtils.computeCanonicalHmac(
                primarySecret, HmacUtils.CURRENT_PROTOCOL_VERSION,
                MessageType.SYNC_SETTINGS.getId(), "velocity", uuid, player,
                nonce, timestamp, payload
        );

        // Attacker attempts to change player name
        assertFalse(HmacUtils.verifyCanonicalHmac(
                List.of(primarySecret), HmacUtils.CURRENT_PROTOCOL_VERSION,
                MessageType.SYNC_SETTINGS.getId(), "velocity", uuid, "Imposter",
                nonce, timestamp, payload, validHmac
        ));

        // Attacker attempts to change payload
        assertFalse(HmacUtils.verifyCanonicalHmac(
                List.of(primarySecret), HmacUtils.CURRENT_PROTOCOL_VERSION,
                MessageType.SYNC_SETTINGS.getId(), "velocity", uuid, player,
                nonce, timestamp, "tampered-payload", validHmac
        ));

        // Attacker attempts to change UUID
        assertFalse(HmacUtils.verifyCanonicalHmac(
                List.of(primarySecret), HmacUtils.CURRENT_PROTOCOL_VERSION,
                MessageType.SYNC_SETTINGS.getId(), "velocity", UUID.randomUUID().toString(), player,
                nonce, timestamp, payload, validHmac
        ));
    }
}
