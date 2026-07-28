package com.flashdrop.delivery.application.port.inbound;

import com.flashdrop.delivery.application.dto.ClaimDeliveryRequest;
import com.flashdrop.delivery.application.dto.DeliveryPersonResponse;

import java.util.List;

public interface ClaimDeliveryOrdersUseCase {

    List<DeliveryPersonResponse> execute(ClaimDeliveryRequest request);
}
