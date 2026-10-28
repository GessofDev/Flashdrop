import { useState } from 'react';

export interface CatalogImageProps {
  /** URL of the picture; the catalog may answer without one. */
  src: string | null;
  /** Name of the dish or of the restaurant, used as the alternative text. */
  alt: string;
  /** Classes of the frame, so the picture keeps the layout of its slot. */
  className?: string;
}

/**
 * Picture of a restaurant or of a dish.
 *
 * The catalog answers with a nullable `image`, so the component paints a
 * neutral placeholder when there is no picture — or when the URL does not
 * load — instead of leaving a broken image in the layout.
 */
export function CatalogImage({ src, alt, className }: CatalogImageProps) {
  const [failed, setFailed] = useState(false);
  const hasSource = src !== null && src.trim().length > 0;

  const frame = [
    'flex h-full w-full items-center justify-center overflow-hidden bg-surface-hover',
    className,
  ]
    .filter((value): value is string => Boolean(value))
    .join(' ');

  if (!hasSource || failed) {
    return (
      <div role="img" aria-label={alt} className={frame}>
        <span className="text-xs font-medium text-foreground-muted">Sin imagen</span>
      </div>
    );
  }

  return (
    <img
      src={src}
      alt={alt}
      className={`h-full w-full object-cover ${className ?? ''}`.trim()}
      onError={() => setFailed(true)}
    />
  );
}
