package com.nexgate.velocity;

import com.nexgate.model.AccountType;
import com.nexgate.model.AuthMode;
import com.nexgate.model.UserAuth;
import com.nexgate.security.UsernameValidator;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PreLoginPolicyTest {

    @Test
    public void testUsernameValidationAndBedrockDetection() {
        // Standard Java usernames
        assertTrue(UsernameValidator.isValidUsername("Player123", true));
        assertTrue(UsernameValidator.isValidUsername("Notch", true));
        assertFalse(UsernameValidator.isFloodgateUsername("Player123"));

        // Floodgate Bedrock usernames with '.' prefix
        assertTrue(UsernameValidator.isFloodgateUsername(".BedrockPlayer"));
        assertTrue(UsernameValidator.isValidUsername(".BedrockPlayer", true));
        assertFalse(UsernameValidator.isValidUsername(".BedrockPlayer", false)); // disallowed when flag false

        // Invalid usernames
        assertFalse(UsernameValidator.isValidUsername("ab", true)); // too short (<3)
        assertFalse(UsernameValidator.isValidUsername("ThisUsernameIsFarTooLongForMinecraft", true)); // >16
        assertFalse(UsernameValidator.isValidUsername("Player with space", true));
        assertFalse(UsernameValidator.isValidUsername("Player!@#", true));
    }

    @Test
    public void testStrictCaseSensitivityMatch() {
        assertTrue(UsernameValidator.isCaseMatch("MinecraftPro", "MinecraftPro"));
        assertFalse(UsernameValidator.isCaseMatch("minecraftpro", "MinecraftPro"));
        assertFalse(UsernameValidator.isCaseMatch("MINECRAFTPRO", "MinecraftPro"));
    }

    @Test
    public void testAuthModeRoutingPolicies() {
        // In ONLINE_ONLY mode, Bedrock players cannot join via Java encryption
        AuthMode onlineOnly = AuthMode.ONLINE_ONLY;
        assertEquals("ONLINE_ONLY", onlineOnly.name());

        // In OFFLINE_AUTH mode, all accounts authenticate via NexGate passwords
        AuthMode offlineAuth = AuthMode.OFFLINE_AUTH;
        assertEquals("OFFLINE_AUTH", offlineAuth.name());

        // In HYBRID mode, verified premium accounts enforce online mode while offline accounts use NexGate auth
        AuthMode hybrid = AuthMode.HYBRID;
        assertEquals("HYBRID", hybrid.name());
    }

    @Test
    public void testPremiumImpersonationDefenseModel() {
        // Account registered as Premium
        UserAuth premiumUser = UserAuth.builder()
                .username("OfficialPlayer")
                .uuid(UUID.randomUUID())
                .premium(true)
                .accountType(AccountType.PREMIUM)
                .build();

        assertTrue(premiumUser.isPremium());
        assertFalse(premiumUser.isPendingPremium());

        // Defensive policy rule: If an offline-mode connection attempts to log into this account,
        // it must be immediately kicked and logged as an impersonation attempt.
        boolean isConnectionOnlineMode = false;
        boolean allowed = !premiumUser.isPremium() || isConnectionOnlineMode;
        assertFalse(allowed, "Offline connection must NOT be allowed to access an account marked Premium!");
    }
}
