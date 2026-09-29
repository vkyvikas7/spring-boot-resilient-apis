package com.portfolio.resilient.api;

import com.portfolio.resilient.api.dto.AvailabilityResponse;
import com.portfolio.resilient.api.dto.BulkheadHoldResponse;
import com.portfolio.resilient.api.dto.DownstreamModeRequest;
import com.portfolio.resilient.api.dto.DownstreamModeResponse;
import com.portfolio.resilient.api.dto.RateLimiterDrainResponse;
import com.portfolio.resilient.api.dto.ResilienceStatusResponse;
import com.portfolio.resilient.demo.DemoResilienceControls;
import com.portfolio.resilient.downstream.DownstreamMode;
import com.portfolio.resilient.downstream.DownstreamModeService;
import com.portfolio.resilient.downstream.InventoryClient;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deterministic controls for a local demo. Absent when the {@code prod} profile is active.
 */
@RestController
@Profile("!prod")
@RequestMapping(path = "/api/v1/demo", produces = MediaType.APPLICATION_JSON_VALUE)
public class DemoController {

    static final String DEMO_SKU = "SKU-1001";

    private final InventoryClient inventoryClient;
    private final DownstreamModeService downstreamModeService;
    private final DemoResilienceControls controls;

    public DemoController(
            InventoryClient inventoryClient,
            DownstreamModeService downstreamModeService,
            DemoResilienceControls controls
    ) {
        this.inventoryClient = inventoryClient;
        this.downstreamModeService = downstreamModeService;
        this.controls = controls;
    }

    @GetMapping("/success")
    public AvailabilityResponse success() {
        return call(DownstreamMode.SUCCESS);
    }

    @GetMapping("/failure")
    public AvailabilityResponse failure() {
        return call(DownstreamMode.FAILURE);
    }

    @GetMapping("/flaky")
    public AvailabilityResponse flaky() {
        return call(DownstreamMode.FLAKY);
    }

    @GetMapping("/slow")
    public AvailabilityResponse slow() {
        return call(DownstreamMode.SLOW);
    }

    @GetMapping("/downstream/mode")
    public DownstreamModeResponse mode() {
        return new DownstreamModeResponse(downstreamModeService.current());
    }

    @PutMapping(path = "/downstream/mode", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DownstreamModeResponse updateMode(@Valid @RequestBody DownstreamModeRequest request) {
        return new DownstreamModeResponse(downstreamModeService.update(request.mode()));
    }

    @GetMapping("/resilience")
    public ResilienceStatusResponse resilience() {
        return controls.status();
    }

    @PostMapping("/resilience/reset")
    public ResilienceStatusResponse reset() {
        return controls.reset();
    }

    @PostMapping("/bulkhead/saturate")
    public BulkheadHoldResponse saturateBulkhead() {
        return controls.saturateBulkhead();
    }

    @PostMapping("/bulkhead/release")
    public BulkheadHoldResponse releaseBulkhead() {
        return controls.releaseBulkhead();
    }

    @PostMapping("/rate-limiter/drain")
    public RateLimiterDrainResponse drainRateLimiter() {
        return controls.drainRateLimiter();
    }

    private AvailabilityResponse call(DownstreamMode mode) {
        return AvailabilityResponse.from(inventoryClient.getAvailability(DEMO_SKU, mode));
    }
}
