package com.nexgate.paper.messaging;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.nexgate.crypto.HmacUtils;
import com.nexgate.message.MessageChannel;
import com.nexgate.message.MessageType;
import com.nexgate.paper.NexGatePaperPlugin;
import com.nexgate.paper.auth.PaperAuthManager;
import com.nexgate.paper.config.PaperConfigManager;
import com.nexgate.security.ReplayProtectionCache;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * Handles incoming HMAC-signed plugin messages from Velocity proxy on Paper.
 */
public class PaperPluginMessageManager implements PluginMessageListener {
    private final NexGatePaperPlugin plugin;
    private final Logger logger;
    private final PaperConfigManager configManager;
    private final PaperAuthManager authManager;
    private final ReplayProtectionCache replayProtection;

    private static final long MAX_AGE_MILLIS = 30_000L;

    public PaperPluginMessageManager(NexGatePaperPlugin plugin, Logger logger,
                                     PaperConfigManager configManager, PaperAuthManager authManager) {
        this.plugin = plugin;
        this.logger = logger;
        this.configManager = configManager;
        this.authManager = authManager;
        this.replayProtection = new ReplayProtectionCache(MAX_AGE_MILLIS);
    }

    public void register() {
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, MessageChannel.MAIN_CHANNEL, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, MessageChannel.MAIN_CHANNEL);
        logger.info("Registered Paper plugin message channels");
    }

    public void unregister() {
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, MessageChannel.MAIN_CHANNEL, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, MessageChannel.MAIN_CHANNEL);
    }

    @Override
    public void onPluginMessageReceived(String channelName, Player carrier, byte[] data) {
        if (!MessageChannel.MAIN_CHANNEL.equals(channelName)) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(data);
        try {
            String typeId = in.readUTF();
            Optional<MessageType> typeOpt = MessageType.fromId(typeId);
            if (typeOpt.isEmpty()) {
                logger.debug("Received unknown message type: {}", typeId);
                return;
            }

            MessageType type = typeOpt.get();
            switch (type) {
                case PERFORM_LOGIN -> handlePerformLogin(in, carrier);
                case SYNC_SETTINGS -> handleSyncSettings(in);
                case LOGOUT -> handleLogout(in);
                default -> logger.debug("Unhandled message type: {}", type);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse plugin message", e);
        }
    }

    private void handlePerformLogin(ByteArrayDataInput in, Player carrier) {
        try {
            String playerName = in.readUTF();
            String nonce = in.readUTF();
            long timestamp = in.readLong();
            String uuidStr = in.readUTF();
            String color = in.readUTF();
            String style = in.readUTF();
            String hmac = in.readUTF();

            String secret = configManager.getHmacSecret();
            if (secret.isEmpty()) {
                logger.warn("Rejected perform.login for {}: hmac-secret not configured!", playerName);
                return;
            }

            // 1. Replay attack and timestamp check
            if (!replayProtection.checkAndRecord(nonce, timestamp)) {
                logger.warn("Rejected perform.login for {}: replay attack detected or message expired!", playerName);
                return;
            }

            // 2. Constant-time HMAC verification
            String expectedHmac = HmacUtils.computePerformLoginHmac(
                    secret, playerName, nonce, timestamp, uuidStr, color, style
            );

            if (!HmacUtils.constantTimeEquals(expectedHmac, hmac)) {
                logger.warn("Rejected perform.login for {}: invalid HMAC signature!", playerName);
                return;
            }

            UUID uuid = uuidStr.isEmpty() ? null : UUID.fromString(uuidStr);

            // Execute on main thread to interact safely with Bukkit entities
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player target = plugin.getServer().getPlayerExact(playerName);
                if (target != null && target.isOnline()) {
                    authManager.authenticate(target, color, style);
                    target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    sendAck(carrier != null ? carrier : target);
                    logger.info("Successfully authenticated {} on backend via signed perform.login", playerName);
                } else if (uuid != null) {
                    // Player might still be in the process of joining; queue session
                    authManager.queueProxySession(uuid, color, style);
                    logger.debug("Queued proxy session for {} ({})", playerName, uuid);
                }
            });
        } catch (Exception e) {
            logger.warn("Failed to process perform.login", e);
        }
    }

    private void handleSyncSettings(ByteArrayDataInput in) {
        try {
            String playerName = in.readUTF();
            String color = in.readUTF();
            String style = in.readUTF();

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player target = plugin.getServer().getPlayerExact(playerName);
                if (target != null) {
                    authManager.setPlayerSettings(target.getUniqueId(), color, style);
                    logger.debug("Updated message appearance for {} to color={}, style={}", playerName, color, style);
                }
            });
        } catch (Exception e) {
            logger.warn("Failed to process sync.settings", e);
        }
    }

    private void handleLogout(ByteArrayDataInput in) {
        try {
            String playerName = in.readUTF();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player target = plugin.getServer().getPlayerExact(playerName);
                if (target != null) {
                    authManager.deauthenticate(target);
                }
            });
        } catch (Exception e) {
            logger.warn("Failed to process logout", e);
        }
    }

    private void sendAck(Player carrier) {
        if (carrier != null && carrier.isOnline()) {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF(MessageType.PERFORM_LOGIN_ACK.getId());
            carrier.sendPluginMessage(plugin, MessageChannel.MAIN_CHANNEL, out.toByteArray());
        }
    }
}
