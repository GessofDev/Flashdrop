import { StarIcon } from './StarIcon';

/** The five scores of the scale. */
export const RATING_VALUES = [1, 2, 3, 4, 5] as const;

/** Highest score of the scale. */
export const RATING_MAX = 5;

export interface StarRatingDisplayProps {
  /** Score the backend sent, from 1 to 5. */
  value: number;
  /** Size of the stars, in pixels. */
  size?: number;
}

/**
 * Stars of a score that nobody can change.
 *
 * It shows the score of the backend and announces it as one image, so the
 * assistive technology reads "4 de 5 estrellas" instead of five separate
 * symbols.
 */
export function StarRatingDisplay({ value, size = 16 }: StarRatingDisplayProps) {
  const score = Math.min(RATING_MAX, Math.max(0, Math.round(value)));

  return (
    <span
      role="img"
      aria-label={`${score} de ${RATING_MAX} estrellas`}
      className="inline-flex items-center gap-0.5"
    >
      {RATING_VALUES.map((star) => (
        <StarIcon key={star} filled={star <= score} size={size} />
      ))}
    </span>
  );
}
