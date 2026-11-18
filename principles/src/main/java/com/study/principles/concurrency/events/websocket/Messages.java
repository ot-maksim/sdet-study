package com.study.principles.concurrency.events.websocket;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The tiny JSON protocol spoken by {@link FakeWsServer}, shaped like the usual "subscribe to a channel"
 * protocols over WebSocket (STOMP {@code SUBSCRIBE} with a receipt, Socket.IO with an ack, GraphQL subscriptions):
 *
 * <pre>
 * client → server   {"op":"subscribe","channel":"orders"}
 * server → client   {"type":"subscribed","channel":"orders"}          the ack: from now on pushes reach you
 * server → client   {"channel":"orders","type":"OrderStatus","orderId":"order-1","status":"PAID"}
 * client → server   {"op":"unsubscribe","channel":"orders"}
 * </pre>
 *
 * Flat string fields only, so a regex is enough; a real client would use a JSON library.
 */
public final class Messages {

    private Messages() {
    }

    public static String subscribe(String channel) {
        return "{\"op\":\"subscribe\",\"channel\":\"" + channel + "\"}";
    }

    public static String unsubscribe(String channel) {
        return "{\"op\":\"unsubscribe\",\"channel\":\"" + channel + "\"}";
    }

    public static String subscribed(String channel) {
        return "{\"type\":\"subscribed\",\"channel\":\"" + channel + "\"}";
    }

    public static String orderStatus(String orderId, String status) {
        return "{\"channel\":\"" + FakeOrderStatusService.CHANNEL + "\",\"type\":\"OrderStatus\",\"orderId\":\""
                + orderId + "\",\"status\":\"" + status + "\"}";
    }

    /** The value of a flat string field, or {@code null} if there is none. */
    public static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }
}
