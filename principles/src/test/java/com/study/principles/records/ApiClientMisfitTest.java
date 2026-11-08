package com.study.principles.records;

import com.study.principles.records.misfit.bad.UsersApiClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiClientMisfitTest {
    private static final String BASE_URL = "http://localhost:7070";
    private static final String TOKEN = "eyJhbGciOi.secret";

    @Test
    void recordClientExposesTheTokenEverywhere() {
        var client = new UsersApiClient(BASE_URL, TOKEN, new ArrayList<>());

        assertThat(client.token()).isEqualTo(TOKEN);
        assertThat(client.toString()).contains(TOKEN);
    }

    @Test
    void anyCallerCanRewriteTheRecordClientHistory() {
        var client = new UsersApiClient(BASE_URL, TOKEN, new ArrayList<>());
        client.getUser(42);

        client.sentRequests().clear();

        assertThat(client.sentRequests()).isEmpty();
    }

    @Test
    void recordClientGetsLostInAHashSetAfterARequest() {
        var client = new UsersApiClient(BASE_URL, TOKEN, new ArrayList<>());
        var clients = new HashSet<UsersApiClient>();
        clients.add(client);

        client.getUser(42);

        assertThat(clients.contains(client)).isFalse();
    }

    @Test
    void classClientHidesInternals() {
        var client = new com.study.principles.records.misfit.good.UsersApiClient(BASE_URL, TOKEN);
        var clients = new HashSet<com.study.principles.records.misfit.good.UsersApiClient>();
        clients.add(client);

        client.getUser(42);

        assertThat(client.toString()).doesNotContain(TOKEN);
        assertThat(clients).contains(client);
        assertThat(client.sentRequests()).containsExactly("GET " + BASE_URL + "/users/42");
        assertThatThrownBy(() -> client.sentRequests().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
