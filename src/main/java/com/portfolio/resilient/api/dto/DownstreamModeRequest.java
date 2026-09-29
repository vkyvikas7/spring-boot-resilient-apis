package com.portfolio.resilient.api.dto;

import com.portfolio.resilient.downstream.DownstreamMode;
import jakarta.validation.constraints.NotNull;

public record DownstreamModeRequest(@NotNull DownstreamMode mode) {
}
