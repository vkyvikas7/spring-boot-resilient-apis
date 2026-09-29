package com.portfolio.resilient.error;

import com.portfolio.resilient.downstream.DownstreamCallException;
import com.portfolio.resilient.downstream.InventoryResilience;
import com.portfolio.resilient.downstream.ProductNotFoundException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Duration;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;

    public GlobalExceptionHandler(CircuitBreakerRegistry circuitBreakerRegistry, RateLimiterRegistry rateLimiterRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> missing(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                "No endpoint matches this request.",
                request,
                null
        );
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiError> productNotFound(ProductNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", ex.getMessage(), request, null);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiError> circuitOpen(CallNotPermittedException ex, HttpServletRequest request) {
        log.warn("Failing fast because the inventory circuit breaker is open");
        long waitMillis = circuitBreakerRegistry.circuitBreaker(InventoryResilience.NAME)
                .getCircuitBreakerConfig()
                .getWaitIntervalFunctionInOpenState()
                .apply(1);
        Duration retryAfter = Duration.ofMillis(waitMillis);
        return respond(
                HttpStatus.SERVICE_UNAVAILABLE,
                "CIRCUIT_OPEN",
                "Inventory circuit breaker is open; failing fast to avoid cascading failures.",
                request,
                retryAfter
        );
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ApiError> rateLimited(RequestNotPermitted ex, HttpServletRequest request) {
        Duration retryAfter = rateLimiterRegistry.rateLimiter(InventoryResilience.NAME)
                .getRateLimiterConfig()
                .getLimitRefreshPeriod();
        return respond(
                HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMITED",
                "Inventory call was rejected by the local rate limiter.",
                request,
                retryAfter
        );
    }

    @ExceptionHandler(BulkheadFullException.class)
    public ResponseEntity<ApiError> bulkheadFull(BulkheadFullException ex, HttpServletRequest request) {
        return respond(
                HttpStatus.SERVICE_UNAVAILABLE,
                "BULKHEAD_FULL",
                "Inventory bulkhead is full; rejecting the call so a slow dependency cannot consume every worker.",
                request,
                Duration.ofSeconds(1)
        );
    }

    @ExceptionHandler(DownstreamCallException.class)
    public ResponseEntity<ApiError> downstreamFailed(DownstreamCallException ex, HttpServletRequest request) {
        log.warn("Inventory dependency call failed: {}", ex.getMessage());
        return respond(
                HttpStatus.SERVICE_UNAVAILABLE,
                "DOWNSTREAM_FAILED",
                "Inventory service call failed: " + ex.getMessage(),
                request,
                Duration.ofSeconds(1)
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Request validation failed.";
        }
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request, null);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> invalidMethod(HandlerMethodValidationException ex, HttpServletRequest request) {
        String message = ex.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Request validation failed.";
        }
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> constraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Request validation failed.";
        }
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> malformed(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_REQUEST",
                "Request body is missing or is not valid JSON for this endpoint.",
                request,
                null
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Unexpected server error.",
                request,
                null
        );
    }

    private ResponseEntity<ApiError> respond(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Duration retryAfter
    ) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (retryAfter != null && !retryAfter.isZero() && !retryAfter.isNegative()) {
            long seconds = Math.max(1, retryAfter.toSeconds());
            builder.header(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        }
        ApiError body = ApiError.of(status, code, message, request.getRequestURI(), RequestIds.current());
        return builder.body(body);
    }
}
