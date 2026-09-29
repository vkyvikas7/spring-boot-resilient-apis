package com.portfolio.resilient.error;

import com.portfolio.resilient.config.RequestIdFilter;
import org.slf4j.MDC;

final class RequestIds {

    private RequestIds() {
    }

    static String current() {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        return requestId == null ? "unavailable" : requestId;
    }
}
