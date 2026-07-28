package com.flashdrop.delivery.application.port.inbound;

import com.flashdrop.delivery.application.dto.RouteResponse;

import java.util.List;

public interface ListDeliveryRoutesUseCase {

    List<RouteResponse> execute(Long deliveryPersonId);
}
