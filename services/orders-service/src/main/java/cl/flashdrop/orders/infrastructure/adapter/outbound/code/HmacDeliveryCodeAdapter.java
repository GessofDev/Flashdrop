package cl.flashdrop.orders.infrastructure.adapter.outbound.code;

import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.port.DeliveryCodePort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/**
 * Deriva el código de entrega con HMAC-SHA256 sobre (pedido, versión) usando una clave secreta
 * del servicio ({@code flashdrop/delivery-code-secret} en Secrets Manager), de modo que el
 * código nunca queda guardado en la base de datos.
 *
 * <p>Los pedidos en modo SEGURO nunca reciben {@code 000000}: el rango es 000001-999999.
 * Sin clave configurada solo funciona el modo NORMAL; pedir un código SEGURO falla de forma
 * explícita en vez de generar uno predecible.</p>
 */
@Slf4j
@Component
public class HmacDeliveryCodeAdapter implements DeliveryCodePort {

    private static final String ALGORITHM = "HmacSHA256";
    private static final long CODE_SPACE = 999_999L;

    private final byte[] secret;

    public HmacDeliveryCodeAdapter(@Value("${orders.delivery-code.secret:}") String secret) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    @PostConstruct
    void warnIfMissingSecret() {
        if (secret.length == 0) {
            log.warn("orders.delivery-code.secret no configurado: el modo de recepcion SEGURO no estara disponible");
        }
    }

    @Override
    public String currentCodeFor(Order order) {
        if (!order.isSecureReception()) {
            return Order.DEFAULT_DELIVERY_CODE;
        }
        if (secret.length == 0) {
            throw new IllegalStateException("Clave del codigo de entrega no configurada (DELIVERY_CODE_SECRET)");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            String material = IdConverter.toLong(order.getId()) + ":" + order.getDeliveryCodeVersion();
            long value = ByteBuffer.wrap(mac.doFinal(material.getBytes(StandardCharsets.UTF_8)), 0, 4).getInt()
                    & 0xFFFFFFFFL;
            return String.format("%06d", value % CODE_SPACE + 1);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo derivar el codigo de entrega", e);
        }
    }
}
