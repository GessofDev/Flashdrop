package com.flashdrop.reviews.infrastructure.adapter.inbound.rest;

import com.flashdrop.reviews.application.port.inbound.GetRatingAggregatesUseCase;
import com.flashdrop.reviews.domain.exception.InvalidTargetIdException;
import com.flashdrop.reviews.domain.model.TargetRatingStats;
import com.flashdrop.reviews.domain.valueobjects.RatingTarget;
import com.flashdrop.reviews.infrastructure.adapter.inbound.rest.dto.InternalAggregatesResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * API interna de reviews-service (T9).
 *
 * <p>{@code GET /api/internal/reviews/aggregates?targetType=TIENDA&ids=1,2,3}}
 * — agregados por lote. La consumen catalog-service (promedio de la tienda) y
 * delivery-service (promedio del repartidor); ambos cachean el resultado en su
 * propia base.</p>
 *
 * <p>La ruta está protegida por el {@code InternalApiKeyFilter} de
 * {@code shared-observability} (cabecera {@code X-Internal-Api-Key}).</p>
 */
@RestController
@RequestMapping("/api/internal/reviews")
public class InternalReviewsController {

    private final GetRatingAggregatesUseCase getRatingAggregates;

    public InternalReviewsController(GetRatingAggregatesUseCase getRatingAggregates) {
        this.getRatingAggregates = getRatingAggregates;
    }

    /**
     * @param targetType tipo de destino (TIENDA | REPARTIDOR | CLIENTE).
     * @param ids        ids de destino separados por coma.
     * @return un agregado por cada id solicitado, en el orden pedido.
     */
    @GetMapping("/aggregates")
    public InternalAggregatesResponse aggregates(@RequestParam String targetType,
                                                 @RequestParam String ids) {
        RatingTarget target = RatingTarget.fromValue(targetType);
        List<Long> targetIds = parseIds(ids);

        List<TargetRatingStats> stats = getRatingAggregates.execute(target, targetIds);

        List<InternalAggregatesResponse.RatingAggregateResponse> items = new ArrayList<>(stats.size());
        for (TargetRatingStats entry : stats) {
            items.add(new InternalAggregatesResponse.RatingAggregateResponse(
                    entry.targetId(), entry.average(), entry.count()));
        }
        return new InternalAggregatesResponse(items);
    }

    /**
     * Parsea {@code ids=1,2,3}. Un id no numérico es un error del cliente
     * (400), no una lista vacía.
     */
    private static List<Long> parseIds(String ids) {
        List<Long> result = new ArrayList<>();
        if (ids == null || ids.isBlank()) {
            return result;
        }
        for (String part : ids.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                result.add(Long.parseLong(trimmed));
            } catch (NumberFormatException e) {
                throw new InvalidTargetIdException("id invalido: " + trimmed);
            }
        }
        return result;
    }
}
