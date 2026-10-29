import { readdirSync, readFileSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Absolute path of the `src/` folder.
 *
 * `import.meta.url` is resolved with `node:path` on purpose: inside the jsdom
 * environment the global `URL` resolves relative paths against the document
 * base URL (`http://localhost:3000/`), which is not a `file:` URL.
 */
export const SRC_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');

export interface SourceFile {
  /** Path relative to `src/`, always with forward slashes. */
  path: string;
  content: string;
}

const SOURCE_EXTENSIONS = ['.ts', '.tsx', '.css'];

/**
 * Collect every source file below `src/<dirRelativeToSrc>`.
 *
 * Used by the design-system tests to prove that brand hex literals only live
 * in the single token file and never inside a component.
 */
export function listSourceFiles(dirRelativeToSrc: string): SourceFile[] {
  const root = join(SRC_ROOT, dirRelativeToSrc);
  const found: SourceFile[] = [];

  const walk = (directory: string): void => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const absolute = join(directory, entry.name);
      if (entry.isDirectory()) {
        walk(absolute);
        continue;
      }
      if (!SOURCE_EXTENSIONS.some((extension) => entry.name.endsWith(extension))) {
        continue;
      }
      found.push({
        path: relative(SRC_ROOT, absolute).split(sep).join('/'),
        content: readFileSync(absolute, 'utf8'),
      });
    }
  };

  walk(root);
  return found.sort((left, right) => left.path.localeCompare(right.path));
}
