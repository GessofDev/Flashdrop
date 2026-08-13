package com.flashdrop.auth.application.port.inbound;

import com.flashdrop.auth.application.dto.AuthResult;
import com.flashdrop.auth.application.dto.AuthenticateCommand;

public interface AuthenticateUserUseCase {
    AuthResult authenticate(AuthenticateCommand command);
}
