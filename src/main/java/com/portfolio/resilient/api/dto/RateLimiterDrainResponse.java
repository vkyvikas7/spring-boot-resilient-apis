package com.portfolio.resilient.api.dto;

public record RateLimiterDrainResponse(int drainedPermissions, int availablePermissions) {
}
