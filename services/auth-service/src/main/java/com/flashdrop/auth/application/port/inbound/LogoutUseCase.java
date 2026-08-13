package com.flashdrop.auth.application.port.inbound;

public interface LogoutUseCase {
    void logout(String refreshToken);
}
