package com.portfolio.resilient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portfolio.resilient.downstream.SlowCallGate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

@TestPropertySource(properties = {
        "resilience4j.bulkhead.instances.inventory.max-concurrent-calls=1",
        "resilience4j.bulkhead.instances.inventory.max-wait-duration=0",
        "resilience4j.retry.instances.inventory.max-attempts=1",
        "resilience4j.circuitbreaker.instances.inventory.minimum-number-of-calls=100",
        "app.inventory.slow-delay=50ms",
        "app.inventory.call-timeout=5s"
})
class BulkheadBehaviorTest extends AbstractIntegrationTest {

    @Autowired
    SlowCallGate slowCallGate;

    @Test
    void secondConcurrentCallIsRejectedWhileTheBulkheadPermitIsHeld() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        slowCallGate.arm(entered, release);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> inFlight = executor.submit(() ->
                    mockMvc.perform(get("/api/v1/demo/slow")).andReturn());
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            long invocationsWhileHeld = inventoryStub.invocations();
            mockMvc.perform(get("/api/v1/demo/slow"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("BULKHEAD_FULL"))
                    .andExpect(jsonPath("$.status").value(503));
            assertThat(inventoryStub.invocations()).isEqualTo(invocationsWhileHeld);

            release.countDown();
            MvcResult admitted = inFlight.get(5, TimeUnit.SECONDS);
            assertThat(admitted.getResponse().getStatus()).isEqualTo(200);
            assertThat(circuitBreakerRegistry.circuitBreaker("inventory").getMetrics().getNumberOfFailedCalls())
                    .isZero();
        } finally {
            release.countDown();
            slowCallGate.clear();
            executor.shutdownNow();
        }
    }
}
