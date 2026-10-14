package com.nexgate;

import com.nexgate.crypto.Argon2Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class Argon2ServiceTest {
    private static Argon2Service argon2Service;

    @BeforeAll
    public static void setup() {
        // Use lightweight parameters for test speed: 2 iterations, 8MB memory, 1 thread
        argon2Service = new Argon2Service(2, 2, 8192, 1);
    }

    @AfterAll
    public static void tearDown() {
        if (argon2Service != null) {
            argon2Service.close();
        }
    }

    @Test
    public void testHashAndVerifySync() {
        String password = "CorrectHorseBatteryStaple!";
        String hash = argon2Service.hashPassword(password);
        assertNotNull(hash);
        assertTrue(hash.startsWith("$argon2id$"));

        assertTrue(argon2Service.verifyPassword(password, hash));
        assertFalse(argon2Service.verifyPassword("WrongPassword", hash));
        assertFalse(argon2Service.verifyPassword("", hash));
    }

    @Test
    public void testHashAndVerifyAsync() throws Exception {
        String password = "AsyncP@ssword2026";
        String hash = argon2Service.hashPasswordAsync(password).get();
        assertNotNull(hash);

        boolean ok = argon2Service.verifyPasswordAsync(password, hash).get();
        assertTrue(ok);

        boolean wrong = argon2Service.verifyPasswordAsync("BadPass", hash).get();
        assertFalse(wrong);
    }

    @Test
    public void testConcurrentAsyncHashing() {
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            futures.add(argon2Service.hashPasswordAsync("Password" + i));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        for (CompletableFuture<String> f : futures) {
            String hash = f.join();
            assertNotNull(hash);
            assertTrue(hash.startsWith("$argon2id$"));
        }
    }

    @Test
    public void testNeedsRehash() {
        // Hash created with 8192 memory
        String hash = argon2Service.hashPassword("TestPass");
        assertFalse(argon2Service.needsRehash(hash));

        // Fake legacy hash with low parameters (m=4096, t=1)
        String legacyHash = "$argon2id$v=19$m=4096,t=1,p=1$c29tZXNhbHQ$c29tZWhhc2g";
        assertTrue(argon2Service.needsRehash(legacyHash));
    }
}
