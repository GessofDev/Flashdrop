package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import com.flashdrop.catalog.domain.model.OpeningHours;
import com.flashdrop.catalog.domain.model.Restaurant;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "restaurant")
public class RestaurantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(nullable = false)
    private String name;

    private String address;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "accepting_orders", nullable = false)
    private boolean acceptingOrders = true;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "delivery_radius_km", precision = 4, scale = 2)
    private BigDecimal deliveryRadiusKm;

    // EAGER porque toDomain corre fuera de la sesión; SUBSELECT evita una consulta
    // por local al listar el marketplace.
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "restaurant_hours", joinColumns = @JoinColumn(name = "restaurant_id"))
    private List<RestaurantHoursEmbeddable> hours = new ArrayList<>();

    protected RestaurantEntity() {
    }

    public static RestaurantEntity create(String name, String address, boolean acceptingOrders) {
        return create(name, address, acceptingOrders, null, null);
    }

    public static RestaurantEntity create(String name, String address, boolean acceptingOrders,
                                          BigDecimal latitude, BigDecimal longitude) {
        return createInMerchant(name, address, null, acceptingOrders, latitude, longitude, null);
    }

    public static RestaurantEntity createInMerchant(String name, String address, Long merchantId,
                                                    boolean acceptingOrders, BigDecimal latitude,
                                                    BigDecimal longitude, BigDecimal deliveryRadiusKm) {
        RestaurantEntity entity = new RestaurantEntity();
        entity.name = name;
        entity.address = address;
        entity.merchantId = merchantId;
        entity.acceptingOrders = acceptingOrders;
        entity.latitude = latitude;
        entity.longitude = longitude;
        entity.deliveryRadiusKm = deliveryRadiusKm;
        entity.createdAt = LocalDateTime.now();
        return entity;
    }

    public void changeMerchant(Long merchantId) {
        this.merchantId = merchantId;
    }

    public void changeAcceptingOrders(boolean acceptingOrders) {
        this.acceptingOrders = acceptingOrders;
    }

    public void replaceHours(List<OpeningHours> newHours) {
        hours.clear();
        newHours.forEach(tramo -> hours.add(new RestaurantHoursEmbeddable(tramo)));
    }

    public void changeLocation(BigDecimal latitude, BigDecimal longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Restaurant toDomain() {
        return new Restaurant(id, null, merchantId, name, address, createdAt, acceptingOrders,
                hours.stream().map(RestaurantHoursEmbeddable::toDomain).toList(),
                latitude, longitude, deliveryRadiusKm);
    }

    public void changeDeliveryRadius(BigDecimal deliveryRadiusKm) {
        this.deliveryRadiusKm = deliveryRadiusKm;
    }
}
