import { StarIcon } from './StarIcon';
import { RATING_VALUES } from './StarRatingDisplay';

export interface StarRatingInputProps {
  /** Score chosen so far, `null` when nobody chose one yet. */
  value: number | null;
  /** Called with the score the user chose. */
  onChange: (value: number) => void;
  /** Whether the input is blocked. */
  disabled?: boolean;
  /** Name of the group of stars, for the assistive technology. */
  label?: string;
}

/**
 * Stars the user chooses a score with.
 *
 * Every star is a button with the name "Calificar con N estrellas", so the
 * scale works with the keyboard alone and the assistive technology says how
 * many stars each one gives. The chosen star is the pressed one, and the
 * stars before it are filled.
 */
export function StarRatingInput({ value, onChange, disabled = false, label }: StarRatingInputProps) {
  return (
    <div
      role="group"
      aria-label={label ?? 'Puntaje de la entrega'}
      className="inline-flex items-center gap-1"
    >
      {RATING_VALUES.map((star) => {
        const selected = value === star;

        return (
          <button
            key={star}
            type="button"
            aria-label={`Calificar con ${star} ${star === 1 ? 'estrella' : 'estrellas'}`}
            aria-pressed={selected}
            disabled={disabled}
            onClick={() => {
              onChange(star);
            }}
            className="rounded-control p-1 transition-colors hover:bg-surface-hover focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 disabled:pointer-events-none disabled:opacity-45"
          >
            <StarIcon filled={value !== null && star <= value} size={24} />
          </button>
        );
      })}
    </div>
  );
}
