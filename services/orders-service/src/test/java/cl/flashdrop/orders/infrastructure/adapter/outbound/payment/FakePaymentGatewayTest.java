package cl.flashdrop.orders.infrastructure.adapter.outbound.payment;

import cl.flashdrop.orders.domain.exception.ForbiddenOperationException;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.port.PaymentGatewayPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FakePaymentGatewayTest {

    private final FakePaymentGateway gateway = new FakePaymentGateway("s3cret");

    @Test
    void abreUnCobroConIdentificadorPropioYUnEnlaceDePago() {
        PaymentGatewayPort.PaymentSession a = gateway.createSession(UUID.randomUUID(), BigDecimal.TEN, "CLP");
        PaymentGatewayPort.PaymentSession b = gateway.createSession(UUID.randomUUID(), BigDecimal.TEN, "CLP");

        assertEquals("FAKE", gateway.providerName());
        assertNotEquals(a.providerTxId(), b.providerTxId());
        assertEquals(gateway.paymentUrlFor(a.providerTxId()), a.paymentUrl());
        assertTrue(a.paymentUrl().endsWith(a.providerTxId()));
    }

    @Test
    void avisoFirmadoAprobadoYRechazado() {
        assertTrue(gateway.verifyNotification("tx", "APPROVED", "s3cret").approved());
        assertTrue(gateway.verifyNotification("tx", "approved", "s3cret").approved());
        assertFalse(gateway.verifyNotification("tx", "REJECTED", "s3cret").approved());
        assertFalse(gateway.verifyNotification("tx", "declined", "s3cret").approved());
    }

    @Test
    void firmaInvalidaOAusenteSeRechaza() {
        assertThrows(ForbiddenOperationException.class, () -> gateway.verifyNotification("tx", "APPROVED", "otra"));
        assertThrows(ForbiddenOperationException.class, () -> gateway.verifyNotification("tx", "APPROVED", null));
    }

    @Test
    void sinSecretoConfiguradoSeRechazaTodoAviso() {
        FakePaymentGateway sinSecreto = new FakePaymentGateway("");
        assertThrows(ForbiddenOperationException.class, () -> sinSecreto.verifyNotification("tx", "APPROVED", ""));
        assertThrows(ForbiddenOperationException.class, () -> sinSecreto.verifyNotification("tx", "APPROVED", null));
    }

    @Test
    void estadoDesconocidoSeRechazaComoErrorDeValidacion() {
        assertThrows(OrderDomainException.class, () -> gateway.verifyNotification("tx", "maybe", "s3cret"));
        assertThrows(OrderDomainException.class, () -> gateway.interpret("tx", null));
    }
}
