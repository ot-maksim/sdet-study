package com.study.principles.async;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A resource a listener writes to and the test releases in its cleanup: a report file, a step log, a mock.
 * Writing after {@link #close()} throws, like a closed stream, and is counted, so a test can assert that the
 * listener touched the resource after it was released.
 */
public final class ReportFile {

    private final List<String> lines = new CopyOnWriteArrayList<>();
    private final AtomicInteger writesAfterClose = new AtomicInteger();
    private volatile boolean closed;

    public void write(String line) {
        if (closed) {
            writesAfterClose.incrementAndGet();
            throw new IllegalStateException("Report is closed, cannot write: " + line);
        }
        lines.add(line);
    }

    public void close() {
        closed = true;
    }

    public List<String> lines() {
        return List.copyOf(lines);
    }

    public int writesAfterClose() {
        return writesAfterClose.get();
    }
}
