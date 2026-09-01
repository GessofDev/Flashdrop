package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.http.dto.InternalOrderDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controlador interno de Orders (contrato C-8).
 *
 * <p>Expone {@code GET /api/internal/orders} protegido por {@code X-Internal-Api-Key}
 * para que otros servicios consulten pedidos por id.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalOrdersController {

    private final OrderRepositoryPort orderRepository;

    /**
     * Lista pedidos por sus ids.
     *
     * <p>Los ids solicitados que no existen se omiten de la respuesta (contrato C-8):
     * el cliente recibe únicamente los pedidos hallados.</p>
     *
     * @param ids ids externos (Long) separados por coma, p. ej {@code ?ids=501,502}
     * @return pedidos encontrados, siguiendo el JSON del contrato.
     */
    @GetMapping("/orders")
    public ResponseEntity<List<InternalOrderDto>> getOrders(
            @RequestParam(name = "ids", required = false) String ids) {

        List<Long> requested = parseIds(ids);
        if (requested.isEmpty()) {
            throw new OrderDomainException("Se requiere el parámetro ids");
        }

        List<UUID> uuids = requested.stream()
                .map(IdConverter::toUuid)
                .collect(Collectors.toList());

        List<Order> orders = orderRepository.findByIds(uuids);
        List<InternalOrderDto> result = orders.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        log.debug("C-8 GET /api/internal/orders -> {} de {} pedidos", result.size(), requested.size());
        return ResponseEntity.ok(result);
    }

    private InternalOrderDto toDto(Order order) {
        return new InternalOrderDto(
                IdConverter.toLong(order.getId()),
                IdConverter.toLong(order.getClientId()),
                IdConverter.toLong(order.getRestaurantId()),
                order.getDeliveryId() != null ? IdConverter.toLong(order.getDeliveryId()) : null,
                order.getStatus().getValue(),
                order.getAddress());
    }

    /**
     * Parsea {@code ids=501,502} (comma-separated) en Longs validos.
     * @throws OrderDomainException si contiene un valor no numerico.
     */
    private static List<Long> parseIds(String ids) {
        List<Long> result = new ArrayList<>();
        if (ids == null || ids.isBlank()) {
            return result;
        }
        for (String part : ids.split(",")) {
            String trim = part.trim();
            if (trim.isEmpty()) {
                continue;
            }
            try {
                result.add(Long.parseLong(trim));
            } catch (NumberFormatException e) {
                throw new OrderDomainException("id invalido: " + trim);
            }
        }
        return result;
    }
}
