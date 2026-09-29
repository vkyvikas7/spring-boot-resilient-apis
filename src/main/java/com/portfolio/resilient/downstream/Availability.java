package com.portfolio.resilient.downstream;

import java.time.Instant;

public record Availability(
        String sku,
        String productName,
        boolean available,
        int quantityOnHand,
        String warehouse,
        Instant servedAt
) {
}
