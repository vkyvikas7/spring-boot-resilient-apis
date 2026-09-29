package com.portfolio.resilient.api.dto;

import com.portfolio.resilient.downstream.Availability;
import java.time.Instant;

public record AvailabilityResponse(
        String sku,
        String productName,
        boolean available,
        int quantityOnHand,
        String warehouse,
        String source,
        Instant servedAt
) {
    public static final String SOURCE = "inventory-stub";

    public static AvailabilityResponse from(Availability availability) {
        return new AvailabilityResponse(
                availability.sku(),
                availability.productName(),
                availability.available(),
                availability.quantityOnHand(),
                availability.warehouse(),
                SOURCE,
                availability.servedAt()
        );
    }
}
