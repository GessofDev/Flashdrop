package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.domain.model.DeliveryTariff;
import cl.flashdrop.orders.domain.model.Vehicle;
import cl.flashdrop.orders.domain.port.DeliveryTariffRepositoryPort;
import cl.flashdrop.orders.infrastructure.api.dto.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Tarifa de envio parametrizable (F8). Solo el administrador (ver {@code AdminAccess}).
 * El PUT reemplaza ambas tablas completas; con listas vacias se vuelve al envio de respaldo.
 */
@RestController
@RequestMapping("/api/admin/delivery-tariff")
@RequiredArgsConstructor
public class AdminDeliveryTariffController {

    private final DeliveryTariffRepositoryPort tariffs;
    private final CurrentUserResolver currentUserResolver;

    public record DimensionRangeBody(@PositiveOrZero int minDimension, Integer maxDimension,
                                     @NotNull Vehicle vehicle, @NotNull @PositiveOrZero BigDecimal price) {
    }

    public record DistanceRangeBody(@NotNull @PositiveOrZero BigDecimal minKm, BigDecimal maxKm,
                                    @NotNull @PositiveOrZero BigDecimal price) {
    }

    public record TariffBody(@Valid List<DimensionRangeBody> dimensionRanges,
                             @Valid List<DistanceRangeBody> distanceRanges) {
    }

    @GetMapping
    public ApiResponse<TariffBody> get() {
        currentUserResolver.requireAdminUserId();
        return ApiResponse.success(toBody(tariffs.find()));
    }

    @PutMapping
    public ApiResponse<TariffBody> replace(@Valid @RequestBody TariffBody body) {
        UUID adminId = currentUserResolver.requireAdminUserId();
        DeliveryTariff tariff = new DeliveryTariff(
                body.dimensionRanges() == null ? List.of() : body.dimensionRanges().stream()
                        .map(r -> new DeliveryTariff.DimensionRange(r.minDimension(), r.maxDimension(), r.vehicle(), r.price()))
                        .toList(),
                body.distanceRanges() == null ? List.of() : body.distanceRanges().stream()
                        .map(r -> new DeliveryTariff.DistanceRange(r.minKm(), r.maxKm(), r.price()))
                        .toList());
        return ApiResponse.success("Tarifa actualizada", toBody(tariffs.replace(tariff, adminId)));
    }

    private static TariffBody toBody(DeliveryTariff t) {
        return new TariffBody(
                t.dimensionRanges().stream()
                        .map(r -> new DimensionRangeBody(r.minDimension(), r.maxDimension(), r.vehicle(), r.price()))
                        .toList(),
                t.distanceRanges().stream()
                        .map(r -> new DistanceRangeBody(r.minKm(), r.maxKm(), r.price()))
                        .toList());
    }
}
