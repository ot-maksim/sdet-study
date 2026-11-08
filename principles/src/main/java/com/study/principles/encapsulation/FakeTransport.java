package com.study.principles.encapsulation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Fake stand-in for RestAssured / Playwright: records what would go over the wire instead of sending it.
 * Lets the kata show wrong URLs and timeouts without a real SUT.
 */
public class FakeTransport {

    public record Call(String action, String url, Duration timeout) {
        @Override
        public String toString() {
            return action + " " + url + " (timeout " + timeout + ")";
        }
    }

    private final List<Call> calls = new ArrayList<>();

    public void send(String action, String url, Duration timeout) {
        calls.add(new Call(action, url, timeout));
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    public Call last() {
        if (calls.isEmpty()) throw new IllegalStateException("nothing was sent");
        return calls.get(calls.size() - 1);
    }
}
