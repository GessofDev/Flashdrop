package com.flashdrop.auth.application.port.inbound;

import com.flashdrop.auth.application.dto.RequestAccountDeletionCommand;

public interface RequestAccountDeletionUseCase {

    /** Acepta la eliminacion de la cuenta propia (se completa en segundo plano). */
    void request(RequestAccountDeletionCommand command);
}
