package com.portfolio.resilient.health;

import com.portfolio.resilient.downstream.DownstreamModeService;
import com.portfolio.resilient.downstream.InventoryStub;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports the stub's configured mode. Status stays UP: an open circuit is already reflected
 * by the Resilience4j circuit-breaker health indicator, which is part of readiness.
 */
@Component
public class InventoryStubHealthIndicator implements HealthIndicator {

    private final DownstreamModeService downstreamModeService;
    private final InventoryStub inventoryStub;

    public InventoryStubHealthIndicator(DownstreamModeService downstreamModeService, InventoryStub inventoryStub) {
        this.downstreamModeService = downstreamModeService;
        this.inventoryStub = inventoryStub;
    }

    @Override
    public Health health() {
        return Health.up()
                .withDetail("mode", downstreamModeService.current().name())
                .withDetail("invocations", inventoryStub.invocations())
                .build();
    }
}
