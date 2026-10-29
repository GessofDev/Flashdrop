/**
 * Commitment of the platform, next to the order.
 *
 * The mockup paints a guarantee card with percentages, deadlines and a
 * refund wallet. None of that comes from the backend — there is no policy
 * endpoint and no wallet — so the card keeps the title and the promise of the
 * brand and states the policy in words, **without any invented metric**.
 */
export function GuaranteeCard() {
  return (
    <section
      aria-labelledby="guarantee-title"
      className="flex flex-col gap-3 rounded-container border border-border bg-surface p-5 shadow-card"
    >
      <div className="flex flex-col gap-0.5">
        <h2 id="guarantee-title" className="font-display text-lg font-bold text-foreground">
          Garantía Térmica y de Tiempo
        </h2>
        <span className="text-sm text-foreground-secondary">
          Compromiso de calidad y puntualidad
        </span>
      </div>

      <p className="text-sm text-foreground-secondary">
        Acompañamos tu pedido desde que el restaurante lo entrega al repartidor hasta que lo
        recibes. Cada etapa queda registrada en la bitácora del pedido, y el estado del reparto se
        muestra en la misma pantalla.
      </p>
    </section>
  );
}
