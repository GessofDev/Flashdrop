import type { CategoryResponse } from '@/api';

export interface CategoryChipsProps {
  /** Categories the catalog answered with. */
  categories: CategoryResponse[];
  /** Category in use; `null` means every category. */
  selectedId: number | null;
  /** Choose a category; `null` goes back to every category. */
  onSelect: (categoryId: number | null) => void;
}

/**
 * Chips of the categories, as in the mockup.
 *
 * The chip "Todas" does not filter: it asks the catalog for every product.
 * The other chips carry the `categoryId` of the query string.
 */
export function CategoryChips({ categories, selectedId, onSelect }: CategoryChipsProps) {
  const chipClasses = (isSelected: boolean) =>
    [
      'shrink-0 rounded-control px-4 py-2 text-sm font-semibold transition-colors',
      isSelected
        ? 'bg-primary text-white shadow-card'
        : 'bg-surface text-foreground-secondary hover:bg-surface-hover',
    ].join(' ');

  return (
    <section aria-labelledby="marketplace-categories-title" className="flex flex-col gap-3">
      <h2
        id="marketplace-categories-title"
        className="font-display text-xl font-bold tracking-tight text-foreground"
      >
        Explorar por categoría
      </h2>
      <div role="group" aria-label="Categorías" className="flex items-center gap-2 overflow-x-auto pb-1">
        <button
          type="button"
          aria-pressed={selectedId === null}
          className={chipClasses(selectedId === null)}
          onClick={() => onSelect(null)}
        >
          Todas
        </button>
        {categories.map((category) => (
          <button
            key={category.id}
            type="button"
            aria-pressed={selectedId === category.id}
            className={chipClasses(selectedId === category.id)}
            onClick={() => onSelect(category.id)}
          >
            {category.name}
          </button>
        ))}
      </div>
    </section>
  );
}
