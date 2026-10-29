import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { UserProfile } from '@/api';
import { sessionStore } from '@/session';

import { jsonResponse, mockFetch } from './support/fetchMock';
import type { RecordedCall } from './support/fetchMock';
import { renderApp, signIn } from './support/renderApp';

const PROFILE_PATH = '/perfil';

const SAMPLE_PROFILE: UserProfile = {
  userId: 101,
  name: 'Carlos',
  lastName: 'Morales Valenzuela',
  email: 'carlos.morales@flashdrop.cl',
  phone: '+56987654321',
  rut: '18.239.402-1',
  photo: null,
  roles: ['CLIENTE', 'RESTAURANTE', 'REPARTIDOR'],
};

function profileResponse(call: RecordedCall, profile: UserProfile = SAMPLE_PROFILE): Response {
  const { pathname } = new URL(call.url, 'http://flashdrop.test');

  if (pathname === '/auth/profile' && call.method === 'GET') {
    return jsonResponse(profile);
  }

  if (pathname === '/auth/profile' && call.method === 'PUT') {
    const body = call.body as Record<string, unknown>;
    return jsonResponse({
      ...profile,
      name: body.name ?? profile.name,
      lastName: body.lastName ?? profile.lastName,
      phone: body.phone ?? profile.phone,
    });
  }

  return jsonResponse({ status: 404, message: 'No encontrado' }, 404);
}

describe('multi-role profile and accounts (spec S15 and S17)', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
    signIn({ activeRole: 'CLIENTE' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('renders user identity with full name, email, RUT and Cuenta Verificada badge', async () => {
    mockFetch((call) => profileResponse(call));
    renderApp(PROFILE_PATH);

    const identitySection = await screen.findByRole('region', { name: 'Identidad del usuario' });
    expect(within(identitySection).getByRole('heading', { level: 1, name: /Carlos Morales Valenzuela/i })).toBeInTheDocument();
    expect(within(identitySection).getByText('Cuenta Verificada')).toBeInTheDocument();
    expect(within(identitySection).getByText(/18\.239\.402-1/)).toBeInTheDocument();
    expect(within(identitySection).getByText('carlos.morales@flashdrop.cl')).toBeInTheDocument();
    expect(within(identitySection).getByText('+56987654321')).toBeInTheDocument();
  });

  it('renders the 3 role cards and allows switching to another role', async () => {
    mockFetch((call) => profileResponse(call));
    renderApp(PROFILE_PATH);

    await screen.findByRole('heading', { level: 1, name: /Carlos Morales Valenzuela/i });

    const clientCard = screen.getByRole('article', { name: 'Rol Cliente' });
    const storeCard = screen.getByRole('article', { name: 'Rol Mi Tienda' });
    const courierCard = screen.getByRole('article', { name: 'Rol Repartidor' });

    expect(clientCard).toBeInTheDocument();
    expect(storeCard).toBeInTheDocument();
    expect(courierCard).toBeInTheDocument();

    // Active role initially is CLIENTE
    expect(within(clientCard).getByText('Rol Actual')).toBeInTheDocument();

    // Click on store card button to activate store role
    const storeBtn = within(storeCard).getByRole('button', { name: /Entrar a Mi Tienda|Activar Perfil/i });
    await userEvent.click(storeBtn);

    expect(sessionStore.getSession()?.activeRole).toBe('RESTAURANTE');
  });

  it('ensures the courier vehicle field is strictly read-only and never editable', async () => {
    mockFetch((call) => profileResponse(call));
    renderApp(PROFILE_PATH);

    await screen.findByRole('heading', { level: 1, name: /Carlos Morales Valenzuela/i });

    const courierCard = screen.getByRole('article', { name: 'Rol Repartidor' });
    expect(within(courierCard).getByText(/Vehículo/i)).toBeInTheDocument();

    // Verify there is NO input or select named vehicle anywhere on the page
    const vehicleInput = screen.queryByLabelText(/vehículo/i);
    expect(vehicleInput).toBeNull();
    const vehicleField = document.querySelector('input[name="vehicle"], select[name="vehicle"]');
    expect(vehicleField).toBeNull();
  });

  it('allows editing personal data (name, lastName, phone) via PUT /auth/profile', async () => {
    const calls: RecordedCall[] = [];
    mockFetch((call) => {
      calls.push(call);
      return profileResponse(call);
    });

    renderApp(PROFILE_PATH);

    await screen.findByRole('heading', { level: 1, name: /Carlos Morales Valenzuela/i });

    const editBtn = screen.getByRole('button', { name: 'Editar datos' });
    await userEvent.click(editBtn);

    const nameInput = screen.getByLabelText('Nombre');
    const lastNameInput = screen.getByLabelText('Apellido');
    const phoneInput = screen.getByLabelText('Teléfono');

    expect(lastNameInput).toHaveValue('Morales Valenzuela');

    // RUT and email must not be editable
    expect(screen.queryByLabelText(/rut/i)).toBeNull();
    expect(screen.queryByLabelText(/email|correo/i)).toBeNull();

    await userEvent.clear(nameInput);
    await userEvent.type(nameInput, 'Carlos Alberto');
    await userEvent.clear(phoneInput);
    await userEvent.type(phoneInput, '+56999998888');

    const saveBtn = screen.getByRole('button', { name: 'Guardar cambios' });
    await userEvent.click(saveBtn);

    await waitFor(() => {
      const putCall = calls.find((c) => c.method === 'PUT' && c.url === '/auth/profile');
      expect(putCall).toBeDefined();
      expect(putCall?.body).toEqual({
        name: 'Carlos Alberto',
        lastName: 'Morales Valenzuela',
        phone: '+56999998888',
      });
    });

    expect(await screen.findByText('Perfil actualizado correctamente.')).toBeInTheDocument();
  });

  it('renders error notice when updating profile fails with backend error', async () => {
    mockFetch((call) => {
      if (call.method === 'PUT' && call.url === '/auth/profile') {
        return jsonResponse({ status: 400, message: 'El teléfono debe tener formato válido' }, 400);
      }
      return profileResponse(call);
    });

    renderApp(PROFILE_PATH);

    await screen.findByRole('heading', { level: 1, name: /Carlos Morales Valenzuela/i });

    await userEvent.click(screen.getByRole('button', { name: 'Editar datos' }));
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('El teléfono debe tener formato válido')).toBeInTheDocument();
  });

  it('renders connected devices section with the current active session', async () => {
    mockFetch((call) => profileResponse(call));
    renderApp(PROFILE_PATH);

    await screen.findByRole('heading', { level: 1, name: /Carlos Morales Valenzuela/i });

    const devicesSection = screen.getByRole('region', { name: 'Dispositivos conectados' });
    expect(within(devicesSection).getByText(/Navegador Web/i)).toBeInTheDocument();
    expect(within(devicesSection).getByText(/Este equipo/i)).toBeInTheDocument();
    expect(within(devicesSection).getByText('Activo ahora')).toBeInTheDocument();
  });

  it('never renders unsupported features per S15 and S17', async () => {
    mockFetch((call) => profileResponse(call));
    renderApp(PROFILE_PATH);

    await screen.findByRole('heading', { level: 1, name: /Carlos Morales Valenzuela/i });

    const mainText = screen.getByRole('main').textContent ?? '';

    expect(mainText).not.toMatch(/boleta\s*electr[oó]nica|\bsii\b/i);
    expect(mainText).not.toMatch(/\bpos\b|impresora\s*t[eé]rmica/i);
    expect(mainText).not.toMatch(/\bip\b|\bdirecci[oó]n\s*ip\b/i);
    expect(mainText).not.toMatch(/telemetr[ií]a|\bgps\b|km\/h|velocidad/i);
    expect(mainText).not.toMatch(/calificaci[oó]n|rating|★/i);
    expect(mainText).not.toMatch(/flashpoints/i);
    expect(mainText).not.toMatch(/tarjeta.*webpay|webpay.*guardad/i);
  });

  it('renders loading skeleton while user profile is being fetched', async () => {
    mockFetch(() => new Promise<Response>(() => undefined));
    renderApp(PROFILE_PATH);

    expect(await screen.findByRole('status', { name: 'Cargando perfil' })).toBeInTheDocument();
  });
});
