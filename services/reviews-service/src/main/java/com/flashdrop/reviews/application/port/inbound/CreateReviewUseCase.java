package com.flashdrop.reviews.application.port.inbound;

import com.flashdrop.reviews.application.dto.CreateReviewCommand;
import com.flashdrop.reviews.domain.model.Review;

/**
 * Caso de uso: crear una calificación (T7).
 *
 * <p>Reglas que aplica (S3, S4, S6, S7):</p>
 * <ul>
 *   <li>La orden debe existir y estar {@code ENTREGADO}.</li>
 *   <li>El autor debe tener un rol del claim que habilite el tipo de destino
 *       (matriz §4) <b>y</b> ser participante de esa orden.</li>
 *   <li>El id del destino lo deriva el servidor desde la orden.</li>
 *   <li>No hay auto-calificación.</li>
 *   <li>Una sola calificación por (orden, autor, destino): el reintento es 409.</li>
 * </ul>
 */
public interface CreateReviewUseCase {

    /**
     * @param command datos de la calificación y la identidad del autor.
     * @return la calificación creada.
     */
    Review execute(CreateReviewCommand command);
}
