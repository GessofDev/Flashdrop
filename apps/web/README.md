# FlashDrop Web

Front-end of FlashDrop for the three roles of the product: **Cliente**, **Mi Tienda**
and **Repartidor**.

This project is an autonomous pnpm project (like `gateway/`): it has its own
`package.json` and `pnpm-lock.yaml` and does not belong to a root pnpm workspace.

## Stack

| Concern | Choice |
|---|---|
| Build | Vite 8 |
| UI | React 19 |
| Language | TypeScript 6, `strict: true`, no `any` outside typed JSON boundaries |
| Styles | Tailwind CSS v4 through `@tailwindcss/vite` (no CDN, no Tailwind v3) |
| Routing | React Router 7 |
| Tests | Vitest 5 + Testing Library + jsdom |
| Lint | ESLint 10 flat config |

## Commands

```bash
pnpm install
pnpm dev          # dev server on http://localhost:5173
pnpm build        # tsc --noEmit && vite build
pnpm test         # vitest run
pnpm typecheck    # tsc --noEmit
pnpm lint         # eslint .
```

The dev server proxies `/api`, `/auth` and `/catalog` to the API gateway
(`http://localhost:3000`), the only public entry point of the backend.

## API client

`src/api/` holds the typed transport. Every call declares how the answer is
shaped, because the services do not share one envelope:

| Service | Success body | `responseShape` |
|---|---|---|
| `orders-service`, `delivery-service` | `{ success, message, data }` | `'envelope'` |
| `auth-service`, `catalog-service` | the bare DTO | `'bare'` |

```ts
const orders = await request<OrderListResponse[]>({
  path: '/api/orders',
  responseShape: 'envelope',
});
```

`session` decides how a call treats the session:

| `session` | `Authorization` header | 401 |
|---|---|---|
| `'authenticated'` (default) | attached when there is a token | renews the token once, retries the call, and clears the session when the renewal fails |
| `'public'` | never attached | raised as it comes |

The base URL is relative by default, so the browser calls the paths the dev
server proxies. `VITE_API_BASE_URL` overrides it (no value is committed).

### Errors

`ApiError` carries the HTTP `status`, the literal `message` of the backend
and a `kind` discriminant (`unauthorized`, `forbidden`, `notFound`,
`conflict`, `badRequest`, `clientError`, `serverError`, `timeout`, `network`,
`aborted`, `unknown`). The message is never translated nor replaced.

The three error bodies of the backend are read through the same reader: the
`message` field is the only one they all share.

```text
orders-service    { status, error, message }
catalog-service   { status, error, message }
delivery-service  { timestamp, status, error, message }
auth-service      { code, service, trace_id, message }
gateway           { statusCode, error, message, timestamp }
```

`resolveApiErrorOutcome` turns an error into the decision of the UI (spec
S7): a 401 sends the user to `/login`, a 403 shows the view of the wrong
role, and a 409 shows the message of the backend with the retry.


## Design system (single source of truth)

All tokens live in [`src/styles/theme.css`](src/styles/theme.css) inside a
Tailwind v4 `@theme` block, so each token becomes a utility class
(`bg-primary`, `rounded-control`, `font-display`, `shadow-card`, ...). No
component ever writes a hex literal — a test enforces that.

`src/styles/index.css` narrows the Tailwind scan with `@source not` for
`dist/`, the README and the pnpm lockfiles, so the generated stylesheet stays
reproducible and never depends on build output or documentation.

| Group | Tokens |
|---|---|
| Brand | `primary` `#E84A0E`, `primary-hover` `#FF5A1F`, `primary-surface` `#FFF4ED` |
| Neutrals | `foreground` `#0F172A`, `foreground-secondary` `#334155`, `foreground-muted` `#64748B`, `border` `#E2E8F0`, `canvas` `#F8FAFC`, `surface` `#FFFFFF` |
| Semantic | `success` `#15803D`/`#DCFCE7`, `info` `#1D4ED8`/`#DBEAFE`, `warning` `#B45309`/`#FEF3C7`, `destructive` `#B91C1C`/`#FEE2E2` |
| Shape | `radius-control` 8px, `radius-container` 16px, `radius-overlay` 24px |
| Typography | `font-display` Plus Jakarta Sans (headers), `font-sans` Inter (data) |

Beyond the minimum token set, the file also declares the hover/active states
and the resting elevation described by
`references/stitch_flashdrop_ui_design_specs/kinetic_delivery_system/DESIGN.md`
(`primary-active`, `destructive-hover`, `surface-hover`, `border-strong`,
`shadow-card`, `tracking-label`, `container-app`).

Two utilities come from the same file:

- `nums-tabular` → `font-variant-numeric: tabular-nums`, for CLP amounts and
  `#FD-XXXX` codes.
- `label-code` → uppercase with `letter-spacing: 0.05em`.

Focus is always visible: `outline: 2px solid var(--color-primary)` with
`outline-offset: 2px`.

## Routes

| Path | Role | Screen | Origin mockup |
|---|---|---|---|
| `/` | Cliente | Marketplace | `marketplace_y_cat_logo_de_clientes_flashdrop_v2.0` |
| `/pedidos` | Cliente | Mis pedidos | `seguimiento_de_pedido_en_vivo_fd_0042_flashdrop_v2.0` |
| `/pedidos/:id` | Cliente | Seguimiento del pedido | `seguimiento_de_pedido_en_vivo_fd_0042_flashdrop_v2.0` |
| `/tienda/kds` | Mi Tienda | Monitor de cocina | `monitor_de_cocina_y_gestor_de_pedidos_mi_tienda` |
| `/tienda/catalogo` | Mi Tienda | Catálogo y menú | `gesti_n_de_cat_logo_y_men_mi_tienda` |
| `/tienda/despachos` | Mi Tienda | Historial de despachos | `anal_ticas_de_ventas_y_rendimiento_mi_tienda_v2.0` |
| `/tienda/analiticas` | Mi Tienda | Analíticas de ventas | `anal_ticas_de_ventas_y_rendimiento_mi_tienda_v2.0` |
| `/repartidor` | Repartidor | Ruta activa | `consola_del_repartidor_flashdrop_courier` |
| `/repartidor/pedidos` | Repartidor | Pedidos disponibles (FIFO) | `consola_del_repartidor_flashdrop_courier` |
| `/repartidor/historial` | Repartidor | Historial de entregas | `consola_del_repartidor_flashdrop_courier` |
| `/perfil` | Todos | Perfil multi-rol | `perfil_multi_rol_y_centro_de_cuentas_flashdrop_v2.0_1` |
| `/login` | Todos | Inicio de sesión | `iniciar_sesi_n_portal_unificado_flashdrop` |

Every route but `/login` renders a placeholder that states its origin mockup.
The marketplace (T5) and the orders of the customer (T7) already have their
screens; the other screens land in later tasks.

## Canonical shell (T3)

`src/components/shell/` holds the shell of the app, built from the mockup
`flashdrop_shell_can_nica_gu_a_de_navegaci_n_por_rol`:

| Zone | Content |
|---|---|
| 1. Brand core | the lightning mark of the brand, `FlashDrop` and the `CHILE OPS` slug |
| 2. Role switcher | the segmented control `Cliente` / `Mi Tienda` / `Repartidor` |
| 3. Context | the chips of the local and of the provider of the active surface |
| 4. Telemetry | the state of the line, with its status dot |
| 5. Identity | the avatar, the name, the badge of the role and the way out |

The shell is mounted **once**, in the layout of the routes
(`src/app/ProtectedLayout.tsx`), so no screen rebuilds it.

The switcher changes the role of the session and the route of the app. It
never refetches the session and never asks for credentials.

The guard of the layout applies two rules:

- without a session, every protected route goes to `/login`;
- with a session, a route that belongs to another role goes to the home of
  the active role (`roleRoutes.ts`), never to a not-found page.

The navigation of each role is declared once, in `src/app/roleRoutes.ts`:

| Role | Entries |
|---|---|
| Cliente | Marketplace & Consumo, Mis Pedidos |
| Mi Tienda | KDS Pantalla Cocina, Catálogo & Menú, Historial Despachos, Analíticas & Ventas |
| Repartidor | Ruta Activa & GPS, Pedidos Disponibles (FIFO), Historial de Entregas |

## Unified login (T4)

`src/components/auth/` holds the portal of access, built from the mockup
`iniciar_sesi_n_portal_unificado_flashdrop`:

- the tabs `Iniciar Sesión` and `Crear Cuenta`, in the left panel;
- the **módulo de destino preferente**: three cards that choose where the
  user lands after signing in. It does **not** grant a role — the backend
  assigns the roles of the account (`PUT /api/internal/users/{userId}/roles`);
- the identifier of the account: the Chilean RUT (with or without dots and
  hyphen) or the email;
- the right panel with the value proposition and the trust data.

`ClaveÚnica`, `Google` and `Apple ID` are rendered as disabled buttons with
the explanation that the backend does not integrate federated identity: they
never fire a request.

The errors come from the client of T2 and the message shown is always the
literal message of the backend, the 401 of a rejected sign-in included.

### "Recordar mi sesión"

The checkbox does **not** persist the access token outside `sessionStorage`:
the token pair lives in `sessionStorage` and dies with the tab. What the
checkbox decides is narrower — whether the app restores the session when the
page is reloaded **inside the same tab**. The preference itself is stored in
`localStorage` (`flashdrop.rememberSession.v1`), because it is a preference
and not a credential.


## Source layout

```text
src/
├── api/            # typed transport: request(), ApiError, DTOs, auth calls
├── app/            # router, route paths, role routes, guard and placeholders
├── components/     # ui/ primitives, errors/ notice, shell/ and auth/ screens
├── lib/            # formatters and the readers of the order and route status
├── session/        # session store, roles, provider and session events
├── styles/         # index.css (entry) and theme.css (the only token file)
└── test/           # Vitest suites and setup
```

## Session

The session lives in `src/session/tokenStore.ts`, a plain module the API
client can read without the component tree. The React provider subscribes to
it.

The token pair is kept in `sessionStorage` — never in `localStorage`, it is an
access token — and the active role travels with the session, so a reload
lands on the same role. `sessionStore.reload()` reads the persisted state
again, the way a reload of the page does.

## Notes

- `pnpm-workspace.yaml` is generated by pnpm 11 to record the release-age
  exception for `react-router-dom@7.18.5`; it keeps installs reproducible.
- The canonical fixture and the screens are not part of this bootstrap.
