package com.nexgate.velocity.messaging;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.nexgate.crypto.HmacUtils;
import com.nexgate.message.MessageChannel;
import com.nexgate.message.MessageType;
import com.nexgate.velocity.auth.AuthManager;
import com.nexgate.velocity.config.ConfigManager;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages plugin messaging between Velocity and Paper backends.
 */
public class PluginMessageManager {
    private final ProxyServer server;
    private final Logger logger;
    private final ConfigManager configManager;
    private final AuthManager authManager;
    private final ChannelIdentifier channel;
    
    private static final long MAX_AGE_MILLIS = 30_000L;
    
    public PluginMessageManager(ProxyServer server, Logger logger, ConfigManager configManager,
                                 AuthManager authManager) {
        this.server = server;
        this.logger = logger;
        this.configManager = configManager;
        this.authManager = authManager;
        this.channel = MinecraftChannelIdentifier.from(MessageChannel.MAIN_CHANNEL);
    }
    
    public void register() {
        server.getChannelRegistrar().register(channel);
        server.getEventManager().register(this, new MessageListener());
        logger.info("Registered plugin message channel: {}", channel.getId());
    }
    
    public void unregister() {
        server.getChannelRegistrar().unregister(channel);
    }
    
    public void sendPerformLogin(RegisteredServer server, String playerName, UUID verifiedPremiumUuid) {
        long timestamp = System.currentTimeMillis();
        String uuidStr = verifiedPremiumUuid == null ? "" : verifiedPremiumUuid.toString();
        String hmac = HmacUtils.computePerformLoginHmac(configManager.getHmacSecret(), playerName, timestamp, uuidStr);
        
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(MessageType.PERFORM_LOGIN.getId());
        out.writeUTF(playerName);
        out.writeLong(timestamp);
        out.writeUTF(uuidStr);
        out.writeUTF(hmac);
        
        server.sendPluginMessage(channel, out.toByteArray());
        logger.debug("Sent perform.login for {} to {}", playerName, server.getServerInfo().getName());
    }
    
    public void sendLoginState(RegisteredServer server, String playerName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(MessageType.LOGIN.getId());
        out.writeUTF(playerName);
        server.sendPluginMessage(channel, out.toByteArray());
    }
    
    public void sendLogoutState(RegisteredServer server, String playerName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(MessageType.LOGOUT.getId());
        out.writeUTF(playerName);
        server.sendPluginMessage(channel, out.toByteArray());
    }
    
    public class MessageListener {
        // Handle incoming messages
        @com.velocitypowered.api.event.Subscribe
        public void onPluginMessage(com.velocitypowered.api.event.connection.PluginMessageEvent event) {
            if (!event.getIdentifier().equals(channel)) {
                return;
            }
            
            event.setResult(com.velocitypowered.api.event.connection.PluginMessageEvent.ForwardResult.handled());
            
            if (!(event.getSource() instanceof RegisteredServer)) {
                return;
            }
            
            ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
            try {
                String typeId = in.readUTF();
                Optional<MessageType> typeOpt = MessageType.fromId(typeId);
                if (typeOpt.isEmpty()) {
                    return;
                }
                
                MessageType type = typeOpt.get();
                if (type == MessageType.PROXY_STARTED) {
                    // Backend says it's ready - could sync state if needed
                    logger.debug("Received proxy.started from backend");
                } else if (type == MessageType.PERFORM_LOGIN_ACK) {
                    // Ack from backend
                    logger.debug("Received perform.login.ack");
                }
            } catch (Exception e) {
                logger.warn("Failed to parse plugin message", e);
            }
        }
    }
}
