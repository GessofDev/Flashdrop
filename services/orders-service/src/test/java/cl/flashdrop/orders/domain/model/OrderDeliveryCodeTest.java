package cl.flashdrop.orders.domain.model;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Reglas de intentos del código de entrega: 3 intentos por código, máximo 2 códigos, luego bloqueo. */
class OrderDeliveryCodeTest {

    private Order secureOrder() {
        return Order.builder().status(OrderStatus.RETIRADO).receptionMode(ReceptionMode.SEGURO).build();
    }

    @Test
    void pedidoNuevoEmpiezaEnModoNormalConElPrimerCodigo() {
        Order order = Order.builder().status(OrderStatus.NUEVO_PEDIDO).build();

        assertEquals(ReceptionMode.NORMAL, order.getReceptionMode());
        assertEquals(1, order.getDeliveryCodeVersion());
        assertEquals(0, order.getDeliveryCodeAttempts());
        assertFalse(order.isSecureReception());
        assertFalse(order.isDeliveryCodeLocked());
    }

    @Test
    void losDosPrimerosFallosSoloDescuentanIntentos() {
        Order order = secureOrder();

        assertFalse(order.registerFailedCodeAttempt());
        assertEquals(2, order.remainingCodeAttempts());
        assertFalse(order.registerFailedCodeAttempt());
        assertEquals(1, order.remainingCodeAttempts());
        assertEquals(1, order.getDeliveryCodeVersion());
    }

    @Test
    void elTercerFalloAnulaElCodigoYEmiteUnoNuevo() {
        Order order = secureOrder();
        order.registerFailedCodeAttempt();
        order.registerFailedCodeAttempt();

        boolean renewed = order.registerFailedCodeAttempt();

        assertTrue(renewed);
        assertEquals(2, order.getDeliveryCodeVersion());
        assertEquals(0, order.getDeliveryCodeAttempts());
        assertEquals(3, order.remainingCodeAttempts());
        assertFalse(order.isDeliveryCodeLocked());
    }

    @Test
    void agotadosLosIntentosDelSegundoCodigoElPedidoQuedaBloqueado() {
        Order order = secureOrder();
        for (int i = 0; i < 3; i++) order.registerFailedCodeAttempt();   // codigo 1 agotado -> codigo 2

        order.registerFailedCodeAttempt();
        order.registerFailedCodeAttempt();
        boolean renewed = order.registerFailedCodeAttempt();             // codigo 2 agotado

        assertFalse(renewed, "No hay un tercer codigo");
        assertTrue(order.isDeliveryCodeLocked());
        assertEquals(2, order.getDeliveryCodeVersion());
        assertEquals(0, order.remainingCodeAttempts());
    }

    @Test
    void modoNormalNuncaSeBloquea() {
        Order order = Order.builder().status(OrderStatus.RETIRADO).deliveryCodeVersion(2).deliveryCodeAttempts(3).build();

        assertFalse(order.isDeliveryCodeLocked());
    }

    @Test
    void recepcionPorDefectoEsNormalYLosValoresSeInterpretanSinImportarMayusculas() {
        assertEquals(ReceptionMode.NORMAL, ReceptionMode.fromRequest(null));
        assertEquals(ReceptionMode.NORMAL, ReceptionMode.fromRequest("  "));
        assertEquals(ReceptionMode.SEGURO, ReceptionMode.fromRequest("seguro"));
        assertEquals(ReceptionMode.NORMAL, ReceptionMode.fromRequest("Normal"));
    }

    @Test
    void modoDeRecepcionInvalidoSeRechaza() {
        assertThrows(OrderDomainException.class, () -> ReceptionMode.fromRequest("express"));
    }
}
