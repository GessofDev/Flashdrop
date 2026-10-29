import { describe, expect, it } from 'vitest';

import { listSourceFiles } from './support/sourceFiles';

/**
 * Brand hex values are assembled at runtime on purpose.
 *
 * The design system has a single source of truth (`src/styles/theme.css`) and
 * the guard added in task T14 fails the build when a brand hex literal shows
 * up anywhere else inside `src/`. This test file lives inside `src/`, so it
 * must not contain the literal either.
 */
const hex = (...parts: string[]): string => parts.join('');

const TOKEN_FILE = 'styles/theme.css';

function tokenFileContent(): string {
  const file = listSourceFiles('styles').find((candidate) => candidate.path === TOKEN_FILE);
  if (!file) {
    throw new Error(`Missing token file: ${TOKEN_FILE}`);
  }
  return file.content;
}

function themeTokens(): Map<string, string> {
  const css = tokenFileContent();
  const block = css.match(/@theme\s*\{([\s\S]*?)\n\}/);
  expect(block, `${TOKEN_FILE} must declare a Tailwind v4 @theme block`).not.toBeNull();

  const tokens = new Map<string, string>();
  for (const match of (block?.[1] ?? '').matchAll(/--([a-z0-9-]+)\s*:\s*([^;]+);/g)) {
    tokens.set(match[1] as string, (match[2] as string).trim());
  }
  return tokens;
}

/** Every token mandated by the design system spec (S4). */
const REQUIRED_TOKENS: ReadonlyArray<readonly [string, string]> = [
  ['color-primary', hex('#', 'E8', '4A', '0E')],
  ['color-primary-hover', hex('#', 'FF', '5A', '1F')],
  ['color-primary-surface', hex('#', 'FF', 'F4', 'ED')],
  ['color-foreground', hex('#', '0F', '17', '2A')],
  ['color-foreground-secondary', hex('#', '33', '41', '55')],
  ['color-foreground-muted', hex('#', '64', '74', '8B')],
  ['color-border', hex('#', 'E2', 'E8', 'F0')],
  ['color-canvas', hex('#', 'F8', 'FA', 'FC')],
  ['color-surface', hex('#', 'FF', 'FF', 'FF')],
  ['color-success', hex('#', '15', '80', '3D')],
  ['color-success-surface', hex('#', 'DC', 'FC', 'E7')],
  ['color-info', hex('#', '1D', '4E', 'D8')],
  ['color-info-surface', hex('#', 'DB', 'EA', 'FE')],
  ['color-warning', hex('#', 'B4', '53', '09')],
  ['color-warning-surface', hex('#', 'FE', 'F3', 'C7')],
  ['color-destructive', hex('#', 'B9', '1C', '1C')],
  ['color-destructive-surface', hex('#', 'FE', 'E2', 'E2')],
];

const HEX_LITERAL = /#[0-9a-fA-F]{3,8}\b/;

describe('design system tokens', () => {
  it('defines the primary brand token with the exact spec value', () => {
    expect(themeTokens().get('color-primary')).toBe(hex('#', 'E8', '4A', '0E'));
  });

  it('defines every token required by the design system spec', () => {
    const tokens = themeTokens();
    for (const [name, expected] of REQUIRED_TOKENS) {
      expect(tokens.get(name), `token --${name} must be declared in ${TOKEN_FILE}`).toBe(expected);
    }
  });

  it('exposes the control, container and overlay radii', () => {
    const tokens = themeTokens();
    expect(tokens.get('radius-control')).toBe('8px');
    expect(tokens.get('radius-container')).toBe('16px');
    expect(tokens.get('radius-overlay')).toBe('24px');
  });

  it('loads the display typeface for headers and the data typeface for numbers', () => {
    const tokens = themeTokens();
    expect(tokens.get('font-display')).toContain('Plus Jakarta Sans');
    expect(tokens.get('font-sans')).toContain('Inter');
  });

  it('derives the focus ring from the primary token', () => {
    const css = tokenFileContent();
    expect(css).toMatch(/outline:\s*2px solid var\(--color-primary\)/);
    expect(css).toMatch(/outline-offset:\s*2px/);
  });

  it('provides the tabular numerals and the label-code utilities', () => {
    const css = tokenFileContent();
    expect(css).toMatch(/font-variant-numeric:\s*tabular-nums/);
    expect(css).toMatch(/text-transform:\s*uppercase/);
    expect(css).toMatch(/letter-spacing:\s*0\.05em/);
  });

  it('keeps every brand hex literal inside the token file', () => {
    const offenders = listSourceFiles('')
      .filter((file) => file.path !== TOKEN_FILE)
      .filter((file) => HEX_LITERAL.test(file.content))
      .map((file) => file.path);

    expect(offenders, 'brand hex literals must only exist in the token file').toEqual([]);
  });
});
