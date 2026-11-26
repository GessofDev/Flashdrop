package com.flashdrop.auth.infrastructure.adapter.inbound.rest;

import com.flashdrop.auth.application.dto.*;
import com.flashdrop.auth.application.port.inbound.*;
import com.flashdrop.auth.domain.exception.InvalidTokenException;
import com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto.ConfirmEmailChangeRequest;
import com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto.ForgotPasswordRequest;
import com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto.LoginRequest;
import com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto.RefreshRequest;
import com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto.RegisterRequest;
import com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto.RequestEmailChangeRequest;
import com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto.ResetPasswordRequest;
import com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto.UpdateProfileRequest;
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
    private final UpdateUserProfileUseCase updateUserProfile;
    private final RequestPasswordResetUseCase requestPasswordReset;
    private final ResetPasswordUseCase resetPassword;
    private final RequestEmailChangeUseCase requestEmailChange;
    private final ConfirmEmailChangeUseCase confirmEmailChange;

    public AuthController(RegisterUserUseCase registerUser, AuthenticateUserUseCase authenticateUser,
                          ValidateTokenUseCase validateToken, RefreshTokenUseCase refreshToken,
                          LogoutUseCase logout, GetUserProfileUseCase getUserProfile,
                          UpdateUserProfileUseCase updateUserProfile,
                          RequestPasswordResetUseCase requestPasswordReset,
                          ResetPasswordUseCase resetPassword,
                          RequestEmailChangeUseCase requestEmailChange,
                          ConfirmEmailChangeUseCase confirmEmailChange) {
        this.registerUser = registerUser;
        this.authenticateUser = authenticateUser;
        this.validateToken = validateToken;
        this.refreshToken = refreshToken;
        this.logout = logout;
        this.getUserProfile = getUserProfile;
        this.updateUserProfile = updateUserProfile;
        this.requestPasswordReset = requestPasswordReset;
        this.resetPassword = resetPassword;
        this.requestEmailChange = requestEmailChange;
        this.confirmEmailChange = confirmEmailChange;
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

    /**
     * Reemplaza los datos editables del perfil propio. El usuario sale del
     * token, nunca del cuerpo, así que no hay forma de editar el de otro.
     *
     * <p>Responde el mismo {@code UserProfile} que el GET, sin envoltorio: es
     * el mismo recurso y el cliente lo lee igual en los dos casos.
     */
    @PutMapping("/profile")
    public UserProfile updateProfile(@RequestHeader(value = "Authorization", required = false) String authorization,
                                     @Valid @RequestBody UpdateProfileRequest req) {
        TokenClaims claims = validateToken.validate(bearer(authorization));
        return updateUserProfile.updateProfile(claims.userId(), new UpdateUserProfileCommand(
                req.name(), req.lastName(), req.phone()));
    }

    /** Responde 202 exista o no el correo: el resultado no se puede distinguir (S-07). */
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest req, HttpServletRequest http) {
        requestPasswordReset.requestReset(new RequestPasswordResetCommand(req.email(), clientIp(http)));
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest req, HttpServletRequest http) {
        resetPassword.reset(new ResetPasswordCommand(req.token(), req.newPassword(), clientIp(http)));
    }

    @PostMapping("/email/change-request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestEmailChange(@RequestHeader(value = "Authorization", required = false) String authorization,
                                   @Valid @RequestBody RequestEmailChangeRequest req,
                                   HttpServletRequest http) {
        TokenClaims claims = validateToken.validate(bearer(authorization));
        requestEmailChange.requestChange(new RequestEmailChangeCommand(
                claims.userId(), req.password(), clientIpReal(http)));
    }

    @PostMapping("/email/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmEmailChange(@Valid @RequestBody ConfirmEmailChangeRequest req, HttpServletRequest http) {
        confirmEmailChange.confirmChange(new ConfirmEmailChangeCommand(
                req.token(), req.newEmail(), clientIpReal(http)));
    }

    private static String bearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new InvalidTokenException();
        }
        return authorization.substring(7);
    }

    /**
     * IP real del cliente para el flujo de cambio de email.
     *
     * <p>El gateway <b>agrega</b> el peer real al final de {@code X-Forwarded-For}
     * y publica {@code x-real-ip} sobrescribiendo el que envie el cliente, asi
     * que el ultimo salto es la identidad fiable. Tomar el primer salto (lo que
     * hace {@link #clientIp}) deja que cualquiera rote el encabezado y se
     * escape del limite por IP.
     *
     * <p>Los demas endpoints siguen usando {@link #clientIp}: corregir el
     * limite y la auditoria del login y del resto de flujos es un cambio aparte,
     * con su propio analisis de regresion.
     */
    private static String clientIpReal(HttpServletRequest http) {
        String realIp = http.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] saltos = forwarded.split(",");
            return saltos[saltos.length - 1].trim();
        }
        return http.getRemoteAddr();
    }

    private static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
