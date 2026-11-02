import type { ChangeEvent } from 'react';

export interface MarketplaceSearchProps {
  /** Current search term. */
  value: string;
  /** Callback fired when the search term changes. */
  onChange: (value: string) => void;
  /** Callback fired when the user clears the search term. */
  onClear: () => void;
}

/**
 * Search bar of the customer marketplace.
 *
 * Allows searching by restaurant name or by available dish name (e.g. "pizza"),
 * filtering the restaurant grid to show matching locals.
 */
export function MarketplaceSearch({ value, onChange, onClear }: MarketplaceSearchProps) {
  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
    onChange(e.target.value);
  };

  return (
    <div className="relative w-full">
      <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-foreground-muted">
        <svg
          aria-hidden="true"
          className="h-4 w-4"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
          />
        </svg>
      </div>

      <input
        type="search"
        role="searchbox"
        aria-label="Buscar restaurante o plato"
        placeholder="Busca tu restaurante o plato favorito (ej: pizza)..."
        value={value}
        onChange={handleChange}
        className="w-full rounded-container border border-border bg-surface py-2.5 pl-10 pr-10 text-sm text-foreground placeholder:text-foreground-muted shadow-card transition-colors focus:border-primary focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2"
      />

      {value.trim() !== '' ? (
        <button
          type="button"
          onClick={onClear}
          aria-label="Limpiar búsqueda"
          className="absolute inset-y-0 right-0 flex items-center pr-3 text-foreground-muted hover:text-foreground focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 rounded"
        >
          <svg
            aria-hidden="true"
            className="h-4 w-4"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M6 18L18 6M6 6l12 12"
            />
          </svg>
        </button>
      ) : null}
    </div>
  );
}
