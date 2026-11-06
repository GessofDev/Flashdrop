package cl.flashdrop.orders.infrastructure.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.flashdrop.orders.application.usecase.EraseUserDataUseCase;
import cl.flashdrop.orders.application.usecase.GetAccountDeletionBlockersUseCase;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import lombok.RequiredArgsConstructor;

/**
 * Eliminacion de cuenta (F5), solo llamadas internas (X-Internal-Api-Key, via InternalApiKeyFilter).
 * El id es el del usuario en Auth (Long en el contrato; UUID en el dominio de Orders).
 */
@RestController
@RequestMapping("/api/internal/users/{userId}")
@RequiredArgsConstructor
public class InternalUserDataController {

    private final GetAccountDeletionBlockersUseCase blockers;
    private final EraseUserDataUseCase erase;

    @GetMapping("/deletion-blockers")
    public Map<String, Object> deletionBlockers(@PathVariable Long userId) {
        GetAccountDeletionBlockersUseCase.Blockers result = blockers.execute(IdConverter.toUuid(userId));
        return Map.of(
                "blocked", result.total() > 0,
                "asClient", result.asClient(),
                "asRestaurant", result.asRestaurant(),
                "asCourier", result.asCourier());
    }

    @PostMapping("/erase")
    public EraseUserDataUseCase.Result erase(@PathVariable Long userId) {
        return erase.execute(IdConverter.toUuid(userId));
    }
}
