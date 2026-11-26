package com.flashdrop.auth.infrastructure.adapter.outbound.mail;

import com.flashdrop.auth.application.port.outbound.EmailChangeMailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Fallback mailer when SMTP is disabled so auth-service still boots cleanly.
 * Never logs the raw confirmation link.
 */
public class UnconfiguredEmailChangeMailer implements EmailChangeMailer {

    private static final Logger log = LoggerFactory.getLogger(UnconfiguredEmailChangeMailer.class);

    @Override
    public void sendConfirmationLink(String currentEmail, String recipientName, String confirmLink, Duration validFor) {
        log.warn("Confirmacion de cambio de email sin enviar: falta configurar AUTH_MAIL_ENABLED y SMTP");
    }

    @Override
    public void sendEmailChangedNotice(String newEmail, String recipientName) {
        log.info("Aviso de cambio de email sin enviar: SMTP deshabilitado");
    }
}
