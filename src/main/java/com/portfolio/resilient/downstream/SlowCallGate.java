package com.portfolio.resilient.downstream;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Test seam for the bulkhead. Production traffic leaves the gate disarmed.
 * A test arms it so the first SLOW call blocks inside the bulkhead permit until the
 * test has observed a second, rejected call.
 */
@Component
public class SlowCallGate {

    private volatile CountDownLatch entered;
    private volatile CountDownLatch release;

    public void arm(CountDownLatch entered, CountDownLatch release) {
        this.entered = entered;
        this.release = release;
    }

    public void clear() {
        this.entered = null;
        this.release = null;
    }

    void awaitIfArmed() {
        CountDownLatch enteredLatch = this.entered;
        CountDownLatch releaseLatch = this.release;
        if (enteredLatch == null && releaseLatch == null) {
            return;
        }
        if (enteredLatch != null) {
            enteredLatch.countDown();
        }
        if (releaseLatch == null) {
            return;
        }
        try {
            if (!releaseLatch.await(10, TimeUnit.SECONDS)) {
                throw new DownstreamCallException("Timed out waiting for the slow-call gate to release.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DownstreamCallException("Interrupted while waiting for the slow-call gate.", ex);
        }
    }
}
