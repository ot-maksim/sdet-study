package com.study.principles.concurrency.events.websocket.good;

/** The connection is gone: the test fails with this reason at once instead of waiting out its timeout. */
public final class ConnectionLostException extends IllegalStateException {

    public ConnectionLostException(String reason) {
        super("WebSocket closed (" + reason + ")");
    }

    public ConnectionLostException(String reason, String channel) {
        super("WebSocket closed (" + reason + ") while waiting on '" + channel + "'");
    }
}
