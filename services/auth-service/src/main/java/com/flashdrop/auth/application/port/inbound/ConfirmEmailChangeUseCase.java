package com.flashdrop.auth.application.port.inbound;

import com.flashdrop.auth.application.dto.ConfirmEmailChangeCommand;

public interface ConfirmEmailChangeUseCase {
    void confirmChange(ConfirmEmailChangeCommand command);
}
