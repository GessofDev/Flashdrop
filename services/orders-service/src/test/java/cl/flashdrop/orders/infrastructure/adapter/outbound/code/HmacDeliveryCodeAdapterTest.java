package cl.flashdrop.orders.infrastructure.adapter.outbound.code;

import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.ReceptionMode;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HmacDeliveryCodeAdapterTest {

    private static Order order(long id, ReceptionMode mode, int version) {
        return Order.builder().id(IdConverter.toUuid(id)).status(OrderStatus.RETIRADO)
                .receptionMode(mode).deliveryCodeVersion(version).build();
    }

    @Test
    void modoNormalSiempreDevuelveElCodigoFijoYNoNecesitaClave() {
        HmacDeliveryCodeAdapter adapter = new HmacDeliveryCodeAdapter("");

        assertEquals("000000", adapter.currentCodeFor(order(1, ReceptionMode.NORMAL, 1)));
    }

    @Test
    void modoSeguroDevuelveSeisDigitosDistintosDeCeroYEstables() {
        HmacDeliveryCodeAdapter adapter = new HmacDeliveryCodeAdapter("clave-de-prueba");

        String code = adapter.currentCodeFor(order(77, ReceptionMode.SEGURO, 1));

        assertTrue(code.matches("\\d{6}"), code);
        assertNotEquals("000000", code);
        assertEquals(code, adapter.currentCodeFor(order(77, ReceptionMode.SEGURO, 1)), "Debe ser determinista");
    }

    @Test
    void elCodigoCambiaAlRenovarseYEntrePedidos() {
        HmacDeliveryCodeAdapter adapter = new HmacDeliveryCodeAdapter("clave-de-prueba");

        String original = adapter.currentCodeFor(order(77, ReceptionMode.SEGURO, 1));
        String renovado = adapter.currentCodeFor(order(77, ReceptionMode.SEGURO, 2));
        String otroPedido = adapter.currentCodeFor(order(78, ReceptionMode.SEGURO, 1));

        assertNotEquals(original, renovado);
        assertNotEquals(original, otroPedido);
    }

    @Test
    void otraClaveProduceOtrosCodigos() {
        Order order = order(77, ReceptionMode.SEGURO, 1);

        assertNotEquals(new HmacDeliveryCodeAdapter("clave-a").currentCodeFor(order),
                new HmacDeliveryCodeAdapter("clave-b").currentCodeFor(order));
    }

    @Test
    void jamasGeneraElCodigoFijoNiSaleDelRango() {
        HmacDeliveryCodeAdapter adapter = new HmacDeliveryCodeAdapter("clave-de-prueba");
        Set<String> seen = new HashSet<>();
        for (long id = 1; id <= 3000; id++) {
            String code = adapter.currentCodeFor(order(id, ReceptionMode.SEGURO, 1));
            assertNotEquals("000000", code);
            assertEquals(6, code.length());
            seen.add(code);
        }
        assertTrue(seen.size() > 2900, "Los codigos deben repartirse bien: " + seen.size());
    }

    @Test
    void modoSeguroSinClaveFallaEnVezDeGenerarUnCodigoPredecible() {
        HmacDeliveryCodeAdapter adapter = new HmacDeliveryCodeAdapter("");

        assertThrows(IllegalStateException.class,
                () -> adapter.currentCodeFor(order(1, ReceptionMode.SEGURO, 1)));
    }
}
