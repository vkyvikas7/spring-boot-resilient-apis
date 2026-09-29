package com.portfolio.resilient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "resilience4j.ratelimiter.instances.inventory.limit-for-period=2",
        "resilience4j.ratelimiter.instances.inventory.limit-refresh-period=60s",
        "resilience4j.retry.instances.inventory.max-attempts=1",
        "resilience4j.circuitbreaker.instances.inventory.minimum-number-of-calls=100"
})
class RateLimiterBehaviorTest extends AbstractIntegrationTest {

    @Test
    void callsPastThePeriodLimitAreRejectedWithoutTouchingTheStub() throws Exception {
        mockMvc.perform(get("/api/v1/demo/success")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/demo/success")).andExpect(status().isOk());
        assertThat(inventoryStub.invocations()).isEqualTo(2);

        mockMvc.perform(get("/api/v1/demo/success"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Inventory call was rejected by the local rate limiter."));

        assertThat(inventoryStub.invocations()).isEqualTo(2);
        assertThat(circuitBreakerRegistry.circuitBreaker("inventory").getMetrics().getNumberOfFailedCalls()).isZero();
    }
}
