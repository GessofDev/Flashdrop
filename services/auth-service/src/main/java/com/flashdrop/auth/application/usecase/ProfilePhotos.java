package com.flashdrop.auth.application.usecase;

import java.util.UUID;

/**
 * Convencion de claves y URL de las fotos de perfil.
 *
 * <p>{@code users.photo} guarda la clave del objeto ({@code avatars/{userId}/{id}.jpg}),
 * nunca una URL. Hacia afuera se expone {@code /auth/profile/photo?v={id}}: el
 * {@code v} cambia con cada foto, asi el cliente no muestra una vieja de su cache.
 * Cualquier otro valor guardado (p. ej. una URL de antes de F10) se trata como "sin foto".
 */
public final class ProfilePhotos {

    static final String PREFIX = "avatars/";
    private static final String EXTENSION = ".jpg";
    private static final String URL = "/auth/profile/photo?v=";

    private ProfilePhotos() {
    }

    public static String folderOf(Long userId) {
        return PREFIX + userId + "/";
    }

    static String newKey(Long userId) {
        return folderOf(userId) + UUID.randomUUID() + EXTENSION;
    }

    /** true si el valor es una clave de foto de este usuario. */
    static boolean isStoredKey(String value, Long userId) {
        return value != null && userId != null
                && value.startsWith(folderOf(userId)) && value.endsWith(EXTENSION)
                && value.indexOf('/', folderOf(userId).length()) < 0;
    }

    /** URL publica de la foto del usuario, o null si no tiene. */
    public static String urlFor(String storedValue, Long userId) {
        if (!isStoredKey(storedValue, userId)) {
            return null;
        }
        String file = storedValue.substring(folderOf(userId).length());
        return URL + file.substring(0, file.length() - EXTENSION.length());
    }
}
