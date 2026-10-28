export interface SkeletonProps {
  /** What is loading; it names the region for the assistive technology. */
  label: string;
  /** Number of the placeholder blocks. */
  blocks?: number;
  /** Height of the placeholders. */
  height?: string;
}

/**
 * Placeholder of a section that is still loading.
 *
 * It keeps the layout of the section it replaces, so the page does not jump
 * when the answer arrives, and it announces what is loading.
 */
export function Skeleton({ label, blocks = 3, height = 'h-40' }: SkeletonProps) {
  return (
    <div role="status" aria-label={label} aria-busy="true" className="flex flex-col gap-4">
      {Array.from({ length: blocks }, (_, index) => (
        <div
          key={index}
          aria-hidden="true"
          className={`animate-pulse rounded-container bg-surface-hover ${height}`}
        />
      ))}
    </div>
  );
}
