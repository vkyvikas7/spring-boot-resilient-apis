package com.portfolio.resilient.api;

import com.portfolio.resilient.api.dto.AvailabilityResponse;
import com.portfolio.resilient.downstream.DownstreamModeService;
import com.portfolio.resilient.downstream.InventoryClient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping(path = "/api/v1/products", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProductAvailabilityController {

    private final InventoryClient inventoryClient;
    private final DownstreamModeService downstreamModeService;

    public ProductAvailabilityController(InventoryClient inventoryClient, DownstreamModeService downstreamModeService) {
        this.inventoryClient = inventoryClient;
        this.downstreamModeService = downstreamModeService;
    }

    @GetMapping("/{sku}/availability")
    public AvailabilityResponse availability(@PathVariable @NotBlank @Size(max = 64) String sku) {
        return AvailabilityResponse.from(inventoryClient.getAvailability(sku, downstreamModeService.current()));
    }
}
