package com.portfolio.resilient.downstream;

import com.portfolio.resilient.config.InventoryProperties;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.stereotype.Service;

/**
 * Boundary around the inventory dependency.
 *
 * <p>Aspect order is configured explicitly (see application.yml) so the stack is:
 * CircuitBreaker → Retry → RateLimiter → Bulkhead → {@link InventoryStub}.
 * The breaker therefore scores the outcome of the whole retry, and an open circuit
 * never reaches the rate limiter, the bulkhead, or the stub.
 */
@Service
public class InventoryClient {

    private final InventoryStub stub;
    private final InventoryProperties properties;

    public InventoryClient(InventoryStub stub, InventoryProperties properties) {
        this.stub = stub;
        this.properties = properties;
    }

    @CircuitBreaker(name = InventoryResilience.NAME)
    @Retry(name = InventoryResilience.NAME)
    @RateLimiter(name = InventoryResilience.NAME)
    @Bulkhead(name = InventoryResilience.NAME)
    public Availability getAvailability(String sku, DownstreamMode mode) {
        return stub.fetch(sku, mode, properties.getCallTimeout());
    }
}
