package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa;

import cl.flashdrop.orders.domain.model.ClientInfo;
import cl.flashdrop.orders.domain.model.UserInfo;
import cl.flashdrop.orders.domain.port.UserPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.ClientEntity;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GAP-02 (auditoría 2026-09-04): cobertura real de {@link JpaClientAdapter} contra
 * PostgreSQL (Testcontainers) — el adapter que efectivamente usa el perfil
 * {@code postgres}/{@code default} en producción, antes sin ningún test dedicado.
 *
 * <p>{@link UserPort} se mockea con Mockito plano (no {@code @MockBean}): el propósito de
 * este test es validar el mapeo JPA/SQL real, no la integración HTTP con Auth (ya cubierta
 * por {@code AuthHttpClientAdapterTest}).</p>
 */
class JpaClientAdapterTest extends PostgresIntegrationTestSupport {

    @Autowired
    private SpringDataClientRepository clientRepository;

    private UserPort userPort;
    private JpaClientAdapter adapter;

    @BeforeEach
    void setUp() {
        userPort = mock(UserPort.class);
        adapter = new JpaClientAdapter(clientRepository, userPort);
    }

    private ClientEntity persistClient(long userId) {
        ClientEntity entity = ClientEntity.builder()
                .userId(userId)
                .createdAt(OffsetDateTime.now())
                .build();
        return clientRepository.save(entity);
    }

    @Test
    void findClientIdByUserId_shouldReturnClientIdWhenUserHasProfile() {
        ClientEntity saved = persistClient(501L);

        Optional<UUID> result = adapter.findClientIdByUserId(IdConverter.toUuid(501L));

        assertTrue(result.isPresent());
        assertEquals(IdConverter.toUuid(saved.getId()), result.get());
    }

    @Test
    void findClientIdByUserId_shouldReturnEmptyWhenUserHasNoProfile() {
        Optional<UUID> result = adapter.findClientIdByUserId(IdConverter.toUuid(999L));
        assertTrue(result.isEmpty());
    }

    @Test
    void findClientIdByUserId_shouldReturnEmptyForNullUserId() {
        assertTrue(adapter.findClientIdByUserId(null).isEmpty());
    }

    @Test
    void findClientById_shouldEnrichWithUserInfoWhenClientAndUserExist() {
        ClientEntity saved = persistClient(502L);
        when(userPort.findUserById(IdConverter.toUuid(502L)))
                .thenReturn(Optional.of(UserInfo.builder()
                        .fullName("Maria Perez")
                        .email("maria@test.com")
                        .phone("+56911111111")
                        .build()));

        Optional<ClientInfo> result = adapter.findClientById(IdConverter.toUuid(saved.getId()));

        assertTrue(result.isPresent());
        assertEquals("Maria Perez", result.get().fullName());
        assertEquals("maria@test.com", result.get().getEmail());
        assertEquals("+56911111111", result.get().getPhone());
    }

    @Test
    void findClientById_shouldFallBackToDefaultNameWhenUserPortReturnsEmpty() {
        ClientEntity saved = persistClient(503L);
        when(userPort.findUserById(IdConverter.toUuid(503L))).thenReturn(Optional.empty());

        Optional<ClientInfo> result = adapter.findClientById(IdConverter.toUuid(saved.getId()));

        assertTrue(result.isPresent());
        assertEquals("Cliente", result.get().fullName());
        assertNull(result.get().getEmail());
        assertNull(result.get().getPhone());
    }

    @Test
    void findClientById_shouldReturnEmptyWhenClientDoesNotExist() {
        Optional<ClientInfo> result = adapter.findClientById(IdConverter.toUuid(999_999L));
        assertTrue(result.isEmpty());
    }

    @Test
    void findClientById_shouldReturnEmptyForNullClientId() {
        assertTrue(adapter.findClientById(null).isEmpty());
    }
}
