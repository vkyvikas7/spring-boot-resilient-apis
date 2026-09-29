package com.portfolio.resilient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ApiContractTest extends AbstractIntegrationTest {

    @Test
    void successAndOutOfStockAreStable() throws Exception {
        mockMvc.perform(get("/api/v1/demo/success").header("X-Request-Id", "success-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "success-1"))
                .andExpect(jsonPath("$.sku").value("SKU-1001"))
                .andExpect(jsonPath("$.available").value(true));

        mockMvc.perform(get("/api/v1/products/SKU-1004/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("Wool Socks"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.quantityOnHand").value(0));
    }

    @Test
    void unknownSkuIsNotADependencyFailure() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/v1/products/SKU-404/availability"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.path").value("/api/v1/products/SKU-404/availability"));
        }
        var circuitBreaker = circuitBreakerRegistry.circuitBreaker("inventory");
        assertThat(circuitBreaker.getState()).isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void configuredModeDrivesTheProductEndpoint() throws Exception {
        mockMvc.perform(put("/api/v1/demo/downstream/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"FAILURE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("FAILURE"));

        mockMvc.perform(get("/api/v1/products/SKU-1002/availability"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DOWNSTREAM_FAILED"));

        mockMvc.perform(post("/api/v1/demo/resilience/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CLOSED"))
                .andExpect(jsonPath("$.downstreamMode").value("SUCCESS"))
                .andExpect(jsonPath("$.downstreamInvocations").value(0))
                .andExpect(jsonPath("$.minimumNumberOfCalls").value(5));

        mockMvc.perform(get("/api/v1/products/SKU-1002/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("Merino Beanie"))
                .andExpect(jsonPath("$.warehouse").value("EAST-2"));
    }

    @Test
    void invalidModeAndUnknownPathShareTheErrorDocument() throws Exception {
        mockMvc.perform(put("/api/v1/demo/downstream/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"EXPLODE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/v1/demo/downstream/mode"));

        mockMvc.perform(put("/api/v1/demo/downstream/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No endpoint matches this request."));

        mockMvc.perform(get("/api/v1/products/" + "S".repeat(65) + "/availability"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
