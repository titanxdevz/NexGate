package com.nexgate.velocity;

import com.nexgate.velocity.config.ConfigManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class VelocityConfigTest {

    @Test
    public void testConfigSaveAndLoad(@TempDir Path tempDir) {
        var logger = LoggerFactory.getLogger(VelocityConfigTest.class);
        ConfigManager configManager = new ConfigManager(logger, tempDir);

        configManager.load();
        assertNotNull(configManager.getConfig());
        assertNotNull(configManager.getHmacSecret());
        assertFalse(configManager.getHmacSecret().isEmpty());
        assertEquals(43, configManager.getHmacSecret().length(), "Base64-encoded 32-byte secret length");
        assertEquals("sqlite", configManager.getConfig().database.type);
        assertEquals(60, configManager.getLoginTimeoutSeconds());
        assertEquals(120, configManager.getSessionTimeoutMinutes());
        assertTrue(configManager.isRequireAuth());
        assertTrue(configManager.isPremiumEnabled());
        assertTrue(configManager.isPremiumAutoDetect());
        assertTrue(configManager.isShowBossBar());
        assertTrue(configManager.isShowTitle());
        assertTrue(configManager.isPlaySounds());

        // Modify config and save
        configManager.getConfig().auth.loginTimeoutSeconds = 90;
        configManager.getConfig().auth.targetServer = "survival";
        configManager.saveDefault();

        // Reload
        ConfigManager reloaded = new ConfigManager(logger, tempDir);
        reloaded.load();
        assertEquals(90, reloaded.getLoginTimeoutSeconds());
        assertEquals("survival", reloaded.getTargetServer());
    }
}
