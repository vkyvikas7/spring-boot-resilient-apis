package com.portfolio.resilient.downstream;

/**
 * Shared Resilience4j instance name. Every pattern around the inventory call uses this name
 * so actuator, health, and the demo status endpoint describe one dependency.
 */
public final class InventoryResilience {

    public static final String NAME = "inventory";

    /** Calls required before the breaker evaluates the failure rate. Matches application.yml. */
    public static final int MINIMUM_CALLS_BEFORE_OPEN = 5;

    private InventoryResilience() {
    }
}
