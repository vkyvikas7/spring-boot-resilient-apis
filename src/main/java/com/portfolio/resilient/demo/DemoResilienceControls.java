package com.portfolio.resilient.demo;

import com.portfolio.resilient.api.dto.BulkheadHoldResponse;
import com.portfolio.resilient.api.dto.RateLimiterDrainResponse;
import com.portfolio.resilient.api.dto.ResilienceStatusResponse;
import com.portfolio.resilient.downstream.DownstreamModeService;
import com.portfolio.resilient.downstream.InventoryResilience;
import com.portfolio.resilient.downstream.InventoryStub;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Demo-only operations against the same Resilience4j instances the inventory client uses.
 * Holding bulkhead permits and draining the rate limiter makes those patterns reproducible
 * from a single curl process. {@link #reset()} releases anything this class acquired.
 */
@Service
public class DemoResilienceControls {

    private static final Logger log = LoggerFactory.getLogger(DemoResilienceControls.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final InventoryStub inventoryStub;
    private final DownstreamModeService downstreamModeService;
    private final AtomicInteger demoHeldBulkheadPermits = new AtomicInteger();

    public DemoResilienceControls(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            BulkheadRegistry bulkheadRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            InventoryStub inventoryStub,
            DownstreamModeService downstreamModeService
    ) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.inventoryStub = inventoryStub;
        this.downstreamModeService = downstreamModeService;
    }

    public ResilienceStatusResponse status() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(InventoryResilience.NAME);
        CircuitBreaker.Metrics circuitMetrics = circuitBreaker.getMetrics();
        Retry.Metrics retryMetrics = retryRegistry.retry(InventoryResilience.NAME).getMetrics();
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(InventoryResilience.NAME);
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(InventoryResilience.NAME);
        return new ResilienceStatusResponse(
                circuitBreaker.getName(),
                circuitBreaker.getState().name(),
                circuitMetrics.getFailureRate(),
                circuitMetrics.getNumberOfBufferedCalls(),
                circuitMetrics.getNumberOfFailedCalls(),
                circuitMetrics.getNumberOfSuccessfulCalls(),
                circuitMetrics.getNumberOfNotPermittedCalls(),
                circuitBreaker.getCircuitBreakerConfig().getMinimumNumberOfCalls(),
                circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold(),
                circuitBreaker.getCircuitBreakerConfig().getSlidingWindowSize(),
                retryMetrics.getNumberOfSuccessfulCallsWithRetryAttempt(),
                retryMetrics.getNumberOfFailedCallsWithRetryAttempt(),
                bulkhead.getMetrics().getMaxAllowedConcurrentCalls(),
                bulkhead.getMetrics().getAvailableConcurrentCalls(),
                demoHeldBulkheadPermits.get(),
                rateLimiter.getRateLimiterConfig().getLimitForPeriod(),
                rateLimiter.getMetrics().getAvailablePermissions(),
                downstreamModeService.current().name(),
                inventoryStub.invocations()
        );
    }

    public ResilienceStatusResponse reset() {
        releaseBulkheadPermits();
        circuitBreakerRegistry.circuitBreaker(InventoryResilience.NAME).reset();
        refillRateLimiter();
        inventoryStub.reset();
        downstreamModeService.reset();
        log.info("Reset inventory circuit breaker, rate limiter, downstream mode, and stub counters");
        return status();
    }

    public BulkheadHoldResponse saturateBulkhead() {
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(InventoryResilience.NAME);
        int acquired = 0;
        while (bulkhead.tryAcquirePermission()) {
            acquired++;
        }
        demoHeldBulkheadPermits.addAndGet(acquired);
        log.info("Demo held {} additional inventory bulkhead permits", acquired);
        return new BulkheadHoldResponse(demoHeldBulkheadPermits.get(), bulkhead.getMetrics().getAvailableConcurrentCalls());
    }

    public BulkheadHoldResponse releaseBulkhead() {
        int released = releaseBulkheadPermits();
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(InventoryResilience.NAME);
        log.info("Demo released {} inventory bulkhead permits", released);
        return new BulkheadHoldResponse(demoHeldBulkheadPermits.get(), bulkhead.getMetrics().getAvailableConcurrentCalls());
    }

    public RateLimiterDrainResponse drainRateLimiter() {
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(InventoryResilience.NAME);
        int before = rateLimiter.getMetrics().getAvailablePermissions();
        rateLimiter.drainPermissions();
        int available = rateLimiter.getMetrics().getAvailablePermissions();
        log.info("Demo drained {} inventory rate-limiter permits", Math.max(0, before - available));
        return new RateLimiterDrainResponse(Math.max(0, before - available), available);
    }

    private void refillRateLimiter() {
        // drainPermissions only empties the current window. Rebuild the same config so the
        // next call sees a full budget. remove() drops the stored configuration, so keep it.
        RateLimiter current = rateLimiterRegistry.rateLimiter(InventoryResilience.NAME);
        RateLimiterConfig config = current.getRateLimiterConfig();
        rateLimiterRegistry.remove(InventoryResilience.NAME);
        rateLimiterRegistry.rateLimiter(InventoryResilience.NAME, config);
    }

    private int releaseBulkheadPermits() {
        int held = demoHeldBulkheadPermits.getAndSet(0);
        if (held == 0) {
            return 0;
        }
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(InventoryResilience.NAME);
        for (int i = 0; i < held; i++) {
            bulkhead.releasePermission();
        }
        return held;
    }
}
