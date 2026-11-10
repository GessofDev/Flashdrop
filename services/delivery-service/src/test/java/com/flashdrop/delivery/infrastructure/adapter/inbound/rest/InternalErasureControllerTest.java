package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.flashdrop.delivery.application.usecase.EraseDeliveryDataUseCase;

class InternalErasureControllerTest {

    private final EraseDeliveryDataUseCase useCase = mock(EraseDeliveryDataUseCase.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new InternalErasureController(useCase)).build();
    }

    @Test
    void anonymizeRoutes_reportsHowManyWereUpdated() throws Exception {
        when(useCase.anonymizeClientRoutes(List.of(10L, 11L))).thenReturn(2);

        mvc.perform(post("/api/internal/erasure/routes/anonymize")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderIds\":[10,11]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(2));
    }

    @Test
    void eraseCourier_reportsWhetherTheProfileExisted() throws Exception {
        when(useCase.eraseCourier("77")).thenReturn(true);

        mvc.perform(post("/api/internal/erasure/delivery-persons").param("userId", "77"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.erased").value(true));
        verify(useCase).eraseCourier("77");
    }
}
