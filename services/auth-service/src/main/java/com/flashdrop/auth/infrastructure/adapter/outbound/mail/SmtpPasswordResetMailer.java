package com.flashdrop.auth.infrastructure.adapter.outbound.mail;

import com.flashdrop.auth.application.port.outbound.PasswordResetMailer;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;

/** Envía el enlace por SMTP. El proveedor se elige por configuración. */
public class SmtpPasswordResetMailer implements PasswordResetMailer {

    private final JavaMailSender sender;
    private final String from;

    public SmtpPasswordResetMailer(JavaMailSender sender, String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(String toEmail, String recipientName, String resetLink, Duration validFor) {
        String greeting = recipientName == null || recipientName.isBlank()
                ? "Hola," : "Hola " + recipientName + ",";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("Recupera tu contraseña de FlashDrop");
        message.setText("""
                %s

                Recibimos una solicitud para cambiar la contraseña de tu cuenta FlashDrop.
                Abre este enlace para crear una nueva. Vence en %d minutos y sirve una sola vez:

                %s

                Si no fuiste tú, ignora este correo: tu contraseña no cambiará.
                """.formatted(greeting, validFor.toMinutes(), resetLink));
        sender.send(message);
    }
}
