package com.portfolio.resilient.config;

import com.portfolio.resilient.downstream.DownstreamMode;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.inventory")
public class InventoryProperties {

    /**
     * Client-side deadline for one inventory attempt. A SLOW stub that takes at least this long
     * fails the attempt, which retry and the circuit breaker then see as a dependency failure.
     */
    private Duration callTimeout = Duration.ofMillis(400);

    /** How long {@link DownstreamMode#SLOW} blocks before returning. */
    private Duration slowDelay = Duration.ofMillis(800);

    /**
     * FLAKY mode throws this many times, then succeeds. Kept below the retry max-attempts
     * (3) so a single client call can demonstrate a recovered retry.
     */
    private int flakyFailuresBeforeSuccess = 2;

    private DownstreamMode defaultMode = DownstreamMode.SUCCESS;

    public Duration getCallTimeout() {
        return callTimeout;
    }

    public void setCallTimeout(Duration callTimeout) {
        this.callTimeout = callTimeout;
    }

    public Duration getSlowDelay() {
        return slowDelay;
    }

    public void setSlowDelay(Duration slowDelay) {
        this.slowDelay = slowDelay;
    }

    public int getFlakyFailuresBeforeSuccess() {
        return flakyFailuresBeforeSuccess;
    }

    public void setFlakyFailuresBeforeSuccess(int flakyFailuresBeforeSuccess) {
        this.flakyFailuresBeforeSuccess = flakyFailuresBeforeSuccess;
    }

    public DownstreamMode getDefaultMode() {
        return defaultMode;
    }

    public void setDefaultMode(DownstreamMode defaultMode) {
        this.defaultMode = defaultMode;
    }
}
