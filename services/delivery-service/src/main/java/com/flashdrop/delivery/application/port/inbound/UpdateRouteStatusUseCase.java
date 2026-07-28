package com.flashdrop.delivery.application.port.inbound;

import com.flashdrop.delivery.application.dto.RouteResponse;
import com.flashdrop.delivery.application.dto.UpdateRouteStatusRequest;

public interface UpdateRouteStatusUseCase {

    RouteResponse execute(Long routeId, UpdateRouteStatusRequest request);
}
