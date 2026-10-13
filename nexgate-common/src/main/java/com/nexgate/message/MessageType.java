package com.nexgate.message;

import java.util.Arrays;
import java.util.Optional;

/**
 * Message types for proxy<->backend communication.
 */
public enum MessageType {
    LOGIN("login", true),                              // backend -> proxy: player logged in
    LOGOUT("logout", true),                            // backend -> proxy: player logged out
    PERFORM_LOGIN("perform.login", false),             // proxy -> backend: signed auto-login
    PERFORM_LOGIN_ACK("perform.login.ack", false),     // backend -> proxy: ack
    PROXY_STARTED("proxy.started", false),             // proxy -> backend: proxy ready
    PREMIUM_LIST("premium.list", false),               // backend -> proxy: full premium list
    PREMIUM_LIST_CHUNK("premium.list.chunk", false),   // backend -> proxy: chunked list
    STATE_UPDATE("state.update", true),                // generic state update
    SYNC_SETTINGS("sync.settings", true),              // sync player color/style settings
    FORCE_LOGOUT("force.logout", false);               // force logout session

    private final String id;
    private final boolean broadcast;

    MessageType(String id, boolean broadcast) {
        this.id = id;
        this.broadcast = broadcast;
    }

    public String getId() {
        return id;
    }

    public boolean isBroadcast() {
        return broadcast;
    }

    public static Optional<MessageType> fromId(String id) {
        return Arrays.stream(values())
                .filter(type -> type.id.equals(id))
                .findFirst();
    }
}
