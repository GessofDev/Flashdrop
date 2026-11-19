package cl.flashdrop.orders.domain.model;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentMethodTest {

    @Test
    void soloTarjetaSeAceptaParaCobrar() {
        assertTrue(PaymentMethod.TARJETA.isAcceptedForCharge());
        assertFalse(PaymentMethod.EFECTIVO.isAcceptedForCharge());
        assertFalse(PaymentMethod.TRANSFERENCIA.isAcceptedForCharge());
    }

    @Test
    void sinValorSeAsumeTarjetaYTarjetaSeAcepta() {
        assertEquals(PaymentMethod.TARJETA, PaymentMethod.fromRequest(null));
        assertEquals(PaymentMethod.TARJETA, PaymentMethod.fromRequest("  "));
        assertEquals(PaymentMethod.TARJETA, PaymentMethod.fromRequest("tarjeta"));
    }

    @Test
    void efectivoTransferenciaYDesconocidosSeRechazan() {
        for (String value : new String[]{"Efectivo", "TRANSFERENCIA", "cheque"}) {
            OrderDomainException ex = assertThrows(OrderDomainException.class, () -> PaymentMethod.fromRequest(value));
            assertTrue(ex.getMessage().contains("solo se acepta Tarjeta"));
        }
    }

    @Test
    void losPedidosHistoricosSiguenLeyendoseConSuMetodoOriginal() {
        assertEquals(PaymentMethod.EFECTIVO, PaymentMethod.fromValue("Efectivo"));
        assertEquals(PaymentMethod.TRANSFERENCIA, PaymentMethod.fromValue("Transferencia"));
        assertEquals(PaymentMethod.EFECTIVO, PaymentMethod.fromValue(null));
    }
}
