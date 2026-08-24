package com.flashdrop.auth.infrastructure.exception;

import com.flashdrop.auth.domain.exception.*;
import com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto.ErrorResponse;
import com.flashdrop.observability.error.ApiError;
import com.flashdrop.observability.tracing.TraceContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Traduce excepciones a la respuesta { code, service, trace_id, message }.
 * Nunca filtra stack traces ni mensajes internos (S-11, E-07).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String SERVICE = "auth-service";

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.code(), ex.getMessage());
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiError> handleInvalidToken(InvalidTokenException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.code(), ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiError> handleEmailTaken(EmailAlreadyRegisteredException ex) {
        return build(HttpStatus.CONFLICT, ex.code(), ex.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimit(RateLimitExceededException ex) {
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.code(), ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, "NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler({InvalidEmailException.class, WeakPasswordException.class, InvalidUserException.class})
    public ResponseEntity<ApiError> handleValidation(DomainException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.code(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBeanValidation(MethodArgumentNotValidException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Datos de entrada inválidos");
    }

    /** Parámetro de query con tipo equivocado, p. ej. ?ids=abc en el endpoint
     *  batch. Sin esto caería en handleGeneric y devolvería un 500 por un
     *  error del llamador.
     *
     *  <p>Responde en el formato que corresponda a la superficie: los
     *  endpoints entre microservicios usan el {@code {status, error, message}}
     *  de MIGRATION_PLAN §10; los públicos usan {@link ApiError}, que lleva
     *  traceId para el dashboard de observabilidad. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                HttpServletRequest req) {
        String message = "Parámetro inválido: " + ex.getName();
        if (isInternal(req)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(400, "BAD_REQUEST", message));
        }
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    /** Los endpoints bajo /api/internal/** hablan el contrato de errores del
     *  plan de migración; el resto habla el de observabilidad. */
    private static boolean isInternal(HttpServletRequest req) {
        return req != null && req.getRequestURI() != null
                && req.getRequestURI().startsWith("/api/internal/");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
        String traceId = TraceContext.currentTraceId();
        log.error("Unhandled exception traceId={} path={}", traceId, req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Error interno", traceId);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message) {
        return build(status, code, message, TraceContext.currentTraceId());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message, String traceId) {
        return ResponseEntity.status(status).body(
                new ApiError(code, SERVICE, traceId, message));
    }
}
