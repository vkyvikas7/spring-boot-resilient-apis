package com.portfolio.resilient.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Same error document for container-level failures (unknown path, method not allowed)
 * that never enter a controller advice.
 */
@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping(path = "/error", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiError> error(HttpServletRequest request) {
        int rawStatus = status(request);
        HttpStatus status = HttpStatus.resolve(rawStatus);
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String code = switch (status.value()) {
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            default -> "HTTP_" + status.value();
        };
        String message = switch (status.value()) {
            case 404 -> "No endpoint matches this request.";
            case 405 -> "HTTP method is not supported for this endpoint.";
            default -> "Request could not be processed.";
        };
        String path = attribute(request, RequestDispatcher.ERROR_REQUEST_URI);
        if (path == null) {
            path = request.getRequestURI();
        }
        return ResponseEntity.status(status).body(ApiError.of(status, code, message, path, RequestIds.current()));
    }

    private static int status(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (value instanceof Integer status) {
            return status;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    private static String attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value == null ? null : value.toString();
    }
}
