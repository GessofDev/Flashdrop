package com.flashdrop.auth.application.port.inbound;

import com.flashdrop.auth.application.dto.RegisterUserCommand;
import com.flashdrop.auth.application.dto.RegisterUserResult;

public interface RegisterUserUseCase {
    RegisterUserResult register(RegisterUserCommand command);
}
