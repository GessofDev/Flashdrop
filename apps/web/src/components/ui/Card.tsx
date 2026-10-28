import type { HTMLAttributes } from 'react';

export type CardProps = HTMLAttributes<HTMLDivElement>;

/**
 * Surface container: 16px radius, surface background, hairline border and the
 * resting elevation defined by the design system.
 */
export function Card({ className, ...rest }: CardProps) {
  const classes = ['rounded-container border border-border bg-surface p-5 shadow-card', className]
    .filter((value): value is string => Boolean(value))
    .join(' ');

  return <div className={classes} {...rest} />;
}
