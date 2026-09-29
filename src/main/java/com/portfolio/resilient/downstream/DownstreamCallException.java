package com.portfolio.resilient.downstream;

/**
 * A failed call to the inventory dependency. Recorded by the circuit breaker and retried.
 * Business outcomes such as {@link ProductNotFoundException} are a different type so they
 * do not open the circuit.
 */
public class DownstreamCallException extends RuntimeException {

    public DownstreamCallException(String message) {
        super(message);
    }

    public DownstreamCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
