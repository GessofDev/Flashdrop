package com.flashdrop.catalog.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.flashdrop.catalog.application.port.outbound.MerchantMemberRepositoryPort;
import com.flashdrop.catalog.domain.model.MerchantMember;
import com.flashdrop.catalog.domain.model.MerchantRole;

class EraseRestaurantOwnerUseCaseTest {

    private final MerchantMemberRepositoryPort merchantMembers = mock(MerchantMemberRepositoryPort.class);
    private final EraseRestaurantOwnerUseCase useCase = new EraseRestaurantOwnerUseCase(merchantMembers);

    @Test
    void execute_ownerWithMembership_deletesMembershipsAndReturnsTrue() {
        when(merchantMembers.findByUserId(7L)).thenReturn(List.of(
                new MerchantMember(1L, 10L, 7L, MerchantRole.DUEÑO, LocalDateTime.now())));

        assertThat(useCase.execute(7L)).isTrue();
        verify(merchantMembers).deleteByUserId(7L);
    }

    @Test
    void execute_userWithoutMembership_isANoOp() {
        when(merchantMembers.findByUserId(8L)).thenReturn(List.of());

        assertThat(useCase.execute(8L)).isFalse();
        verify(merchantMembers, never()).deleteByUserId(any());
    }

    @Test
    void execute_invalidUser_isRejected() {
        assertThatThrownBy(() -> useCase.execute(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
