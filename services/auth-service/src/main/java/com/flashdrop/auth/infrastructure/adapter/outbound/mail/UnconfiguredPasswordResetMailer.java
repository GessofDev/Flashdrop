package com.flashdrop.auth.infrastructure.adapter.outbound.mail;

import com.flashdrop.auth.application.port.outbound.PasswordResetMailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Para entornos sin SMTP: el servicio arranca igual y el resto de auth sigue
 * funcionando. No registra el enlace, porque sería una credencial en los logs.
 */
public class UnconfiguredPasswordResetMailer implements PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(UnconfiguredPasswordResetMailer.class);

    @Override
    public void send(String toEmail, String recipientName, String resetLink, Duration validFor) {
        log.warn("Recuperacion de contrasena sin enviar: falta configurar AUTH_MAIL_ENABLED y SMTP");
    }
}
