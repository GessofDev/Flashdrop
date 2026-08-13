package com.flashdrop.auth.infrastructure.adapter.inbound.rest;

import com.flashdrop.auth.application.dto.*;
import com.flashdrop.auth.application.port.inbound.*;
import com.flashdrop.auth.domain.exception.InvalidTokenException;
import com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto.LoginRequest;
import com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto.RefreshRequest;
import com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RegisterUserUseCase registerUser;
    private final AuthenticateUserUseCase authenticateUser;
    private final ValidateTokenUseCase validateToken;
    private final RefreshTokenUseCase refreshToken;
    private final LogoutUseCase logout;
    private final GetUserProfileUseCase getUserProfile;

    public AuthController(RegisterUserUseCase registerUser, AuthenticateUserUseCase authenticateUser,
                          ValidateTokenUseCase validateToken, RefreshTokenUseCase refreshToken,
                          LogoutUseCase logout, GetUserProfileUseCase getUserProfile) {
        this.registerUser = registerUser;
        this.authenticateUser = authenticateUser;
        this.validateToken = validateToken;
        this.refreshToken = refreshToken;
        this.logout = logout;
        this.getUserProfile = getUserProfile;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterUserResult> register(@Valid @RequestBody RegisterRequest req) {
        var result = registerUser.register(new RegisterUserCommand(
                req.email(), req.password(), req.rut(), req.name(), req.lastName(), req.phone()));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/login")
    public AuthResult login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return authenticateUser.authenticate(
                new AuthenticateCommand(req.login(), req.password(), clientIp(http)));
    }

    @PostMapping("/refresh")
    public TokenPair refresh(@Valid @RequestBody RefreshRequest req) {
        return refreshToken.refresh(new RefreshCommand(req.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest req) {
        logout.logout(req.refreshToken());
    }

    /** El gateway valida el token aquí antes de propagar la identidad. */
    @GetMapping("/validate")
    public TokenClaims validate(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return validateToken.validate(bearer(authorization));
    }

    @GetMapping("/profile")
    public UserProfile profile(@RequestHeader(value = "Authorization", required = false) String authorization) {
        TokenClaims claims = validateToken.validate(bearer(authorization));
        return getUserProfile.getProfile(claims.userId());
    }

    private static String bearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new InvalidTokenException();
        }
        return authorization.substring(7);
    }

    private static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
