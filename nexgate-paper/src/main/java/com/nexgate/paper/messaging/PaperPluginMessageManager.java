package com.nexgate.paper.messaging;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.nexgate.crypto.HmacUtils;
import com.nexgate.message.MessageChannel;
import com.nexgate.message.MessageType;
import com.nexgate.paper.auth.PaperAuthManager;
import com.nexgate.paper.config.PaperConfigManager;
import com.nexgate.paper.NexGatePaperPlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * Handles plugin messages from Velocity proxy.
 */
public class PaperPluginMessageManager implements PluginMessageListener {
    private final NexGatePaperPlugin plugin;
    private final Logger logger;
    private final PaperConfigManager configManager;
    private final PaperAuthManager authManager;
    
    private static final long MAX_AGE_MILLIS = 30_000L;
    
    public PaperPluginMessageManager(NexGatePaperPlugin plugin, Logger logger,
                                   PaperConfigManager configManager, PaperAuthManager authManager) {
        this.plugin = plugin;
        this.logger = logger;
        this.configManager = configManager;
        this.authManager = authManager;
    }
    
    public void register() {
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, MessageChannel.MAIN_CHANNEL, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, MessageChannel.MAIN_CHANNEL);
        logger.info("Registered plugin message channels");
    }
    
    public void unregister() {
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, MessageChannel.MAIN_CHANNEL, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, MessageChannel.MAIN_CHANNEL);
    }
    
    @Override
    public void onPluginMessageReceived(String channelName, Player player, byte[] data) {
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
            if (type == MessageType.PERFORM_LOGIN) {
                handlePerformLogin(in, player);
            } else if (type == MessageType.LOGIN) {
                // Player logged in
                String username = in.readUTF();
                authManager.authenticate(player);
            } else if (type == MessageType.LOGOUT) {
                // Player logged out
                String username = in.readUTF();
                authManager.deauthenticate(player);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse plugin message", e);
        }
    }
    
    private void handlePerformLogin(ByteArrayDataInput in, Player carrier) {
        try {
            String playerName = in.readUTF();
            long timestamp = in.readLong();
            String uuidStr = in.readUTF();
            String hmac = in.readUTF();
            
            UUID uuid = uuidStr.isEmpty() ? null : UUID.fromString(uuidStr);
            String secret = configManager.getHmacSecret();
            
            if (secret.isEmpty()) {
                logger.warning("Rejected perform.login for {}: hmac-secret not configured", playerName);
                return;
            }
            
            if (Math.abs(System.currentTimeMillis() - timestamp) > MAX_AGE_MILLIS) {
                logger.warning("Rejected perform.login for {}: message expired", playerName);
                return;
            }
            
            String expectedHmac = HmacUtils.computePerformLoginHmac(secret, playerName, timestamp, uuidStr);
            if (!HmacUtils.constantTimeEquals(expectedHmac, hmac)) {
                logger.warning("Rejected perform.login for {}: invalid HMAC", playerName);
                return;
            }
            
            // Find the player
            Player target = plugin.getServer().getPlayerExact(playerName);
            if (target != null && target.isOnline()) {
                authManager.authenticate(target);
                authManager.queueProxySession(target.getUniqueId());
                logger.debug("Successfully authenticated {} via perform.login", playerName);
                
                // Send ack
                sendAck(carrier);
            } else {
                logger.debug("Player {} not online for perform.login", playerName);
            }
        } catch (Exception e) {
            logger.warn("Failed to handle perform.login", e);
        }
    }
    
    private void sendAck(Player carrier) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(MessageType.PERFORM_LOGIN_ACK.getId());
        if (carrier != null && carrier.isOnline()) {
            carrier.sendPluginMessage(plugin, MessageChannel.MAIN_CHANNEL, out.toByteArray());
        }
    }
}
