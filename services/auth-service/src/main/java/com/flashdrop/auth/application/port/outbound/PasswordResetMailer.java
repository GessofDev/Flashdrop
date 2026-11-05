package com.flashdrop.auth.application.port.outbound;

import java.time.Duration;

public interface PasswordResetMailer {
    void send(String toEmail, String recipientName, String resetLink, Duration validFor);
}
