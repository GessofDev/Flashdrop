package com.flashdrop.auth.infrastructure.config;

import com.flashdrop.auth.application.port.outbound.EmailChangeMailer;
import com.flashdrop.auth.infrastructure.adapter.outbound.mail.SmtpEmailChangeMailer;
import com.flashdrop.auth.infrastructure.adapter.outbound.mail.UnconfiguredEmailChangeMailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
@EnableConfigurationProperties({EmailChangeProperties.class, AuthMailProperties.class})
public class EmailChangeConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailChangeConfig.class);

    @Bean
    EmailChangeMailer emailChangeMailer(AuthMailProperties mail, EmailChangeProperties emailChange) {
        if (!mail.isEnabled()) {
            return new UnconfiguredEmailChangeMailer();
        }
        // A diferencia de la recuperacion de contrasena, aca no se aborta el
        // arranque: un despliegue con SMTP habilitado pero sin la URL del
        // enlace perderia el servicio entero de auth. El flujo de cambio de
        // email queda sin correo y lo avisa en el log.
        if (emailChange.getLinkBaseUrl().isBlank()) {
            log.warn("Cambio de email sin URL de enlace: define EMAIL_CHANGE_URL para activar el flujo");
            return new UnconfiguredEmailChangeMailer();
        }
        if (mail.getHost().isBlank() || mail.getFrom().isBlank()) {
            log.warn("Cambio de email sin SMTP: define SMTP_HOST y SMTP_FROM para activar el flujo");
            return new UnconfiguredEmailChangeMailer();
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mail.getHost());
        sender.setPort(mail.getPort());
        sender.setUsername(mail.getUsername().isBlank() ? null : mail.getUsername());
        sender.setPassword(mail.getPassword().isBlank() ? null : mail.getPassword());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", String.valueOf(!mail.getUsername().isBlank()));
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        return new SmtpEmailChangeMailer(sender, mail.getFrom());
    }
}
