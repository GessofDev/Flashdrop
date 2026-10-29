import type { Role } from '@/session';

export interface ShellFooterProps {
  /** Role the user is working on. */
  role: Role;
}

/**
 * Institutional footer of the comensal and store surfaces.
 *
 * It carries the coverage, the legal links and the way to get the app; the
 * payment methods of the mockup are left out on purpose — the checkout of
 * the product is declarative and the app does not integrate a gateway yet.
 */
function InstitutionalFooter() {
  return (
    <footer className="border-t border-border bg-surface">
      <div className="mx-auto flex w-full max-w-app flex-col gap-4 px-4 py-6 md:flex-row md:items-center md:justify-between md:px-8">
        <div className="flex flex-wrap items-center gap-2 text-sm text-foreground-secondary">
          <span className="font-semibold text-foreground">Cobertura FlashDrop Chile:</span>
          <span className="rounded-full bg-canvas px-2.5 py-0.5 text-xs">Santiago (RM)</span>
          <span className="rounded-full bg-canvas px-2.5 py-0.5 text-xs">Valparaíso &amp; Viña</span>
          <span className="rounded-full bg-canvas px-2.5 py-0.5 text-xs">Gran Concepción</span>
        </div>
        <div className="flex flex-wrap items-center gap-4 text-sm text-foreground-secondary">
          <a className="hover:text-primary" href="#">
            Términos y Condiciones
          </a>
          <a className="hover:text-primary" href="#">
            Política de Privacidad
          </a>
          <a className="font-semibold text-primary hover:text-primary-hover" href="#">
            Descarga la App
          </a>
          <span>© 2025 FlashDrop SpA</span>
        </div>
      </div>
    </footer>
  );
}

/**
 * Status bar of the courier surface.
 *
 * It is a low band, as in the mockup, but it states no route data: the
 * active route belongs to the courier console (task T10) and the backend does
 * not send it yet.
 */
function CourierFooter() {
  return (
    <footer className="border-t border-border bg-surface">
      <div className="mx-auto flex h-14 w-full max-w-app items-center justify-between gap-4 px-4 md:px-8">
        <div className="flex items-center gap-2 text-sm">
          <span className="h-2.5 w-2.5 rounded-full bg-success" aria-hidden="true" />
          <span className="font-semibold text-foreground">Consola de reparto</span>
          <span className="hidden text-foreground-secondary sm:inline">
            Ruta activa, cola FIFO e historial
          </span>
        </div>
        <span className="text-xs text-foreground-secondary">Mesa Central: +56 2 2914 8000</span>
      </div>
    </footer>
  );
}

/**
 * Footer of the shell.
 *
 * The mockup declares three surface policies: the comensal and the store
 * surfaces carry the institutional footer and the courier surface carries
 * the status bar of the console.
 */
export function ShellFooter({ role }: ShellFooterProps) {
  if (role === 'REPARTIDOR') {
    return <CourierFooter />;
  }

  return <InstitutionalFooter />;
}
