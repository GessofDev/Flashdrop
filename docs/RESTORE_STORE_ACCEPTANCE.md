# Restaurar aceptacion del comercio

Base: main `23a83a4`, revisado el 24-11-2026.

## Comportamiento

Confirmar un pago (simulado o real) conserva el pago APROBADO y activa el pedido
en Nuevo pedido. El comercio lo acepta para pasar a Preparando, y despues marca
Listo para retiro. El pago no escribe acceptedAt ni inicia la espera de repartidor.
La ruta se sigue creando al pagar, pero el repartidor solo puede tomar un pedido
listo para retiro. order.created informa Nuevo pedido.

La actualizacion es condicional al estado pendiente y sigue siendo idempotente.
Los pedidos ya cancelados no se reactivan. No hay migraciones ni modificaciones
de datos existentes: los que ya estan en Preparando no retroceden.
No se reintroduce un vencimiento automatico de aceptacion; eso es una decision
funcional separada. El timeout de repartidor empieza al marcar listo para retiro.

## Notificacion al comercio cuando delivery toma el pedido

main ya asigna deliveryId al tomarlo y lo expone en OrderListResponse. El claim
conserva el estado Listo para retiro: no hay que marcar Retirado o En camino para
forzar un aviso. La app 1.0.57 detecta el cambio de deliveryId mediante el sondeo F9.
No requiere migracion ni un endpoint nuevo para ese aviso local.

## Verificacion

Pruebas sin Docker: ConfirmPaymentUseCaseTest, UpdateOrderStatusUseCaseTest,
OrderDomainTest, RoleTransitionPolicyTest, PaymentActivationPersistenceTest y
ClaimDeliveryOrdersUseCaseTest: 74 pruebas aprobadas con JDK 21.

Desde services/orders-service:

```powershell
./mvnw.cmd -B "-Dtest=ConfirmPaymentUseCaseTest,UpdateOrderStatusUseCaseTest,OrderDomainTest,RoleTransitionPolicyTest,PaymentActivationPersistenceTest,ClaimDeliveryOrdersUseCaseTest" test
```

Pruebas con Docker/PostgreSQL: DeliveryTariffAndPaymentConfirmationDbTest,
UnattendedOrdersDbTest y PaidOrderLifecycleFullContextTest. Actualizadas para
comprobar Nuevo pedido, acceptedAt vacio, aceptacion manual y paso a retiro.
No pudieron ejecutarse localmente porque Docker Desktop no estaba iniciado.

## Entrega

Rama del PR: codex/restore-store-acceptance. Requiere revision y validacion de
integracion antes de fusionar; abrir el PR no despliega el cambio. Tras la
aprobacion, desplegar solo orders-service. No cambiar pedidos ya activos a
Nuevo pedido mediante SQL.
Despues del despliegue probar con un pedido nuevo y dos cuentas separadas:
cliente y comercio. El comercio debe poder Aceptar/Rechazar antes de preparar.
