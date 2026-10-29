import { Card } from '@/components/ui';

export interface RoutePlaceholderProps {
  /** Title shown as the page heading. */
  title: string;
  /** Short explanation of what this route will hold. */
  description: string;
  /** Mockup this screen will be built from (spec S3). */
  mockup: string;
}

/**
 * Placeholder rendered by every route until its screen task lands.
 *
 * It states the origin mockup so the mapping stays traceable from the first
 * commit on.
 */
export function RoutePlaceholder({ title, description, mockup }: RoutePlaceholderProps) {
  return (
    <div className="mx-auto w-full max-w-app px-4 py-8 md:px-6 lg:px-8">
      <Card className="flex flex-col gap-2">
        <p className="label-code text-xs text-foreground-muted">FlashDrop</p>
        <h1 className="font-display text-2xl font-bold text-foreground">{title}</h1>
        <p className="text-sm text-foreground-secondary">{description}</p>
        <p className="mt-4 text-xs text-foreground-muted">
          Maqueta de origen: <span className="nums-tabular">{mockup}</span>
        </p>
      </Card>
    </div>
  );
}
