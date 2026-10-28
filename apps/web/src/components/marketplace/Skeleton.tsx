/**
 * The placeholder of a loading section now lives in the design system
 * (`src/components/ui/Skeleton.tsx`), because the marketplace and the orders
 * of the customer use it.
 *
 * This re-export keeps the public API of the marketplace untouched until the
 * cleanup task removes it.
 */
export { Skeleton } from '@/components/ui/Skeleton';
export type { SkeletonProps } from '@/components/ui/Skeleton';
