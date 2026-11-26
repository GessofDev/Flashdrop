package com.flashdrop.auth.application.port.outbound;

import java.time.Duration;

public interface EmailChangeMailer {
    void sendConfirmationLink(String currentEmail, String recipientName, String confirmLink, Duration validFor);

    void sendEmailChangedNotice(String newEmail, String recipientName);
}
