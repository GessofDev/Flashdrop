package com.flashdrop.auth.application.port.inbound;

public interface ProcessAccountDeletionUseCase {

    /** Avanza las eliminaciones pendientes cuyo reintento vencio. Devuelve cuantas cuentas se completaron. */
    int processDue();
}
