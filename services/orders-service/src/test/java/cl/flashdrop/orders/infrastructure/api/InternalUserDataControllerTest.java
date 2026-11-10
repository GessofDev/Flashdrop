package cl.flashdrop.orders.infrastructure.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import cl.flashdrop.orders.application.usecase.EraseUserDataUseCase;
import cl.flashdrop.orders.application.usecase.GetAccountDeletionBlockersUseCase;
import cl.flashdrop.orders.application.usecase.GetAccountDeletionBlockersUseCase.Blockers;
import cl.flashdrop.orders.domain.exception.AccountDeletionBlockedException;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.exception.GlobalExceptionHandler;

class InternalUserDataControllerTest {

    private final GetAccountDeletionBlockersUseCase blockers = mock(GetAccountDeletionBlockersUseCase.class);
    private final EraseUserDataUseCase erase = mock(EraseUserDataUseCase.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new InternalUserDataController(blockers, erase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void deletionBlockers_reportsTheCountsPerRole() throws Exception {
        when(blockers.execute(IdConverter.toUuid(42L))).thenReturn(new Blockers(1, 0, 2));

        mvc.perform(get("/api/internal/users/42/deletion-blockers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true))
                .andExpect(jsonPath("$.asClient").value(1))
                .andExpect(jsonPath("$.asRestaurant").value(0))
                .andExpect(jsonPath("$.asCourier").value(2));
    }

    @Test
    void deletionBlockers_withoutActiveOrders_isNotBlocked() throws Exception {
        when(blockers.execute(IdConverter.toUuid(42L))).thenReturn(new Blockers(0, 0, 0));

        mvc.perform(get("/api/internal/users/42/deletion-blockers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(false));
    }

    @Test
    void erase_returnsWhatWasAnonymized() throws Exception {
        when(erase.execute(IdConverter.toUuid(42L))).thenReturn(new EraseUserDataUseCase.Result(3, 3, 1, true));

        mvc.perform(post("/api/internal/users/42/erase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordersAnonymized").value(3))
                .andExpect(jsonPath("$.profileErased").value(true));
    }

    @Test
    void erase_withActiveOrders_isConflict() throws Exception {
        when(erase.execute(IdConverter.toUuid(42L)))
                .thenThrow(new AccountDeletionBlockedException("Tienes pedidos en curso"));

        mvc.perform(post("/api/internal/users/42/erase"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Tienes pedidos en curso"));
    }
}
