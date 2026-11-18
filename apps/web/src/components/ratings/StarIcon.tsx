export interface StarIconProps {
  /** Whether the star is drawn filled. */
  filled: boolean;
  /** Size of the icon, in pixels. */
  size?: number;
}

/**
 * Star of the rating scale.
 *
 * It is drawn with the tokens of the theme (`text-warning` for the filled
 * one, `text-border` for the empty one), so it never declares a colour of
 * its own. The icon is decorative: the score it belongs to is announced by
 * the component that groups the stars.
 */
export function StarIcon({ filled, size = 20 }: StarIconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      aria-hidden="true"
      className={filled ? 'text-warning' : 'text-border'}
      fill={filled ? 'currentColor' : 'none'}
      stroke={filled ? 'none' : 'currentColor'}
      strokeWidth={1.5}
    >
      <path d="M12 2.6l2.9 5.9 6.5.95-4.7 4.6 1.11 6.45L12 17.45l-5.81 3.05 1.11-6.45-4.7-4.6 6.5-.95L12 2.6z" />
    </svg>
  );
}
