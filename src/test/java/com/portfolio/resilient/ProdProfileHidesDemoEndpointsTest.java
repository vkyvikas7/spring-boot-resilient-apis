package com.portfolio.resilient;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "prod"})
class ProdProfileHidesDemoEndpointsTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void prodProfileRemovesDemoRoutesAndKeepsTheProductApi() throws Exception {
        mockMvc.perform(get("/api/v1/demo/success"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(get("/api/v1/products/SKU-1001/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-1001"));
    }
}
