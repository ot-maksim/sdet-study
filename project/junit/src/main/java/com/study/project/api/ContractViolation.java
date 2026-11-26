package com.study.project.api;

import java.util.List;

/**
 * A response that breaks the contract, seen by {@link ContractFilter} in a call that is not a contract test.
 *
 * @param call     {@code GET /api/users/7 -> 200}
 * @param problems what the validator found, one line each
 */
public record ContractViolation(String call, List<String> problems) {

    public ContractViolation {
        problems = List.copyOf(problems);
    }

    @Override
    public String toString() {
        return call + " breaks the SUT's contract (openapi.yaml):\n" + problems.stream()
                .map(problem -> "  - " + problem)
                .reduce((a, b) -> a + "\n" + b).orElse("");
    }
}
