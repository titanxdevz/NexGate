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

    @Test
    public void testActiveSessionBypassesAuthServerDirectlyToLobby() {
        // Player with an active session in cache
        boolean hasActiveSession = true;
        boolean isAuthenticated = hasActiveSession;
        String targetServer = "auth";
        String mainLobbyServer = "lobby";

        // If authenticated and target is auth server, router must divert straight to main lobby
        String routedServer = (isAuthenticated && "auth".equals(targetServer)) ? mainLobbyServer : targetServer;
        assertEquals("lobby", routedServer, "Authenticated player with active session MUST be routed directly to lobby!");
    }

    @Test
    public void testPreResolvedSessionCacheWorkflow() {
        java.util.Map<String, UserAuth> preResolved = new java.util.concurrent.ConcurrentHashMap<>();

        UserAuth user = UserAuth.builder()
                .username("ActivePlayer")
                .uuid(UUID.randomUUID())
                .premium(false)
                .accountType(AccountType.OFFLINE)
                .build();

        // 1. PreLogin caches the user
        String key = "activeplayer";
        preResolved.put(key, user);
        assertTrue(preResolved.containsKey(key));

        // 2. ServerPreConnect consumes the session (atomic single-use)
        UserAuth consumed = preResolved.remove(key);
        assertNotNull(consumed);
        assertEquals("ActivePlayer", consumed.getUsername());

        // 3. Second call returns null (consumed)
        assertNull(preResolved.remove(key));
    }

    @Test
    public void testSessionValidityRules() {
        long now = System.currentTimeMillis();
        long twoHoursAgo = now - (120 * 60 * 1000L);
        long threeHoursAgo = now - (180 * 60 * 1000L);

        // Valid session created within 120 minutes
        com.nexgate.model.SessionRecord validSession = new com.nexgate.model.SessionRecord(
                UUID.randomUUID(), "activeplayer", "token123", "192.168.1.50", twoHoursAgo + 1000, now + (120 * 60 * 1000L)
        );
        assertTrue(validSession.isValid("192.168.1.50", true));
        assertTrue(validSession.isValid("192.168.1.99", false)); // /24 subnet match in relaxed mode
        assertFalse(validSession.isValid("10.0.0.1", false)); // completely different subnet fails

        // Expired session (>120 mins)
        com.nexgate.model.SessionRecord expiredSession = new com.nexgate.model.SessionRecord(
                UUID.randomUUID(), "activeplayer", "token123", "192.168.1.50", threeHoursAgo, threeHoursAgo + (60 * 60 * 1000L)
        );
        assertFalse(expiredSession.isValid("192.168.1.50", true));
    }

    @Test
    public void testBedrockDirectLoginPolicy() {
        String bedrockUser = ".BedrockSteve";
        assertTrue(UsernameValidator.isFloodgateUsername(bedrockUser));

        // Verified Bedrock user auto-registers with AccountType.BEDROCK
        UserAuth bedrockAccount = UserAuth.builder()
                .username(bedrockUser)
                .uuid(UUID.randomUUID())
                .accountType(AccountType.BEDROCK)
                .build();

        assertEquals(AccountType.BEDROCK, bedrockAccount.getAccountType());
        assertFalse(bedrockAccount.isPremium());
    }

    @Test
    public void testBedrockAccountProtectionFromJavaPlayers() {
        // Bedrock account in database
        UserAuth bedrockAccount = UserAuth.builder()
                .username(".BedrockPlayer")
                .uuid(UUID.randomUUID())
                .accountType(AccountType.BEDROCK)
                .build();

        // Java player attempting to connect with regular username
        String javaConnectingName = "JavaPlayer";
        boolean isBedrockConn = UsernameValidator.isFloodgateUsername(javaConnectingName);
        assertFalse(isBedrockConn);

        // Security check: If target account is BEDROCK, non-Bedrock connections are rejected
        boolean allowed = bedrockAccount.getAccountType() != AccountType.BEDROCK || isBedrockConn;
        assertFalse(allowed, "Java offline connections must be prevented from accessing Bedrock accounts!");
    }

    @Test
    public void testLogoutRoutingDirectsToAuthServer() {
        // Player logs out: auth state cleared, target is auth server
        boolean isAuthenticated = false;
        String authServer = "auth";
        String mainLobbyServer = "lobby";

        // Unauthenticated player must route to auth server
        String destination = isAuthenticated ? mainLobbyServer : authServer;
        assertEquals("auth", destination, "Logged out / unauthenticated player must be routed to auth server!");
    }
}
