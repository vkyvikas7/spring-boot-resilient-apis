package com.portfolio.resilient.api.dto;

public record BulkheadHoldResponse(int heldPermits, int availableConcurrentCalls) {
}
