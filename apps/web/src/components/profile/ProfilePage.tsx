import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { asApiError, authApi } from '@/api';
import type { ApiError, UserProfile } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { Badge, Button, Skeleton } from '@/components/ui';
import { MISSING_VALUE } from '@/lib/format';
import { routePaths } from '@/app/routePaths';
import { useSession } from '@/session';
import type { Role } from '@/session';

export function ProfilePage() {
  const navigate = useNavigate();
  const { activeRole, setActiveRole, logout, tokens } = useSession();

  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  const [isEditing, setIsEditing] = useState(false);
  const [editName, setEditName] = useState('');
  const [editLastName, setEditLastName] = useState('');
  const [editPhone, setEditPhone] = useState('');
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    authApi
      .profile()
      .then((data) => {
        if (!active) return;
        setProfile(data);
        setEditName(data.name);
        setEditLastName(data.lastName);
        setEditPhone(data.phone ?? '');
        setLoading(false);
      })
      .catch((cause: unknown) => {
        if (!active) return;
        setError(asApiError(cause));
        setLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);

  const handleStartEdit = () => {
    if (!profile) return;
    setEditName(profile.name);
    setEditLastName(profile.lastName);
    setEditPhone(profile.phone ?? '');
    setSaveError(null);
    setNotice(null);
    setIsEditing(true);
  };

  const handleSaveEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setSaveError(null);
    setNotice(null);

    try {
      const updated = await authApi.updateProfile({
        name: editName.trim(),
        lastName: editLastName.trim(),
        phone: editPhone.trim().length > 0 ? editPhone.trim() : null,
      });
      setProfile(updated);
      setIsEditing(false);
      setNotice('Perfil actualizado correctamente.');
    } catch (cause: unknown) {
      setSaveError(asApiError(cause));
    } finally {
      setSaving(false);
    }
  };

  const handleSwitchRole = (role: Role, targetPath: string) => {
    setActiveRole(role);
    navigate(targetPath);
  };

  const handleLogout = async () => {
    try {
      if (tokens?.refreshToken) {
        await authApi.logout(tokens.refreshToken);
      }
    } finally {
      logout();
      navigate(routePaths.login);
    }
  };

  const roles = profile?.roles ?? [];
  const fullName = profile ? `${profile.name} ${profile.lastName}`.trim() : '';

  return (
    <div className="mx-auto w-full max-w-app px-4 py-6 md:px-8">
      <h1 className="sr-only">Perfil</h1>

      {notice !== null ? (
        <div className="mb-6 rounded-container border border-success/30 bg-success-surface p-4 text-sm font-semibold text-success">
          {notice}
        </div>
      ) : null}

      {error !== null ? (
        <div className="mb-6">
          <ApiErrorNotice error={error} />
        </div>
      ) : null}

      {loading ? (
        <Skeleton label="Cargando perfil" blocks={3} height="h-36" />
      ) : profile !== null ? (
        <div className="flex flex-col gap-8">
          {/* 1. Header Identidad del usuario */}
          <section
            role="region"
            aria-label="Identidad del usuario"
            className="flex flex-col gap-6 rounded-container border border-border bg-surface p-6 shadow-card md:flex-row md:items-center md:justify-between"
          >
            <div className="flex items-center gap-4">
              <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-primary-surface text-xl font-bold text-primary">
                {profile.name.charAt(0)}
                {profile.lastName.charAt(0)}
              </div>
              <div className="flex flex-col gap-1">
                <div className="flex flex-wrap items-center gap-2">
                  <h1 className="font-display text-2xl font-bold text-foreground">{fullName}</h1>
                  <Badge variant="success">Cuenta Verificada</Badge>
                </div>
                <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-foreground-secondary">
                  <span>
                    RUT: <strong className="font-medium text-foreground">{profile.rut ?? MISSING_VALUE}</strong>
                  </span>
                  <span>{profile.email}</span>
                  <span>{profile.phone ?? MISSING_VALUE}</span>
                </div>
              </div>
            </div>

            <div>
              <Button variant="secondary" onClick={handleStartEdit}>
                Editar datos
              </Button>
            </div>
          </section>

          {/* Formulario de edición si isEditing está abierto */}
          {isEditing ? (
            <section
              aria-label="Edición de perfil"
              className="rounded-container border border-border bg-surface p-6 shadow-card"
            >
              <h2 className="mb-4 font-display text-lg font-bold text-foreground">
                Editar datos personales
              </h2>

              {saveError !== null ? (
                <div className="mb-4">
                  <ApiErrorNotice error={saveError} />
                </div>
              ) : null}

              <form onSubmit={handleSaveEdit} className="grid max-w-xl gap-4">
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="profile-name" className="text-sm font-semibold text-foreground">
                    Nombre
                  </label>
                  <input
                    id="profile-name"
                    type="text"
                    required
                    value={editName}
                    onChange={(e) => setEditName(e.target.value)}
                    className="h-10 rounded-control border border-border bg-canvas px-3 text-sm text-foreground focus-visible:outline-2 focus-visible:outline-primary"
                  />
                </div>

                <div className="flex flex-col gap-1.5">
                  <label htmlFor="profile-lastName" className="text-sm font-semibold text-foreground">
                    Apellido
                  </label>
                  <input
                    id="profile-lastName"
                    type="text"
                    required
                    value={editLastName}
                    onChange={(e) => setEditLastName(e.target.value)}
                    className="h-10 rounded-control border border-border bg-canvas px-3 text-sm text-foreground focus-visible:outline-2 focus-visible:outline-primary"
                  />
                </div>

                <div className="flex flex-col gap-1.5">
                  <label htmlFor="profile-phone" className="text-sm font-semibold text-foreground">
                    Teléfono
                  </label>
                  <input
                    id="profile-phone"
                    type="tel"
                    value={editPhone}
                    onChange={(e) => setEditPhone(e.target.value)}
                    className="h-10 rounded-control border border-border bg-canvas px-3 text-sm text-foreground focus-visible:outline-2 focus-visible:outline-primary"
                  />
                </div>

                <div className="flex items-center gap-3 pt-2">
                  <Button type="submit" disabled={saving}>
                    Guardar cambios
                  </Button>
                  <Button type="button" variant="secondary" onClick={() => setIsEditing(false)}>
                    Cancelar
                  </Button>
                </div>
              </form>
            </section>
          ) : null}

          {/* 2. Selector y estado de Roles */}
          <section aria-label="Mis roles en FlashDrop" className="flex flex-col gap-4">
            <div>
              <h2 className="font-display text-xl font-bold text-foreground">
                Mis roles en FlashDrop
              </h2>
              <p className="text-sm text-foreground-secondary">
                Cambia de rol para acceder a las herramientas correspondientes.
              </p>
            </div>

            <div className="grid gap-5 md:grid-cols-3">
              {/* Tarjeta Cliente */}
              <article
                aria-label="Rol Cliente"
                className={`flex flex-col justify-between rounded-container border bg-surface p-5 shadow-card transition-all ${
                  activeRole === 'CLIENTE' ? 'border-primary ring-2 ring-primary/20' : 'border-border'
                }`}
              >
                <div className="flex flex-col gap-3">
                  <div className="flex items-center justify-between">
                    <span className="font-display text-lg font-bold text-foreground">Cliente</span>
                    {activeRole === 'CLIENTE' ? (
                      <Badge variant="info">Rol Actual</Badge>
                    ) : (
                      <span className="inline-flex h-6 items-center rounded-control bg-surface-hover px-2 text-xs font-semibold tracking-label text-foreground-secondary">
                        Inactivo
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-foreground-secondary">
                    Consumo personal y pedidos on-demand
                  </p>
                  <div className="rounded-control bg-canvas p-3 text-xs">
                    <p className="text-foreground-secondary">
                      Estado: <strong className="text-foreground">{roles.includes('CLIENTE') ? 'Habilitado' : 'Sin rol'}</strong>
                    </p>
                    <p className="mt-1 text-foreground-secondary">
                      Email: <span className="text-foreground">{profile.email}</span>
                    </p>
                  </div>
                </div>

                <div className="pt-4">
                  {activeRole === 'CLIENTE' ? (
                    <Button variant="secondary" className="w-full" onClick={() => navigate(routePaths.marketplace)}>
                      Ir a Marketplace
                    </Button>
                  ) : (
                    <Button className="w-full" onClick={() => handleSwitchRole('CLIENTE', routePaths.marketplace)}>
                      Activar Perfil
                    </Button>
                  )}
                </div>
              </article>

              {/* Tarjeta Mi Tienda */}
              <article
                aria-label="Rol Mi Tienda"
                className={`flex flex-col justify-between rounded-container border bg-surface p-5 shadow-card transition-all ${
                  activeRole === 'RESTAURANTE' ? 'border-primary ring-2 ring-primary/20' : 'border-border'
                }`}
              >
                <div className="flex flex-col gap-3">
                  <div className="flex items-center justify-between">
                    <span className="font-display text-lg font-bold text-foreground">Mi Tienda</span>
                    {activeRole === 'RESTAURANTE' ? (
                      <Badge variant="info">Rol Actual</Badge>
                    ) : (
                      <span className="inline-flex h-6 items-center rounded-control bg-surface-hover px-2 text-xs font-semibold tracking-label text-foreground-secondary">
                        Inactivo
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-foreground-secondary">
                    Operación de cocina, menú y analíticas
                  </p>
                  <div className="rounded-control bg-canvas p-3 text-xs">
                    <p className="text-foreground-secondary">
                      Estado: <strong className="text-foreground">{roles.includes('RESTAURANTE') ? 'Habilitado' : 'Sin rol'}</strong>
                    </p>
                    <p className="mt-1 text-foreground-secondary">
                      Módulos: <span className="text-foreground">KDS, Catálogo, Analíticas</span>
                    </p>
                  </div>
                </div>

                <div className="pt-4">
                  {activeRole === 'RESTAURANTE' ? (
                    <Button variant="secondary" className="w-full" onClick={() => navigate(routePaths.storeKds)}>
                      Entrar a Mi Tienda
                    </Button>
                  ) : (
                    <Button className="w-full" onClick={() => handleSwitchRole('RESTAURANTE', routePaths.storeKds)}>
                      Entrar a Mi Tienda
                    </Button>
                  )}
                </div>
              </article>

              {/* Tarjeta Repartidor */}
              <article
                aria-label="Rol Repartidor"
                className={`flex flex-col justify-between rounded-container border bg-surface p-5 shadow-card transition-all ${
                  activeRole === 'REPARTIDOR' ? 'border-primary ring-2 ring-primary/20' : 'border-border'
                }`}
              >
                <div className="flex flex-col gap-3">
                  <div className="flex items-center justify-between">
                    <span className="font-display text-lg font-bold text-foreground">Repartidor</span>
                    {activeRole === 'REPARTIDOR' ? (
                      <Badge variant="info">Rol Actual</Badge>
                    ) : (
                      <span className="inline-flex h-6 items-center rounded-control bg-surface-hover px-2 text-xs font-semibold tracking-label text-foreground-secondary">
                        Inactivo
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-foreground-secondary">
                    Navegación y entrega de pedidos
                  </p>
                  <div className="rounded-control bg-canvas p-3 text-xs">
                    <p className="text-foreground-secondary">
                      Estado: <strong className="text-foreground">{roles.includes('REPARTIDOR') ? 'Habilitado' : 'Sin rol'}</strong>
                    </p>
                    {/* S15 / D-5: vehicle is strictly read-only, never editable */}
                    <p className="mt-1 text-foreground-secondary">
                      Vehículo: <span className="font-medium text-foreground">Asignado en operaciones (No editable)</span>
                    </p>
                  </div>
                </div>

                <div className="pt-4">
                  {activeRole === 'REPARTIDOR' ? (
                    <Button variant="secondary" className="w-full" onClick={() => navigate(routePaths.courier)}>
                      Ir a Repartidor
                    </Button>
                  ) : (
                    <Button className="w-full" onClick={() => handleSwitchRole('REPARTIDOR', routePaths.courier)}>
                      Activar Perfil
                    </Button>
                  )}
                </div>
              </article>
            </div>
          </section>

          {/* 3. Dispositivos Conectados */}
          <section
            role="region"
            aria-label="Dispositivos conectados"
            className="flex flex-col gap-4 rounded-container border border-border bg-surface p-6 shadow-card"
          >
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
              <div>
                <h2 className="font-display text-lg font-bold text-foreground">
                  Dispositivos conectados
                </h2>
                <p className="text-xs text-foreground-secondary">
                  Equipos con acceso activo a tu cuenta de FlashDrop.
                </p>
              </div>
              <Button variant="secondary" onClick={handleLogout}>
                Cerrar sesión
              </Button>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <div className="flex items-center justify-between rounded-control border border-border bg-canvas p-4">
                <div className="flex flex-col">
                  <span className="font-medium text-sm text-foreground">Navegador Web (Este equipo)</span>
                  <span className="text-xs text-foreground-secondary">Sesión activa en este navegador</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="h-2 w-2 rounded-full bg-success"></span>
                  <span className="text-xs font-semibold text-success">Activo ahora</span>
                </div>
              </div>
            </div>
          </section>
        </div>
      ) : null}
    </div>
  );
}
