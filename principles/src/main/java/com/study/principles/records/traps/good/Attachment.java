package com.study.principles.records.traps.good;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * GOOD: an array component handled explicitly — copies in, copies out, content-based equality.
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><b>Defensive copy in the compact constructor and in the accessor</b> — unlike {@code List.copyOf},
 *       there is no read-only array, so the accessor has to copy on every call.</li>
 *   <li><b>{@code equals}/{@code hashCode} via {@link Arrays}</b> — equality by content, consistent hash.</li>
 *   <li><b>{@code toString} with size and a short hex prefix</b> — enough to see a diff, not a megabyte
 *       of bytes in the log.</li>
 * </ul>
 * Once you override equals, hashCode, toString and the accessor, the record gives you little beyond the
 * canonical constructor. Alternatives: a {@code List<Byte>} or {@code ByteBuffer} component (immutable
 * view, value equality for free), or comparing a checksum instead of keeping the bytes.
 *
 * @param name    file name
 * @param content raw bytes (copied on the way in and out)
 * @see com.study.principles.records.traps.bad.Attachment the broken version
 */
public record Attachment(String name, byte[] content) {
    private static final int PREVIEW_BYTES = 8;

    public Attachment {
        Objects.requireNonNull(name, "name");
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Attachment that
                && name.equals(that.name)
                && Arrays.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return 31 * name.hashCode() + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        int shown = Math.min(content.length, PREVIEW_BYTES);
        String preview = HexFormat.of().formatHex(content, 0, shown) + (content.length > shown ? "…" : "");
        return "Attachment[name=" + name + ", size=" + content.length + ", content=" + preview + "]";
    }
}
