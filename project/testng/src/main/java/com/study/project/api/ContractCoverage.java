package com.study.project.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Which responses of the contract the run has seen: every call of every test counts ({@link ContractFilter} records
 * it), contract tests and functional ones alike. It answers what the contract tests cannot say about themselves:
 * a response declared in {@code openapi.yaml} that nobody asked for (a new status, a new operation without a row in
 * {@code UsersContractTest}), and a response the SUT gave that the contract does not declare (ADR 0015).
 *
 * <p>Thread-safe: parallel tests record into one instance per run.
 */
public final class ContractCoverage {

    private final Contract contract;
    private final Map<String, LongAdder> calls = new ConcurrentHashMap<>();

    public ContractCoverage(Contract contract) {
        this.contract = contract;
    }

    /** One call; a path no operation of the contract matches is kept as it was sent. */
    void record(String method, String path, int status) {
        String operation = contract.operation(method, path).orElse(method + " " + path);
        calls.computeIfAbsent(operation + " " + status, response -> new LongAdder()).increment();
    }

    public long calls() {
        return calls.values().stream().mapToLong(LongAdder::sum).sum();
    }

    /** Declared and never seen in this run. */
    public List<String> missing() {
        return contract.responses().stream().filter(response -> !calls.containsKey(response)).toList();
    }

    /** Seen in this run and not declared: each one is also a contract violation. */
    public List<String> undeclared() {
        return calls.keySet().stream().filter(response -> !contract.responses().contains(response)).sorted().toList();
    }

    /**
     * Markdown: the declared responses with their number of calls, then what the contract does not declare.
     * <pre>
     * | GET /api/users/{id} 404 | 7 |
     * | DELETE /api/users/{id} 400 | 0 — never seen |
     * </pre>
     */
    public String report() {
        List<String> declared = contract.responses();
        List<String> lines = new ArrayList<>();
        lines.add("# Contract coverage");
        lines.add("");
        lines.add((declared.size() - missing().size()) + " of " + declared.size() + " responses of openapi.yaml seen in "
                + calls() + " calls. A partial run (-Dgroups, -Dtest) sees part of the API.");
        lines.add("");
        lines.add("| Response | Calls |");
        lines.add("|---|---|");
        declared.forEach(response -> {
            long count = count(response);
            lines.add("| " + response + " | " + count + (count == 0 ? " — never seen" : "") + " |");
        });
        List<String> undeclared = undeclared();
        if (!undeclared.isEmpty()) {
            lines.add("");
            lines.add("Seen but not in the contract (each is a contract violation too):");
            lines.add("");
            undeclared.forEach(response -> lines.add("- " + response + " — " + count(response) + " calls"));
        }
        return String.join("\n", lines) + "\n";
    }

    private long count(String response) {
        LongAdder count = calls.get(response);
        return count == null ? 0 : count.sum();
    }
}
