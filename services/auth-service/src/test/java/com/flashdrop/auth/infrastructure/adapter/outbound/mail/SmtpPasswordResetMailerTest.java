package com.flashdrop.auth.infrastructure.adapter.outbound.mail;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpPasswordResetMailerTest {

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final SmtpPasswordResetMailer mailer = new SmtpPasswordResetMailer(sender, "no-responder@flashdrop.cl");

    @Test
    void elCorreoLlevaElEnlaceYLaVigencia() {
        mailer.send("ana@x.cl", "Ana", "https://web.test/recuperar-clave#token=abc", Duration.ofMinutes(30));

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        SimpleMailMessage message = sent.getValue();

        assertEquals("no-responder@flashdrop.cl", message.getFrom());
        assertArrayEquals(new String[]{"ana@x.cl"}, message.getTo());
        assertTrue(message.getText().startsWith("Hola Ana,"));
        assertTrue(message.getText().contains("https://web.test/recuperar-clave#token=abc"));
        assertTrue(message.getText().contains("30 minutos"));
    }

    @Test
    void sinNombreSaludaIgual() {
        mailer.send("ana@x.cl", null, "https://web.test/x#token=abc", Duration.ofMinutes(30));

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        assertTrue(sent.getValue().getText().startsWith("Hola,"));
    }
}
