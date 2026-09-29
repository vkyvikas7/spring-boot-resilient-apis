package com.portfolio.resilient.api.dto;

public record ResilienceStatusResponse(
        String circuitBreaker,
        String state,
        float failureRate,
        int bufferedCalls,
        int failedCalls,
        int successfulCalls,
        long notPermittedCalls,
        int minimumNumberOfCalls,
        float failureRateThreshold,
        int slidingWindowSize,
        long successfulCallsWithRetry,
        long failedCallsWithRetry,
        int bulkheadMaxConcurrentCalls,
        int bulkheadAvailableConcurrentCalls,
        int bulkheadPermitsHeldByDemo,
        int rateLimitForPeriod,
        int rateLimitAvailablePermissions,
        String downstreamMode,
        long downstreamInvocations
) {
}
