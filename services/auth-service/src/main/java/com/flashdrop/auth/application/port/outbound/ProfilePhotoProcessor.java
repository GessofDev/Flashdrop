package com.flashdrop.auth.application.port.outbound;

/**
 * Valida una foto de perfil y la normaliza: la endereza segun su orientacion,
 * la reduce y la reescribe como JPEG sin metadatos (sin EXIF ni ubicacion).
 */
public interface ProfilePhotoProcessor {

    String OUTPUT_CONTENT_TYPE = "image/jpeg";

    /**
     * @param content             bytes tal como llegaron
     * @param declaredContentType tipo que declaro el cliente; puede ser null o generico
     * @return JPEG normalizado
     * @throws com.flashdrop.auth.domain.exception.InvalidProfilePhotoException  si no es JPEG o PNG legible
     * @throws com.flashdrop.auth.domain.exception.ProfilePhotoTooLargeException si supera peso o dimensiones
     */
    byte[] normalize(byte[] content, String declaredContentType);
}
