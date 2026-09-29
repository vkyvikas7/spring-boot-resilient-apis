package com.portfolio.resilient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RetryBehaviorTest extends AbstractIntegrationTest {

    @Autowired
    RetryRegistry retryRegistry;

    @Test
    void flakyDependencySucceedsInsideTheRetryBudget() throws Exception {
        Retry retry = retryRegistry.retry("inventory");
        long recoveredBefore = retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt();

        mockMvc.perform(get("/api/v1/demo/flaky"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-1001"))
                .andExpect(jsonPath("$.productName").value("Trail Running Shoe"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.quantityOnHand").value(42))
                .andExpect(jsonPath("$.warehouse").value("WEST-1"))
                .andExpect(jsonPath("$.source").value("inventory-stub"));

        assertThat(inventoryStub.invocations())
                .as("two forced failures plus the successful attempt")
                .isEqualTo(3);
        assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt())
                .isEqualTo(recoveredBefore + 1);
        assertThat(circuitBreakerRegistry.circuitBreaker("inventory").getState())
                .isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED);
    }
}
