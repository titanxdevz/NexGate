package com.nexgate;

import com.nexgate.security.TotpUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class TotpUtilsTest {

    @Test
    public void testSecretGenerationAndFormat() {
        String secret = TotpUtils.generateSecret();
        assertNotNull(secret);
        assertEquals(32, secret.length(), "160-bit (20-byte) secret encoded in Base32 is 32 chars");
        assertTrue(secret.matches("^[A-Z2-7]+$"), "Base32 must contain only A-Z and 2-7");
    }

    @Test
    public void testTotpVerificationCurrentCode() {
        String secret = TotpUtils.generateSecret();
        String currentCode = TotpUtils.generateCurrentCode(secret);
        assertNotNull(currentCode);
        assertEquals(6, currentCode.length());

        // Verifying current code with 0 or 1 window steps must succeed
        assertTrue(TotpUtils.verifyCode(secret, currentCode, 0));
        assertTrue(TotpUtils.verifyCode(secret, currentCode, 1));

        // Invalid codes must fail
        assertFalse(TotpUtils.verifyCode(secret, "000000".equals(currentCode) ? "999999" : "000000", 1));
        assertFalse(TotpUtils.verifyCode(secret, "123", 1)); // too short
        assertFalse(TotpUtils.verifyCode(secret, "invalid", 1));
        assertFalse(TotpUtils.verifyCode(secret, null, 1));
        assertFalse(TotpUtils.verifyCode(null, currentCode, 1));
    }

    @Test
    public void testRecoveryCodesLifecycle() {
        List<String> plainCodes = TotpUtils.generatePlainRecoveryCodes(8);
        assertNotNull(plainCodes);
        assertEquals(8, plainCodes.size());

        for (String code : plainCodes) {
            assertEquals(9, code.length(), "Formatted as XXXX-XXXX (9 chars)");
            assertTrue(code.contains("-"));
        }

        List<String> hashedCodes = TotpUtils.hashRecoveryCodes(plainCodes);
        assertEquals(8, hashedCodes.size());

        // Verify valid recovery code match
        String codeToUse = plainCodes.get(0);
        Optional<String> matched = TotpUtils.verifyAndFindRecoveryCodeHash(codeToUse, hashedCodes);
        assertTrue(matched.isPresent());
        assertEquals(hashedCodes.get(0), matched.get());

        // Verify case and whitespace tolerance
        Optional<String> matchedCase = TotpUtils.verifyAndFindRecoveryCodeHash(
                "  " + codeToUse.toLowerCase() + "  ", hashedCodes);
        assertTrue(matchedCase.isPresent());

        // Invalid recovery code must fail
        Optional<String> invalidMatch = TotpUtils.verifyAndFindRecoveryCodeHash("XXXX-YYYY", hashedCodes);
        assertTrue(invalidMatch.isEmpty());
    }

    @Test
    public void testTotpUriFormatting() {
        String secret = "JBSWY3DPEHPK3PXP";
        String uri = TotpUtils.formatTotpUri("NexGate", "Steve", secret);
        assertNotNull(uri);
        assertTrue(uri.startsWith("otpauth://totp/"));
        assertTrue(uri.contains("secret=" + secret));
        assertTrue(uri.contains("issuer=NexGate"));
    }
}
