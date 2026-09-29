package com.portfolio.resilient.downstream;

/**
 * Deterministic behaviour of the in-process inventory stub.
 * SUCCESS and FAILURE are stable. FLAKY fails a fixed number of attempts, then succeeds.
 * SLOW sleeps and, when that sleep meets the client timeout, fails as a timeout.
 */
public enum DownstreamMode {
    SUCCESS,
    FAILURE,
    FLAKY,
    SLOW
}
