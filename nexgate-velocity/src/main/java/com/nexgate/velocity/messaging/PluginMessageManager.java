package com.nexgate.velocity.messaging;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.nexgate.crypto.HashingUtils;
import com.nexgate.crypto.HmacUtils;
import com.nexgate.message.MessageChannel;
import com.nexgate.message.MessageType;
import com.nexgate.velocity.auth.AuthManager;
import com.nexgate.velocity.config.ConfigManager;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * Manages authenticated plugin messaging between Velocity proxy and Paper backend companions.
 * Every privileged message is signed with HMAC-SHA256, unique nonce, and millisecond timestamp.
 */
public class PluginMessageManager {
    private final ProxyServer server;
    private final Logger logger;
    private final ConfigManager configManager;
    private final AuthManager authManager;
    private final ChannelIdentifier channel;

    public PluginMessageManager(ProxyServer server, Logger logger, ConfigManager configManager,
                                AuthManager authManager) {
        this.server = server;
        this.logger = logger;
        this.configManager = configManager;
        this.authManager = authManager;
        this.channel = MinecraftChannelIdentifier.from(MessageChannel.MAIN_CHANNEL);
    }

    public void register(Object pluginInstance) {
        server.getChannelRegistrar().register(channel);
        server.getEventManager().register(pluginInstance, new MessageListener());
        logger.info("Registered plugin message channel: {}", channel.getId());
    }

    public void unregister() {
        server.getChannelRegistrar().unregister(channel);
    }

    /**
     * Sends signed HMAC perform.login message to the backend server with replay protection.
     */
    public void sendPerformLogin(RegisteredServer targetServer, String playerName, UUID uuid,
                                 String messageColor, String messageStyle) {
        if (targetServer == null) return;

        long timestamp = System.currentTimeMillis();
        String nonce = HashingUtils.generateSecureToken(16);
        String uuidStr = uuid == null ? "" : uuid.toString();
        String color = messageColor == null ? "WHITE" : messageColor;
        String style = messageStyle == null ? "DEFAULT" : messageStyle;

        String hmac = HmacUtils.computePerformLoginHmac(
                configManager.getHmacSecret(), playerName, nonce, timestamp, uuidStr, color, style
        );

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(MessageType.PERFORM_LOGIN.getId());
        out.writeUTF(playerName);
        out.writeUTF(nonce);
        out.writeLong(timestamp);
        out.writeUTF(uuidStr);
        out.writeUTF(color);
        out.writeUTF(style);
        out.writeUTF(hmac);

        targetServer.sendPluginMessage(channel, out.toByteArray());
        logger.debug("Sent signed perform.login for {} to server {}", playerName, targetServer.getServerInfo().getName());
    }

    /**
     * Broadcasts authenticated color & style settings update to a server.
     */
    public void sendSyncSettings(RegisteredServer targetServer, String playerName, UUID uuid, String messageColor, String messageStyle) {
        if (targetServer == null) return;

        long timestamp = System.currentTimeMillis();
        String nonce = HashingUtils.generateSecureToken(16);
        String uuidStr = uuid == null ? "" : uuid.toString();
        String color = messageColor != null ? messageColor : "WHITE";
        String style = messageStyle != null ? messageStyle : "DEFAULT";
        String payload = color + ":" + style;

        String hmac = HmacUtils.computeCanonicalHmac(
                configManager.getHmacSecret(), HmacUtils.CURRENT_PROTOCOL_VERSION,
                MessageType.SYNC_SETTINGS.getId(), "velocity", uuidStr, playerName,
                nonce, timestamp, payload
        );

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(MessageType.SYNC_SETTINGS.getId());
        out.writeUTF(playerName);
        out.writeUTF(nonce);
        out.writeLong(timestamp);
        out.writeUTF(uuidStr);
        out.writeUTF(color);
        out.writeUTF(style);
        out.writeUTF(hmac);

        targetServer.sendPluginMessage(channel, out.toByteArray());
        logger.debug("Sent signed sync.settings for {} to server {}", playerName, targetServer.getServerInfo().getName());
    }

    /**
     * Broadcasts authenticated logout state to a server.
     */
    public void sendLogoutState(RegisteredServer targetServer, String playerName, UUID uuid) {
        if (targetServer == null) return;

        long timestamp = System.currentTimeMillis();
        String nonce = HashingUtils.generateSecureToken(16);
        String uuidStr = uuid == null ? "" : uuid.toString();

        String hmac = HmacUtils.computeCanonicalHmac(
                configManager.getHmacSecret(), HmacUtils.CURRENT_PROTOCOL_VERSION,
                MessageType.LOGOUT.getId(), "velocity", uuidStr, playerName,
                nonce, timestamp, "logout"
        );

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(MessageType.LOGOUT.getId());
        out.writeUTF(playerName);
        out.writeUTF(nonce);
        out.writeLong(timestamp);
        out.writeUTF(uuidStr);
        out.writeUTF(hmac);

        targetServer.sendPluginMessage(channel, out.toByteArray());
        logger.debug("Sent signed logout for {} to server {}", playerName, targetServer.getServerInfo().getName());
    }

    public class MessageListener {
        @com.velocitypowered.api.event.Subscribe
        public void onPluginMessage(PluginMessageEvent event) {
            if (!event.getIdentifier().equals(channel)) {
                return;
            }

            event.setResult(PluginMessageEvent.ForwardResult.handled());

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
                if (type == MessageType.PERFORM_LOGIN_ACK) {
                    logger.debug("Received perform.login.ack from backend");
                } else if (type == MessageType.PROXY_STARTED) {
                    logger.debug("Received proxy.started announcement from backend");
                }
            } catch (Exception e) {
                logger.warn("Failed to parse plugin message from backend", e);
            }
        }
    }
}
