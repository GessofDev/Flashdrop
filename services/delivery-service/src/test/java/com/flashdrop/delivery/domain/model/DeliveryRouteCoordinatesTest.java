package com.flashdrop.delivery.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class DeliveryRouteCoordinatesTest {

    @Test
    void setPickupCoordinates_validPair_storesCoordinates() {
        DeliveryRoute route = new DeliveryRoute();
        BigDecimal lat = new BigDecimal("-33.448900");
        BigDecimal lng = new BigDecimal("-70.669300");

        route.setPickupCoordinates(lat, lng);

        assertThat(route.getPickupLatitude()).isEqualByComparingTo(lat);
        assertThat(route.getPickupLongitude()).isEqualByComparingTo(lng);
    }

    @Test
    void setPickupCoordinates_bothNull_clearsCoordinates() {
        DeliveryRoute route = new DeliveryRoute();
        route.setPickupCoordinates(new BigDecimal("-33.45"), new BigDecimal("-70.66"));

        route.setPickupCoordinates(null, null);

        assertThat(route.getPickupLatitude()).isNull();
        assertThat(route.getPickupLongitude()).isNull();
    }

    @Test
    void setPickupCoordinates_halfPair_throwsIllegalArgument() {
        DeliveryRoute route = new DeliveryRoute();

        assertThatThrownBy(() -> route.setPickupCoordinates(new BigDecimal("-33.45"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> route.setPickupCoordinates(null, new BigDecimal("-70.66")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setPickupCoordinates_outOfRange_throwsIllegalArgument() {
        DeliveryRoute route = new DeliveryRoute();

        assertThatThrownBy(() -> route.setPickupCoordinates(new BigDecimal("91"), new BigDecimal("0")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> route.setPickupCoordinates(new BigDecimal("0"), new BigDecimal("-181")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setClientUserId_blankNormalizedToNull() {
        DeliveryRoute route = new DeliveryRoute();
        route.setClientUserId("   ");
        assertThat(route.getClientUserId()).isNull();

        route.setClientUserId(" 42 ");
        assertThat(route.getClientUserId()).isEqualTo("42");
    }
}
