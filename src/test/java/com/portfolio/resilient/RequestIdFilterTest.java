package com.portfolio.resilient;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.resilient.config.RequestIdFilter;
import org.junit.jupiter.api.Test;

class RequestIdFilterTest {

    @Test
    void rejectsUnsafeRequestIdsAndKeepsSafeOnes() {
        assertThat(RequestIdFilter.resolve("abc-123")).isEqualTo("abc-123");
        assertThat(RequestIdFilter.resolve("bad id")).hasSize(36);
        assertThat(RequestIdFilter.resolve(null)).hasSize(36);
        assertThat(RequestIdFilter.resolve("../etc")).hasSize(36);
    }
}
