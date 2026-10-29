import { useState } from 'react';
import type { FormEvent } from 'react';

import type { RegisterRequest } from '@/api';
import { Button } from '@/components/ui';

import { formatRut } from './rut';

export interface RegisterFormProps {
  /** Called with the data of the new account. */
  onSubmit: (account: RegisterRequest) => void;
  /** Whether a call is in flight. */
  submitting: boolean;
}

const INPUT_CLASSES =
  'h-11 w-full rounded-control border border-border bg-surface px-3 text-sm text-foreground placeholder:text-foreground-muted';

/**
 * Account creation of the unified portal.
 *
 * It calls the endpoint that really exists (`POST /auth/register`); the
 * account is created with the role `Cliente` and the roles are assigned by
 * the backend, never by the form.
 */
export function RegisterForm({ onSubmit, submitting }: RegisterFormProps) {
  const [email, setEmail] = useState('');
  const [rut, setRut] = useState('');
  const [name, setName] = useState('');
  const [lastName, setLastName] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');

  const handleSubmit = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();

    onSubmit({
      email: email.trim(),
      password,
      rut: rut.trim().length > 0 ? rut.trim() : null,
      name: name.trim().length > 0 ? name.trim() : null,
      lastName: lastName.trim().length > 0 ? lastName.trim() : null,
      phone: phone.trim().length > 0 ? phone.trim() : null,
    });
  };

  return (
    <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-semibold text-foreground" htmlFor="register-email">
          Correo electrónico
        </label>
        <input
          id="register-email"
          name="email"
          type="email"
          autoComplete="email"
          placeholder="nombre@ejemplo.cl"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          className={INPUT_CLASSES}
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-semibold text-foreground" htmlFor="register-rut">
          RUT
        </label>
        <input
          id="register-rut"
          name="rut"
          autoComplete="off"
          placeholder="16.482.915-K"
          value={rut}
          onChange={(event) => setRut(formatRut(event.target.value))}
          className={INPUT_CLASSES}
        />
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-semibold text-foreground" htmlFor="register-name">
            Nombre
          </label>
          <input
            id="register-name"
            name="name"
            autoComplete="given-name"
            value={name}
            onChange={(event) => setName(event.target.value)}
            className={INPUT_CLASSES}
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-semibold text-foreground" htmlFor="register-last-name">
            Apellido
          </label>
          <input
            id="register-last-name"
            name="lastName"
            autoComplete="family-name"
            value={lastName}
            onChange={(event) => setLastName(event.target.value)}
            className={INPUT_CLASSES}
          />
        </div>
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-semibold text-foreground" htmlFor="register-phone">
          Teléfono
        </label>
        <input
          id="register-phone"
          name="phone"
          type="tel"
          autoComplete="tel"
          placeholder="+56911112222"
          value={phone}
          onChange={(event) => setPhone(event.target.value)}
          className={INPUT_CLASSES}
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-semibold text-foreground" htmlFor="register-password">
          Contraseña
        </label>
        <input
          id="register-password"
          name="password"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          className={INPUT_CLASSES}
        />
        <p className="text-xs text-foreground-secondary">
          Mínimo 10 caracteres, con mayúscula, minúscula y número.
        </p>
      </div>

      <Button type="submit" disabled={submitting} className="w-full">
        {submitting ? 'Creando cuenta...' : 'Crear Cuenta en FlashDrop'}
      </Button>
    </form>
  );
}
