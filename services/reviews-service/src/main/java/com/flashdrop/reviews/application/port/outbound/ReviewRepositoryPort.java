package com.flashdrop.reviews.application.port.outbound;

import com.flashdrop.reviews.domain.model.Review;
import com.flashdrop.reviews.domain.model.TargetRatingStats;
import com.flashdrop.reviews.domain.valueobjects.RatingTarget;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para persistir y consultar calificaciones.
 *
 * <p>Implementado por el adaptador JPA sobre {@code internal.reviews}.</p>
 */
public interface ReviewRepositoryPort {

    /**
     * @param review calificación a persistir (puede ser nueva, sin id).
     * @return la calificación persistida, con id asignado.
     */
    Review save(Review review);

    /**
     * @param id id de la calificación.
     * @return la calificación, o vacío si no existe.
     */
    Optional<Review> findById(Long id);

    /**
     * Chequeo de idempotencia (S6): la unicidad real la garantiza el índice
     * {@code uq_review_participant} en la base; esta consulta solo evita el
     * round-trip en el caso normal.
     *
     * @return {@code true} si ya existe una calificación para esa terna.
     */
    boolean existsByOrderIdAndAuthorUserIdAndTargetType(Long orderId, Long authorUserId, RatingTarget targetType);

    /**
     * @return todas las calificaciones que el autor escribió en esa orden
     *         (las "ya hechas" del formulario de pendientes).
     */
    List<Review> findByOrderIdAndAuthorUserId(Long orderId, Long authorUserId);

    /**
     * @return las calificaciones públicas de un destino, paginadas.
     */
    List<Review> findPublishedByTarget(RatingTarget targetType, Long targetId, Pageable pageable);

    /**
     * @return cuántas calificaciones públicas tiene un destino.
     */
    long countPublishedByTarget(RatingTarget targetType, Long targetId);

    /**
     * @return las calificaciones públicas escritas por el autor, paginadas.
     */
    List<Review> findPublishedByAuthor(Long authorUserId, Pageable pageable);

    /**
     * @return cuántas calificaciones públicas escribió el autor.
     */
    long countPublishedByAuthor(Long authorUserId);

    /**
     * Agregado por lotes para el endpoint interno.
     *
     * @param targetType tipo de destino.
     * @param targetIds  ids de destino consultados.
     * @return un agregado por cada id con calificaciones públicas. Los ids sin
     *         calificaciones no aparecen en el resultado.
     */
    List<TargetRatingStats> aggregatePublishedByTargets(RatingTarget targetType, List<Long> targetIds);
}
