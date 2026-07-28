package com.flashdrop.delivery.domain.valueobjects;

import java.util.Objects;

public record EstimatedTime(Integer minutes) {

    public EstimatedTime {
        if (minutes == null || minutes < 0) {
            throw new IllegalArgumentException("Estimated time cannot be null or negative");
        }
    }

    public static EstimatedTime of(Integer minutes) {
        return new EstimatedTime(minutes);
    }

    public static EstimatedTime zero() {
        return new EstimatedTime(0);
    }

    public Integer getMinutes() {
        return minutes;
    }
}
