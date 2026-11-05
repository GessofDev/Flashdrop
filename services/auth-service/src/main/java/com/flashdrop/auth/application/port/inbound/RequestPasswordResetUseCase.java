package com.flashdrop.auth.application.port.inbound;

import com.flashdrop.auth.application.dto.RequestPasswordResetCommand;

public interface RequestPasswordResetUseCase {
    void requestReset(RequestPasswordResetCommand command);
}
