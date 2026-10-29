import { useNavigate } from 'react-router-dom';

import { authApi } from '@/api';
import { routePaths } from '@/app/routePaths';
import { ROLE_HOME } from '@/app/roleRoutes';
import { Badge, Button } from '@/components/ui';
import { useSession } from '@/session';
import type { Role } from '@/session';

import { FlashDropMark } from './FlashDropMark';
import { RoleSwitcher } from './RoleSwitcher';
import { ROLE_LABELS, ROLE_TELEMETRY, SHELL_CONTEXT } from './shellConfig';

export interface TopBarProps {
  /** Role the user is working on. */
  role: Role;
}

/** Initials of the user, for the avatar of the identity zone. */
function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter((part) => part.length > 0);

  if (parts.length === 0) {
    return '—';
  }

  const first = parts[0] ?? '';
  const last = parts.length > 1 ? (parts[parts.length - 1] ?? first) : first;

  return `${first.charAt(0)}${last.charAt(0)}`.toUpperCase();
}

/**
 * Top bar of the canonical shell: the five zones of the mockup
 * `flashdrop_shell_can_nica_gu_a_de_navegaci_n_por_rol`.
 *
 * 1. Brand core — the mark, the name and the country slug.
 * 2. Role switcher — the three segments of the product.
 * 3. Context — the local and the provider of the active surface.
 * 4. Telemetry — the state of the line.
 * 5. Identity — the avatar, the name, the role and the way out.
 */
export function TopBar({ role }: TopBarProps) {
  const { session, tokens, setActiveRole, logout } = useSession();
  const navigate = useNavigate();
  const context = SHELL_CONTEXT[role];

  const handleSwitch = (next: Role): void => {
    setActiveRole(next);
    navigate(ROLE_HOME[next]);
  };

  const handleSignOut = async (): Promise<void> => {
    if (tokens !== null) {
      try {
        await authApi.logout(tokens.refreshToken);
      } catch {
        // The session is local: a failed revocation must not trap the user.
      }
    }

    logout();
    navigate(routePaths.login);
  };

  return (
    <header className="border-b border-border bg-surface/95 backdrop-blur">
      <div className="mx-auto flex h-[68px] w-full max-w-app items-center justify-between gap-4 px-4 md:px-8">
        <div className="flex items-center gap-2">
          <span className="flex h-9 w-9 items-center justify-center rounded-control bg-primary text-white shadow-card">
            <FlashDropMark className="h-5 w-5" />
          </span>
          <span className="flex flex-col leading-none">
            <span className="font-display text-sm font-bold text-foreground">
              Flash<span className="text-primary">Drop</span>
            </span>
            <span className="label-code text-[10px] font-bold text-foreground-muted">CHILE OPS</span>
          </span>
        </div>

        <RoleSwitcher activeRole={role} onSelect={handleSwitch} />

        <div className="flex items-center gap-4">
          <div className="hidden items-center gap-2 lg:flex">
            <span className="flex items-center gap-2 rounded-control bg-surface-hover px-3 py-1.5 text-xs">
              <span className="label-code text-foreground-muted">Local</span>
              <span className="font-semibold text-foreground">{context.local}</span>
            </span>
            <span className="flex items-center gap-2 rounded-control bg-surface-hover px-3 py-1.5 text-xs">
              <span className="label-code text-foreground-muted">Proveedor</span>
              <span className="font-semibold text-foreground">{context.provider}</span>
            </span>
          </div>

          <div className="hidden items-center gap-2 rounded-full bg-surface-hover px-3 py-1.5 text-xs font-medium text-foreground md:flex">
            <span className="h-2 w-2 rounded-full bg-success" aria-hidden="true" />
            <span>{ROLE_TELEMETRY[role]}</span>
          </div>

          <div className="flex items-center gap-3">
            <span
              className="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-xs font-bold text-white"
              aria-hidden="true"
            >
              {initials(session?.name ?? '')}
            </span>
            <span className="hidden flex-col items-start leading-none lg:flex">
              <span className="text-xs font-semibold text-foreground">{session?.name}</span>
              <Badge variant="info" className="mt-1">
                {ROLE_LABELS[role]}
              </Badge>
            </span>
            <Button variant="secondary" size="sm" onClick={handleSignOut}>
              Salir
            </Button>
          </div>
        </div>
      </div>
    </header>
  );
}
