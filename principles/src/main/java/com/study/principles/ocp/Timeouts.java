package com.study.principles.ocp;

import java.time.Duration;

public record Timeouts(Duration navigation, Duration action) {
}
