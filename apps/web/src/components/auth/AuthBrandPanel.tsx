import { FlashDropMark } from '@/components/shell/FlashDropMark';

/**
 * Brand panel of the unified portal.
 *
 * It carries the value proposition and the trust data of the mockup
 * `iniciar_sesi_n_portal_unificado_flashdrop`. It states no score of the
 * service and no delivery estimate of a restaurant: the product does not
 * expose them.
 */
export function AuthBrandPanel() {
  return (
    <div className="flex h-full flex-col justify-between gap-8">
      <div className="flex flex-col gap-6">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="flex h-7 w-7 items-center justify-center rounded-control bg-primary text-white">
              <FlashDropMark className="h-4 w-4" />
            </span>
            <span className="font-display text-lg font-bold text-white">FlashDrop</span>
          </div>
          <span className="label-code rounded-full bg-white/10 px-2.5 py-1 text-[11px] text-white">
            v2.8 CL
          </span>
        </div>

        <div className="flex flex-col gap-2">
          <span className="label-code text-xs font-bold text-primary-surface">Ecosistema Integrado</span>
          <h2 className="font-display text-2xl font-bold leading-tight text-white">
            Una sola cuenta para pedir, cocinar o repartir.
          </h2>
          <p className="text-sm text-white/80">
            Conectamos el sabor auténtico de los mejores restorantes de Chile con la flota de entrega
            más veloz y coordinada.
          </p>
        </div>

        <div className="rounded-container bg-white/10 p-4">
          <div className="flex items-center justify-between text-xs text-white/80">
            <span>Tiempo promedio de entrega Santiago</span>
            <span className="nums-tabular font-bold text-primary">24 min</span>
          </div>
          <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-white/20">
            <div className="h-full w-[82%] rounded-full bg-primary" />
          </div>
          <div className="mt-2 flex items-center justify-between text-xs text-white/70">
            <span>98.4% entregas a tiempo</span>
            <span>3 Regiones Activas</span>
          </div>
        </div>
      </div>

      <div className="flex items-center justify-between text-xs text-white/70">
        <span>Tasa de despacho líder en Chile</span>
        <span className="label-code">Cifrado TLS 1.3</span>
      </div>
    </div>
  );
}
