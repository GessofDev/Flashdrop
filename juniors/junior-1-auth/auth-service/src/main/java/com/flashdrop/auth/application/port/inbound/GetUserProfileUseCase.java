package com.flashdrop.auth.application.port.inbound;

import com.flashdrop.auth.application.dto.UserProfile;

public interface GetUserProfileUseCase {
    UserProfile getProfile(Long userId);
}
