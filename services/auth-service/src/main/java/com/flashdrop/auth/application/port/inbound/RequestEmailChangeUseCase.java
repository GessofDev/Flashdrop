package com.flashdrop.auth.application.port.inbound;

import com.flashdrop.auth.application.dto.RequestEmailChangeCommand;

public interface RequestEmailChangeUseCase {
    void requestChange(RequestEmailChangeCommand command);
}
