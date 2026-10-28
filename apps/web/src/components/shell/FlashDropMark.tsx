export interface FlashDropMarkProps {
  /** Classes of the icon; it inherits the color of its container. */
  className?: string;
}

/**
 * Lightning bolt of the FlashDrop brand.
 *
 * The mark is drawn inline, as an SVG, so it inherits the color of the
 * surface that holds it and it never needs an external asset.
 */
export function FlashDropMark({ className }: FlashDropMarkProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={className}
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
    >
      <path d="M13.2 2 5.6 13.1a.6.6 0 0 0 .47.98h4.3l-1.1 7.02a.6.6 0 0 0 1.03.46l7.6-11.1a.6.6 0 0 0-.47-.98h-4.3l1.1-7.02a.6.6 0 0 0-1.03-.46Z" />
    </svg>
  );
}
