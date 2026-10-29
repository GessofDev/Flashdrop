import { ORDER_STEPS, completedStepsForStatus } from '@/lib/orderStatus';

export interface OrderStepperProps {
  /** Status of the order, as the backend labels it. */
  status: string;
}

/**
 * The five steps of the tracking of an order (spec S6).
 *
 * The steps are the states the customer follows: `Confirmado`, `En cocina`,
 * `Listo para retiro`, `Retirado` and `Entregado`. There is **no** `En camino`
 * step: that status of the order is legacy and no role can set it, so the
 * phase of the transit is shown apart, from the status of the route.
 *
 * The stepper reads the status the backend sent and never sets one: the
 * customer cannot move the order forward.
 */
export function OrderStepper({ status }: OrderStepperProps) {
  const completed = completedStepsForStatus(status);

  return (
    <ol
      aria-label="Estado del pedido"
      className="grid grid-cols-2 gap-4 md:grid-cols-5"
    >
      {ORDER_STEPS.map((step, index) => {
        const done = index < completed;
        const current = index === completed;

        return (
          <li
            key={step}
            aria-current={current ? 'step' : undefined}
            className="flex items-center gap-2"
          >
            <span
              aria-hidden="true"
              className={
                done
                  ? 'flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-success-surface text-xs font-bold text-success'
                  : current
                    ? 'flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary text-xs font-bold text-white'
                    : 'flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-surface-hover text-xs font-bold text-foreground-muted'
              }
            >
              {index + 1}
            </span>
            <span className="flex min-w-0 flex-col">
              <span
                className={
                  done
                    ? 'truncate text-sm font-bold text-foreground'
                    : current
                      ? 'truncate text-sm font-bold text-primary'
                      : 'truncate text-sm font-semibold text-foreground-muted'
                }
              >
                {step}
              </span>
              <span className="text-xs text-foreground-muted">
                {done ? 'Completado' : 'Pendiente'}
              </span>
            </span>
          </li>
        );
      })}
    </ol>
  );
}
