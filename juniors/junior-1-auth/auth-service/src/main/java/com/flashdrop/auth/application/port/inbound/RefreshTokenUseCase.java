package com.flashdrop.auth.application.port.inbound;

import com.flashdrop.auth.application.dto.RefreshCommand;
import com.flashdrop.auth.application.dto.TokenPair;

public interface RefreshTokenUseCase {
    TokenPair refresh(RefreshCommand command);
}
