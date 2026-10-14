package com.nexgate.velocity;

import com.nexgate.velocity.antibot.AntiBotManager;
import com.nexgate.velocity.antibot.CidrMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AntiBotManagerTest {

    private AntiBotManager antiBot;

    @BeforeEach
    public void setup() {
        antiBot = new AntiBotManager();
    }

    @Test
    public void testCidrMatcherIPv4() {
        CidrMatcher cidr24 = new CidrMatcher("192.168.1.0/24");
        assertTrue(cidr24.matches("192.168.1.1"));
        assertTrue(cidr24.matches("192.168.1.254"));
        assertFalse(cidr24.matches("192.168.2.1"));
        assertFalse(cidr24.matches("10.0.0.1"));

        CidrMatcher singleIp = new CidrMatcher("10.20.30.40");
        assertTrue(singleIp.matches("10.20.30.40"));
        assertFalse(singleIp.matches("10.20.30.41"));
    }

    @Test
    public void testCidrMatcherIPv6() {
        CidrMatcher v6Subnet = new CidrMatcher("2001:db8:abcd::/48");
        assertTrue(v6Subnet.matches("2001:db8:abcd:0000:0000:0000:0000:0001"));
        assertTrue(v6Subnet.matches("2001:db8:abcd:ffff:ffff:ffff:ffff:ffff"));
        assertFalse(v6Subnet.matches("2001:db8:abce:0000:0000:0000:0000:0001"));
        assertFalse(v6Subnet.matches("192.168.1.1")); // IPv4 vs IPv6
    }

    @Test
    public void testWhitelistAndBlacklistCidrs() {
        antiBot.addBlacklistCidr("10.0.0.0/8");
        antiBot.addWhitelistCidr("10.0.1.0/24"); // more specific whitelist

        // Normal IP allowed
        assertTrue(antiBot.evaluateConnection("192.168.1.50", "Player1", 763, "play.server.com").allowed());

        // Whitelisted IP allowed even if inside 10.0.0.0/8
        assertTrue(antiBot.evaluateConnection("10.0.1.25", "Player2", 763, "play.server.com").allowed());

        // Blacklisted IP rejected
        AntiBotManager.AdmissionResult denied = antiBot.evaluateConnection("10.0.2.1", "Player3", 763, "play.server.com");
        assertFalse(denied.allowed());
        assertTrue(denied.rejectionReason().contains("blocked"));
    }

    @Test
    public void testTemporaryBan() {
        String ip = "172.16.0.5";
        antiBot.addTemporaryBan(ip, 60);

        AntiBotManager.AdmissionResult denied = antiBot.evaluateConnection(ip, "Bot", 763, "");
        assertFalse(denied.allowed());
        assertTrue(denied.rejectionReason().contains("throttled"));

        antiBot.removeTemporaryBan(ip);
        assertTrue(antiBot.evaluateConnection(ip, "Bot", 763, "").allowed());
    }

    @Test
    public void testFastReconnectMitigation() {
        String ip = "192.168.10.15";

        // Initial connect and disconnect
        antiBot.onPlayerConnected(ip);
        antiBot.onPlayerDisconnected(ip);

        // Immediate reconnect within threshold (<1200ms) triggers burst rejection
        AntiBotManager.AdmissionResult burst = antiBot.evaluateConnection(ip, "FastBot", 763, "");
        assertFalse(burst.allowed());
        assertTrue(burst.rejectionReason().contains("burst"));
    }

    @Test
    public void testUnderAttackModeManualAndAuto() {
        assertFalse(antiBot.isMitigationActive());

        // Manual ON
        antiBot.setMode(AntiBotManager.Mode.ON);
        assertTrue(antiBot.isMitigationActive());

        // Manual OFF
        antiBot.setMode(AntiBotManager.Mode.OFF);
        assertFalse(antiBot.isMitigationActive());

        // AUTO mode
        antiBot.setMode(AntiBotManager.Mode.AUTO);
        assertFalse(antiBot.isMitigationActive());

        antiBot.activateMitigation("Simulated flood test");
        assertTrue(antiBot.isMitigationActive());

        antiBot.deactivateMitigation();
        assertFalse(antiBot.isMitigationActive());
    }

    @Test
    public void testConcurrentConnectionsPerIp() {
        antiBot.setMaxConcurrentPerIp(2);
        antiBot.setFastReconnectThresholdMs(0); // isolate concurrency limit from fast-reconnect check
        String ip = "192.168.20.1";

        // 1st connection
        assertTrue(antiBot.evaluateConnection(ip, "User1", 763, "").allowed());
        antiBot.onPlayerConnected(ip);

        // 2nd connection
        assertTrue(antiBot.evaluateConnection(ip, "User2", 763, "").allowed());
        antiBot.onPlayerConnected(ip);

        // 3rd connection exceeds limit!
        AntiBotManager.AdmissionResult third = antiBot.evaluateConnection(ip, "User3", 763, "");
        assertFalse(third.allowed());
        assertTrue(third.rejectionReason().contains("Maximum simultaneous"));

        // One disconnects
        antiBot.onPlayerDisconnected(ip);

        // Now connection allowed again
        assertTrue(antiBot.evaluateConnection(ip, "User3", 763, "").allowed());
    }

    @Test
    public void testMetricsTracking() {
        antiBot.addBlacklistCidr("192.0.2.0/24");
        antiBot.evaluateConnection("192.0.2.1", "Bot1", 763, "");
        antiBot.evaluateConnection("1.1.1.1", "Normal", 763, "");

        AntiBotManager.Metrics metrics = antiBot.getMetrics();
        assertEquals(2, metrics.evaluated);
        assertEquals(1, metrics.rejected);
        assertEquals(1, metrics.blacklisted);
    }
}
