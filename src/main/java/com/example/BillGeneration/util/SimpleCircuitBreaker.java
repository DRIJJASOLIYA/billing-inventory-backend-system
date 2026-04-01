package com.example.BillGeneration.util;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class SimpleCircuitBreaker {

    private final int failureThreshold;
    private final Duration openDuration;
    private final AtomicInteger failures = new AtomicInteger(0);
    private final AtomicReference<Instant> openUntil = new AtomicReference<>(Instant.EPOCH);

    public SimpleCircuitBreaker(int failureThreshold, Duration openDuration) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDuration = openDuration.isNegative() ? Duration.ZERO : openDuration;
    }

    public boolean isOpen() {
        return Instant.now().isBefore(openUntil.get());
    }

    public void recordSuccess() {
        failures.set(0);
        openUntil.set(Instant.EPOCH);
    }

    public void recordFailure() {
        int currentFailures = failures.incrementAndGet();
        if (currentFailures >= failureThreshold) {
            openUntil.set(Instant.now().plus(openDuration));
            failures.set(0);
        }
    }
}
