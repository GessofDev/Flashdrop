export interface MarketplaceHeroProps {
  /** Restaurants the catalog answered with. */
  restaurantCount: number;
  /** Dishes the catalog answered with. */
  productCount: number;
  /** Categories the catalog answered with. */
  categoryCount: number;
}

/**
 * Hero of the marketplace, built from the mockup
 * `marketplace_y_cat_logo_de_clientes_flashdrop_v2.0`.
 *
 * The structure of the mockup is kept — the badge, the headline, the pitch and
 * the row of facts — but the blocks the backend cannot back are gone: there
 * is no discount code, no customer score, no delivery time per restaurant, no
 * shipping cost and no payment gateway. The numbers of the row of facts are
 * the ones the catalog answered with.
 */
export function MarketplaceHero({
  restaurantCount,
  productCount,
  categoryCount,
}: MarketplaceHeroProps) {
  const facts = [
    `${restaurantCount} ${restaurantCount === 1 ? 'local disponible' : 'locales disponibles'}`,
    `${productCount} ${productCount === 1 ? 'plato en catálogo' : 'platos en catálogo'}`,
    `${categoryCount} ${categoryCount === 1 ? 'categoría' : 'categorías'}`,
  ];

  return (
    <section
      aria-labelledby="marketplace-hero-title"
      className="rounded-container border border-border bg-surface p-6 shadow-card sm:p-8"
    >
      <div className="flex flex-col gap-3">
        <span className="inline-flex w-fit items-center gap-2 rounded-full bg-primary-surface px-3 py-1 text-xs font-bold text-primary">
          Delivery de comida
        </span>
        <h1
          id="marketplace-hero-title"
          className="font-display text-3xl font-bold tracking-tight text-foreground"
        >
          Pide en minutos con FlashDrop Chile
        </h1>
        <p className="max-w-xl text-sm text-foreground-secondary">
          Arma tu pedido con los locales del catálogo, declara cómo quieres pagar y confirma el
          despacho desde la misma app.
        </p>
        <ul className="flex flex-wrap items-center gap-2 pt-2">
          {facts.map((fact) => (
            <li
              key={fact}
              className="rounded-full bg-surface-hover px-3 py-1 text-xs font-medium text-foreground-secondary"
            >
              {fact}
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
