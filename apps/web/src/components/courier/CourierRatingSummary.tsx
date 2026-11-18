import { RatingAverage } from '@/components/ratings';

export interface CourierRatingSummaryProps {
  /** Average of the scores of the courier, when the backend sends it. */
  average: number | null;
  /** Number of scores behind the average. */
  count: number | null;
}

/**
 * Average of the scores of a courier (spec S8).
 *
 * The delivery service publishes the aggregate of the ratings with the
 * profile of the courier. Until it does, the component shows nothing: the
 * screen never shows a number the backend did not send.
 */
export function CourierRatingSummary({ average, count }: CourierRatingSummaryProps) {
  if (average === null || count === null || count <= 0) {
    return null;
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      <span className="text-sm font-semibold text-foreground">Valoración del repartidor</span>
      <RatingAverage average={average} count={count} showStars />
    </div>
  );
}
