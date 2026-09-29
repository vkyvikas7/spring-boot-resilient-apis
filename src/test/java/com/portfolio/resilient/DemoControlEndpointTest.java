package com.portfolio.resilient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DemoControlEndpointTest extends AbstractIntegrationTest {

    @Test
    void saturateAndDrainForceBulkheadAndRateLimitResponses() throws Exception {
        mockMvc.perform(post("/api/v1/demo/bulkhead/saturate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heldPermits").value(5))
                .andExpect(jsonPath("$.availableConcurrentCalls").value(0));

        long invocations = inventoryStub.invocations();
        mockMvc.perform(get("/api/v1/demo/success"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("BULKHEAD_FULL"));
        assertThat(inventoryStub.invocations()).isEqualTo(invocations);

        mockMvc.perform(post("/api/v1/demo/bulkhead/release"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heldPermits").value(0))
                .andExpect(jsonPath("$.availableConcurrentCalls").value(5));

        mockMvc.perform(get("/api/v1/demo/success")).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/demo/rate-limiter/drain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availablePermissions").value(0));

        long afterSuccess = inventoryStub.invocations();
        mockMvc.perform(get("/api/v1/demo/success"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        assertThat(inventoryStub.invocations()).isEqualTo(afterSuccess);

        mockMvc.perform(post("/api/v1/demo/resilience/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateLimitAvailablePermissions").value(1000));
        mockMvc.perform(get("/api/v1/demo/success")).andExpect(status().isOk());
    }
}
