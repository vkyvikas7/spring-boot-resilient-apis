package com.portfolio.resilient.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import org.springframework.http.HttpStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String requestId
) {
    public static ApiError of(HttpStatus status, String code, String message, String path, String requestId) {
        return new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                path,
                requestId
        );
    }
}
