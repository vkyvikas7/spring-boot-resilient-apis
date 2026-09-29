package com.portfolio.resilient;

import com.portfolio.resilient.downstream.InventoryStub;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    InventoryStub inventoryStub;

    @BeforeEach
    void resetBreakerAndStub() {
        circuitBreakerRegistry.circuitBreaker("inventory").reset();
        inventoryStub.reset();
    }
}
