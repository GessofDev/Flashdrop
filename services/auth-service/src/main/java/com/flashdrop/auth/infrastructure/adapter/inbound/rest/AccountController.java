package com.flashdrop.auth.infrastructure.adapter.inbound.rest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.flashdrop.auth.application.dto.RequestAccountDeletionCommand;
import com.flashdrop.auth.application.port.inbound.RequestAccountDeletionUseCase;
import com.flashdrop.auth.application.port.inbound.ValidateTokenUseCase;
import com.flashdrop.auth.domain.exception.InvalidTokenException;
import com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto.DeleteAccountRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Eliminar la cuenta propia (F5). El token se valida aqui, como en /auth/profile. Responde 202: la cuenta
 * queda inhabilitada al instante y el borrado de datos en los demas servicios se completa en segundo plano.
 */
@RestController
@RequestMapping("/auth/account")
public class AccountController {

    private final ValidateTokenUseCase validateToken;
    private final RequestAccountDeletionUseCase requestAccountDeletion;

    public AccountController(ValidateTokenUseCase validateToken, RequestAccountDeletionUseCase requestAccountDeletion) {
        this.validateToken = validateToken;
        this.requestAccountDeletion = requestAccountDeletion;
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                       @Valid @RequestBody DeleteAccountRequest request,
                       HttpServletRequest http) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new InvalidTokenException();
        }
        var claims = validateToken.validate(authorization.substring(7));
        requestAccountDeletion.request(new RequestAccountDeletionCommand(
                claims.userId(), request.password(), clientIp(http)));
    }

    private static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
