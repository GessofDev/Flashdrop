package com.flashdrop.auth.application.port.inbound;

import com.flashdrop.auth.application.dto.ResetPasswordCommand;

public interface ResetPasswordUseCase {
    void reset(ResetPasswordCommand command);
}
