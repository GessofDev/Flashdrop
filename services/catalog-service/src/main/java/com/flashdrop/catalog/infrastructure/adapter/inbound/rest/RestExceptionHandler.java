package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.ErrorResponse;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(
                        status.value(),
                        code,
                        message
                ));
    }
}
