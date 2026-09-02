package com.flashdrop.delivery.infrastructure.exception;

import com.flashdrop.delivery.domain.exception.DeliveryPersonNotFoundException;
import com.flashdrop.delivery.domain.exception.OrderClaimFailedException;
import com.flashdrop.delivery.domain.exception.RouteAlreadyAssignedException;
import com.flashdrop.delivery.domain.exception.RouteNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DeliveryPersonNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleDeliveryPersonNotFound(
            DeliveryPersonNotFoundException ex) {
        log.error("Delivery person not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(RouteNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRouteNotFound(RouteNotFoundException ex) {
        log.error("Route not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(RouteAlreadyAssignedException.class)
    public ResponseEntity<Map<String, Object>> handleRouteAlreadyAssigned(
            RouteAlreadyAssignedException ex) {
        log.error("Route already assigned: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * PR-B: orders-service claim delegation failed. Maps the upstream status
     * (carried by the exception) to the response. Routes may already be saved
     * (orphan) — the caller decides whether to reconcile.
     */
    @ExceptionHandler(OrderClaimFailedException.class)
    public ResponseEntity<Map<String, Object>> handleOrderClaimFailed(
            OrderClaimFailedException ex) {
        HttpStatus status = ex.getStatus() != null ? ex.getStatus() : HttpStatus.BAD_GATEWAY;
        log.error("Order claim delegation failed (upstreamStatus={}): {}",
                status, ex.getMessage(), ex);
        return buildErrorResponse(status, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("Missing required parameter: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.error("Invalid argument: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error: ", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private String formatFieldError(FieldError fe) {
        return fe.getField() + ": " + (fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid");
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return new ResponseEntity<>(body, status);
    }
}