import type { HTMLAttributes } from 'react';

export type BadgeVariant = 'success' | 'info' | 'warning' | 'destructive';

const BASE_CLASSES = [
  'inline-flex items-center',
  'h-6 rounded-control px-2',
  'text-xs font-semibold tracking-label',
].join(' ');

const VARIANT_CLASSES: Record<BadgeVariant, string> = {
  success: 'bg-success-surface text-success',
  info: 'bg-info-surface text-info',
  warning: 'bg-warning-surface text-warning',
  destructive: 'bg-destructive-surface text-destructive',
};

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  variant?: BadgeVariant;
}

/**
 * Status chip. The surface colour and the tone colour both come from the
 * semantic design tokens.
 */
export function Badge({ variant = 'info', className, ...rest }: BadgeProps) {
  const classes = [BASE_CLASSES, VARIANT_CLASSES[variant], className]
    .filter((value): value is string => Boolean(value))
    .join(' ');

  return <span className={classes} {...rest} />;
}
