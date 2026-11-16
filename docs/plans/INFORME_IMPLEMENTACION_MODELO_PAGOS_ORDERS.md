# Informe de implementación: Modelo de pagos, reembolsos y plazos (spec 4.1)

**Para:** Felipe y el equipo (Orders, Delivery, apps) · **Fecha:** 2026-11-16
**Rama:** `feat/orders-payments-model` (creada desde `origin/main`, commit `1d7cfd9`)
**Estado:** desarrollo terminado y probado (451 pruebas unitarias y 19 de integración en verde). Subido a GitHub, listo para PR.
**Documentos relacionados:** el plan `docs/plan-implementacion-4.1-orders-modelo-y-migraciones.md` y la especificación "FlashDrop 1.0 – Tiendas", sección 4.1

---

## 1. En una frase

Orders ahora sabe guardar **pagos, reembolsos y plazos configurables**, y tiene un estado nuevo, "Pendiente de pago", que la tienda y los repartidores no ven hasta que el pago se apruebe. **Todavía no se cobra nada**: esto prepara el terreno para 4.2 a 4.5.

## 2. Qué cambió para cada persona

| Quién | Antes | Ahora |
|---|---|---|
| **Cliente** | El pedido nace "Nuevo pedido" sin cobrar | Sin cambios visibles hoy. Cuando se active el cobro, su pedido esperará el pago y solo él lo verá |
| **Restaurante** | Ve todos los pedidos de su local | No verá pedidos sin pagar. Sin otro cambio |
| **Repartidor** | Podría tomar cualquier pedido no cerrado | No puede tomar un pedido sin pagar |
| **Administrador** | — | Define los plazos globales (pagar, responder, cancelar). Falta su endpoint (4.3) |
| **Dueño de tienda** | — | Podrá ajustar los plazos de su tienda. Falta su endpoint (4.3) |

## 3. Qué se construyó

Cuatro migraciones, solo aditivas (la versión actual sigue funcionando):

| Migración | Qué guarda |
|---|---|
| **V6** `payments` | Cada intento de cobro. **La base impide cobrar dos veces** el mismo pedido (un solo pago abierto y uno aprobado) |
| **V7** `orders` | Llave anti-duplicados, plazo para pagar y plazo de respuesta de la tienda |
| **V8** `refunds` | Devoluciones **totales o parciales**, con motivo, estado y reintentos |
| **V9** `order_policies` | Plazos configurables: global (admin) y por tienda, campo por campo |

Y en el código: el estado `PENDIENTE_PAGO` con sus transiciones, las reglas de pagos y reembolsos (nunca se devuelve más de lo cobrado), la resolución de plazos y los adaptadores de base de datos (también el perfil legacy de Supabase).

## 4. Pedidos sin pagar: por dónde podían escaparse

| Dónde | Ahora |
|---|---|
| Tomar el pedido (repartidor) | Bloqueado |
| Listado de la tienda | Excluye pendientes (el cliente sí ve el suyo) |
| Detalle del pedido | 403 para tienda y repartidor |
| Pedidos en curso (tienda y repartidor) | No cuentan los pendientes |
| Cambiar el estado | Solo el sistema confirma el pago; ningún rol puede |

## 5. Decisiones del equipo incorporadas

| Decisión | Resultado |
|---|---|
| Reembolso paramétrico | Total o parcial según el caso |
| Cancelación del cliente | No se puede una vez que el producto salió de la tienda (se cobra igual). Antes, hay un plazo desde la compra que define la tienda |
| Plazos paramétricos | Los fija el admin o el dueño; ya no son variables de entorno |
| Doble cobro | Protección en la base de datos, destacada en el PR |
| Supabase | Se mantiene, con las mismas reglas de visibilidad |

## 6. Cómo se probó

`./mvnw verify`: 451 unitarias y 19 de integración con PostgreSQL real. Lo más importante:
- Cada restricción de V6 a V9 contra la base: lo imposible falla.
- **Prueba de actualización:** una base con pedidos reales de V1 a V5 recibe V6 a V9 y los pedidos antiguos quedan intactos.
- Una prueba por cada fuga de "pedido sin pagar".

## 7. Observaciones y pendientes

**Observaciones (no bloquean):**
1. Las apps y la web deben soportar el estado "Pendiente de pago" **antes** de activar el cobro.
2. Los pedidos históricos sin pago: decidir cómo cuentan cuando `sales-summary` pase a "solo pagados" (4.5).
3. Un pedido sin pagar cuenta como "en curso" solo para su cliente; se confirma en 4.5.
4. Al reintentar un pago (4.3), cerrar el intento anterior en la base antes de abrir el siguiente.
5. Falta el endpoint para editar los plazos y aplicar la ventana de cancelación (4.3).

**Pendientes (se definen después):**
- Respaldo y ensayo de V6 a V9 sobre una copia de la base del servidor (quién y cuándo).
- Acordar desplegar primero las tablas y activar el cobro en un despliegue posterior.
- Datos de la ficha de producto (fuera de orders).

## 8. Qué sigue

4.2 puerto de pago (`PaymentGateway` y `FakePaymentGateway`), 4.3 endpoints (incluida la edición de plazos y la cancelación con ventana), 4.4 tareas programadas y 4.5 eventos y resumen de ventas.
