package com.flashdrop.auth.infrastructure.config;

import com.flashdrop.auth.application.port.outbound.PasswordResetMailer;
import com.flashdrop.auth.infrastructure.adapter.outbound.mail.SmtpPasswordResetMailer;
import com.flashdrop.auth.infrastructure.adapter.outbound.mail.UnconfiguredPasswordResetMailer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Properties;

/**
 * Correo y ejecutor de la recuperación de contraseña.
 *
 * <p>El emisor se arma aquí y no con {@code spring.mail.*} para que Boot no
 * registre un indicador de salud de correo: un SMTP caído no debe marcar a
 * auth como no saludable.
 */
@Configuration
@EnableConfigurationProperties({PasswordResetProperties.class, AuthMailProperties.class})
public class PasswordResetConfig {

    @Bean
    PasswordResetMailer passwordResetMailer(AuthMailProperties mail, PasswordResetProperties reset) {
        if (!mail.isEnabled()) {
            return new UnconfiguredPasswordResetMailer();
        }
        // Con el correo encendido, una configuración incompleta no debe descubrirse
        // recién cuando un usuario pide recuperar su clave.
        if (mail.getHost().isBlank() || mail.getFrom().isBlank() || reset.getLinkBaseUrl().isBlank()) {
            throw new IllegalStateException(
                    "AUTH_MAIL_ENABLED=true requiere SMTP_HOST, SMTP_FROM y PASSWORD_RESET_URL");
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

        return new SmtpPasswordResetMailer(sender, mail.getFrom());
    }

    /** Cola acotada: una avalancha de solicitudes no debe agotar la memoria. */
    @Bean
    ThreadPoolTaskExecutor passwordResetExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("password-reset-");
        return executor;
    }
}
