package com.flashdrop.auth.application.port.inbound;

import com.flashdrop.auth.application.dto.UpdateUserProfileCommand;
import com.flashdrop.auth.application.dto.UserProfile;

public interface UpdateUserProfileUseCase {
    UserProfile updateProfile(Long userId, UpdateUserProfileCommand command);
}
