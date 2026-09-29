package com.portfolio.resilient.downstream;

import com.portfolio.resilient.config.InventoryProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * In-process stand-in for a remote inventory service. The failure mode is chosen by the caller
 * so demo endpoints can force success or failure without racing a global flag.
 */
@Component
public class InventoryStub {

    private static final Map<String, CatalogItem> CATALOG = Map.of(
            "SKU-1001", new CatalogItem("Trail Running Shoe", 42, "WEST-1"),
            "SKU-1002", new CatalogItem("Merino Beanie", 18, "EAST-2"),
            "SKU-1003", new CatalogItem("Stainless Bottle", 7, "CENTRAL-3"),
            "SKU-1004", new CatalogItem("Wool Socks", 0, "EAST-2")
    );

    private final InventoryProperties properties;
    private final SlowCallGate slowCallGate;
    private final AtomicLong invocations = new AtomicLong();
    private final AtomicInteger flakyAttempts = new AtomicInteger();

    public InventoryStub(InventoryProperties properties, SlowCallGate slowCallGate) {
        this.properties = properties;
        this.slowCallGate = slowCallGate;
    }

    public Availability fetch(String sku, DownstreamMode mode, Duration callTimeout) {
        invocations.incrementAndGet();
        CatalogItem item = CATALOG.get(sku);
        if (item == null) {
            throw new ProductNotFoundException(sku);
        }
        switch (mode) {
            case FAILURE -> throw new DownstreamCallException("inventory returned HTTP 503");
            case FLAKY -> {
                int attempt = flakyAttempts.incrementAndGet();
                if (attempt <= properties.getFlakyFailuresBeforeSuccess()) {
                    throw new DownstreamCallException("transient inventory failure on flaky attempt " + attempt);
                }
            }
            case SLOW -> simulateSlowCall(callTimeout);
            case SUCCESS -> {
                // Healthy dependency. Fall through to the catalog snapshot.
            }
        }
        return new Availability(
                sku,
                item.productName(),
                item.quantityOnHand() > 0,
                item.quantityOnHand(),
                item.warehouse(),
                Instant.now()
        );
    }

    public long invocations() {
        return invocations.get();
    }

    public void reset() {
        invocations.set(0);
        flakyAttempts.set(0);
    }

    private void simulateSlowCall(Duration callTimeout) {
        slowCallGate.awaitIfArmed();
        Duration delay = properties.getSlowDelay();
        sleep(Math.min(delay.toMillis(), callTimeout.toMillis()));
        if (delay.compareTo(callTimeout) >= 0) {
            throw new DownstreamCallException(
                    "inventory call timed out after " + callTimeout.toMillis() + "ms");
        }
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DownstreamCallException("inventory call interrupted", ex);
        }
    }

    private record CatalogItem(String productName, int quantityOnHand, String warehouse) {
    }
}
