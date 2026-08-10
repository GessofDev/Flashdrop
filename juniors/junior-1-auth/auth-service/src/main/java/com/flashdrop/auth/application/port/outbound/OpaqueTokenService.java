package com.flashdrop.auth.application.port.outbound;

/**
 * Genera y hashea tokens opacos (refresh tokens). El valor crudo se entrega
 * al cliente una sola vez; en la BD solo se guarda el hash.
 */
public interface OpaqueTokenService {
    String generate();
    String hash(String rawToken);
}
