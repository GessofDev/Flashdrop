package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.entity;

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
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "restaurant")
public class RestaurantEntity {

    @Id
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    private String address;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "accepting_orders", nullable = false)
    private boolean acceptingOrders = true;

    // EAGER porque toDomain corre fuera de la sesión; SUBSELECT evita una consulta
    // por local al listar el marketplace.
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "restaurant_hours", joinColumns = @JoinColumn(name = "restaurant_id"))
    private List<RestaurantHoursEmbeddable> hours = new ArrayList<>();

    protected RestaurantEntity() {
    }

    public void changeOwner(Long userId) {
        this.userId = userId;
    }

    public void changeAcceptingOrders(boolean acceptingOrders) {
        this.acceptingOrders = acceptingOrders;
    }

    public void replaceHours(List<OpeningHours> newHours) {
        hours.clear();
        newHours.forEach(tramo -> hours.add(new RestaurantHoursEmbeddable(tramo)));
    }

    public Restaurant toDomain() {
        return new Restaurant(id, userId, name, address, createdAt, acceptingOrders,
                hours.stream().map(RestaurantHoursEmbeddable::toDomain).toList());
    }
}
