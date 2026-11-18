package com.study.principles.concurrency.events.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * HMAC-SHA256 over the <b>raw body</b>, hex-encoded: the scheme of most webhook providers (Stripe, GitHub,
 * Shopify differ only in header names and prefixes). Verify the bytes as received, before parsing: a parsed
 * and re-serialized JSON is not the same bytes.
 */
public final class Signatures {

    private Signatures() {
    }

    public static String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    /** Constant-time comparison: {@code equals} would leak how many leading characters match. */
    public static boolean verify(String secret, String body, String signature) {
        if (signature == null) {
            return false;
        }
        return MessageDigest.isEqual(sign(secret, body).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }
}
