package com.portfolio.resilient.downstream;

import com.portfolio.resilient.config.InventoryProperties;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

/**
 * Mode used by the product API. Demo endpoints that force success or failure pass a mode
 * explicitly and do not change this value.
 */
@Service
public class DownstreamModeService {

    private final AtomicReference<DownstreamMode> mode;
    private final DownstreamMode defaultMode;

    public DownstreamModeService(InventoryProperties properties) {
        this.defaultMode = properties.getDefaultMode();
        this.mode = new AtomicReference<>(properties.getDefaultMode());
    }

    public DownstreamMode current() {
        return mode.get();
    }

    public DownstreamMode update(DownstreamMode next) {
        mode.set(next);
        return next;
    }

    public void reset() {
        mode.set(defaultMode);
    }
}
