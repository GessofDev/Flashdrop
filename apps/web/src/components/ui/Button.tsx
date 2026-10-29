import type { ButtonHTMLAttributes } from 'react';

export type ButtonVariant = 'primary' | 'secondary' | 'destructive';
export type ButtonSize = 'sm' | 'md';

const BASE_CLASSES = [
  'inline-flex items-center justify-center gap-2',
  'rounded-control font-semibold',
  'transition-colors',
  'focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2',
  'disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-45',
].join(' ');

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary: 'bg-primary text-white hover:bg-primary-hover active:bg-primary-active',
  secondary:
    'bg-canvas text-foreground border border-border hover:bg-surface-hover hover:border-border-strong',
  destructive: 'bg-destructive text-white hover:bg-destructive-hover',
};

const SIZE_CLASSES: Record<ButtonSize, string> = {
  sm: 'h-9 px-3 text-sm',
  md: 'h-11 px-4 text-sm',
};

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
}

/**
 * Primary action control.
 *
 * Every colour comes from the design tokens (`src/styles/theme.css`); the
 * component never declares a hex literal.
 */
export function Button({
  variant = 'primary',
  size = 'md',
  className,
  type = 'button',
  ...rest
}: ButtonProps) {
  const classes = [BASE_CLASSES, VARIANT_CLASSES[variant], SIZE_CLASSES[size], className]
    .filter((value): value is string => Boolean(value))
    .join(' ');

  return <button type={type} className={classes} {...rest} />;
}
