package com.study.framework.core.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class UniqueTest {

    @Test
    void readableValuesCarryTheRunId() {
        Unique unique = new Unique("k3x9q");

        assertThat(unique.email("ada")).isEqualTo("ada-k3x9q-1@example.test");
        assertThat(unique.name("Ada")).isEqualTo("Ada k3x9q-2");
    }

    @Test
    void distinctAcrossThreadsAndRuns() {
        Unique unique = new Unique();
        Set<String> seen = ConcurrentHashMap.newKeySet();

        IntStream.range(0, 10_000).parallel().forEach(i -> seen.add(unique.suffix()));

        assertThat(seen).hasSize(10_000);
        assertThat(new Unique().runId()).isNotEqualTo(unique.runId()).matches("[a-z0-9]{5}");
    }

    @Test
    void runIdIsSafeForEmailsAndUrls() {
        assertThatThrownBy(() -> new Unique("Run 1")).isInstanceOf(IllegalArgumentException.class);
    }
}
