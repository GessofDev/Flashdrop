package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa;

import cl.flashdrop.orders.domain.model.ClientInfo;
import cl.flashdrop.orders.domain.model.UserInfo;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.UserPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.ClientEntity;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@Profile({"postgres", "default"})
@RequiredArgsConstructor
public class JpaClientAdapter implements ClientPort {

    private final SpringDataClientRepository clientRepository;
    private final UserPort userPort;

    @Override
    public Optional<UUID> findClientIdByUserId(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        long rawUserId = IdConverter.toLong(userId);
        log.debug("JpaClientAdapter: buscando client por userId={}", rawUserId);
        return clientRepository.findByUserId(rawUserId)
                .map(ClientEntity::getId)
                .map(IdConverter::toUuid);
    }

    @Override
    public Optional<ClientInfo> findClientById(UUID clientId) {
        if (clientId == null) {
            return Optional.empty();
        }
        long rawId = IdConverter.toLong(clientId);
        log.debug("JpaClientAdapter: buscando client por id={}", rawId);
        Optional<ClientEntity> clientOpt = clientRepository.findById(rawId);
        if (clientOpt.isEmpty()) {
            return Optional.empty();
        }
        ClientEntity entity = clientOpt.get();
        UUID userUuid = IdConverter.toUuid(entity.getUserId());
        Optional<UserInfo> user = userPort.findUserById(userUuid);

        return Optional.of(ClientInfo.builder()
                .clientId(clientId)
                .fullName(user.map(UserInfo::getFullName).orElse("Cliente"))
                .email(user.map(UserInfo::getEmail).orElse(null))
                .phone(user.map(UserInfo::getPhone).orElse(null))
                .build());
    }
}
