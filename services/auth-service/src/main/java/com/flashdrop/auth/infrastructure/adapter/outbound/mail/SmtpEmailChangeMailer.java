package com.flashdrop.auth.infrastructure.adapter.outbound.mail;

import com.flashdrop.auth.application.port.outbound.EmailChangeMailer;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;

/** Sends email change confirmation links and notices over SMTP. */
public class SmtpEmailChangeMailer implements EmailChangeMailer {

    private final JavaMailSender sender;
    private final String from;

    public SmtpEmailChangeMailer(JavaMailSender sender, String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void sendConfirmationLink(String currentEmail, String recipientName, String confirmLink, Duration validFor) {
        String greeting = recipientName == null || recipientName.isBlank()
                ? "Hola," : "Hola " + recipientName + ",";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(currentEmail);
        message.setSubject("Confirma el cambio de email de tu cuenta FlashDrop");
        message.setText("""
                %s

                Recibimos una solicitud para cambiar el correo electrónico de acceso de tu cuenta FlashDrop.
                Abre este enlace para confirmar el cambio. Vence en %d minutos y sirve una sola vez:

                %s

                Si no fuiste tú, ignora este correo y te recomendamos cambiar tu contraseña.
                """.formatted(greeting, validFor.toMinutes(), confirmLink));
        sender.send(message);
    }

    @Override
    public void sendEmailChangedNotice(String newEmail, String recipientName) {
        String greeting = recipientName == null || recipientName.isBlank()
                ? "Hola," : "Hola " + recipientName + ",";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(newEmail);
        message.setSubject("Tu correo de acceso en FlashDrop fue actualizado");
        message.setText("""
                %s

                Tu correo electrónico de acceso en FlashDrop fue actualizado a esta dirección.
                A partir de ahora debes iniciar sesión con este correo.
                """.formatted(greeting));
        sender.send(message);
    }
}
