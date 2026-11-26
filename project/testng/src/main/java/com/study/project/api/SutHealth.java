package com.study.project.api;

/** {@code GET /health} for whoever checks a stand before the run; the generated client stays in this layer. */
public final class SutHealth {

    private final ApiTransport api;

    public SutHealth(ApiTransport api) {
        this.api = api;
    }

    /** Throws {@link ApiException} with the exchange unless the SUT answers 200. */
    public void expectUp() {
        check().expect(200);
    }

    /** The answer whatever it is: for the contract tests. */
    public ApiResponse check() {
        return api.send(client -> client.health().health());
    }
}
