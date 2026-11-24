import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { UserProfile } from '@/api';
import { sessionStore } from '@/session';

import { jsonResponse, mockFetch, noContentResponse } from './support/fetchMock';
import type { RecordedCall } from './support/fetchMock';
import { renderApp, signIn } from './support/renderApp';

const PROFILE_PATH = '/perfil';
const PHOTO_PATH = '/auth/profile/photo';

const SAMPLE_PROFILE: UserProfile = {
  userId: 101,
  name: 'Carlos',
  lastName: 'Morales Valenzuela',
  email: 'carlos.morales@flashdrop.cl',
  phone: '+56987654321',
  rut: '18.239.402-1',
  photo: '/auth/profile/photo?v=1',
  roles: ['CLIENTE', 'RESTAURANTE', 'REPARTIDOR'],
};

/** Bytes of a picture, as the backend streams them. */
const PHOTO_BYTES = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10]);

/** The photo of the profile, as the backend streams it. */
function photoBytesResponse(): Response {
  return new Response(PHOTO_BYTES, {
    status: 200,
    headers: { 'Content-Type': 'image/jpeg' },
  });
}

/** Answer of the backend for the calls of the profile screen. */
function profileResponse(call: RecordedCall): Response {
  const { pathname } = new URL(call.url, 'http://flashdrop.test');

  if (pathname === '/auth/profile' && call.method === 'GET') {
    return jsonResponse(SAMPLE_PROFILE);
  }

  if (pathname === PHOTO_PATH && call.method === 'GET') {
    return photoBytesResponse();
  }

  if (pathname === PHOTO_PATH && call.method === 'POST') {
    return jsonResponse({ ...SAMPLE_PROFILE, photo: '/auth/profile/photo?v=2' });
  }

  if (pathname === PHOTO_PATH && call.method === 'DELETE') {
    return noContentResponse();
  }

  return jsonResponse({ status: 404, message: 'No encontrado' }, 404);
}

/**
 * jsdom does not implement the object URLs of the browser, so the tests stub
 * them: the component asks the browser for one per picture and releases it
 * when the picture is replaced.
 */
function stubObjectUrls(): {
  createObjectURL: ReturnType<typeof vi.fn>;
  revokeObjectURL: ReturnType<typeof vi.fn>;
} {
  let counter = 0;

  const createObjectURL = vi.fn(() => {
    counter += 1;

    return `blob:flashdrop/${counter}`;
  });
  const revokeObjectURL = vi.fn();

  Object.defineProperty(URL, 'createObjectURL', {
    configurable: true,
    writable: true,
    value: createObjectURL,
  });
  Object.defineProperty(URL, 'revokeObjectURL', {
    configurable: true,
    writable: true,
    value: revokeObjectURL,
  });

  return { createObjectURL, revokeObjectURL };
}

describe('photo of the profile (spec S9)', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
    signIn({ activeRole: 'CLIENTE' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();

    Reflect.deleteProperty(URL, 'createObjectURL');
    Reflect.deleteProperty(URL, 'revokeObjectURL');
  });

  it('asks the backend for the photo with the session and shows it', async () => {
    stubObjectUrls();

    const calls: RecordedCall[] = [];

    mockFetch((call) => {
      calls.push(call);

      return profileResponse(call);
    });

    renderApp(PROFILE_PATH);

    const avatar = await screen.findByRole('img', { name: 'Foto de perfil' });

    expect(avatar).toHaveAttribute('src', 'blob:flashdrop/1');

    const photoCall = calls.find((call) => call.url === PHOTO_PATH && call.method === 'GET');

    expect(photoCall).toBeDefined();
    expect(photoCall?.headers.authorization).toBe('Bearer access-1');
  });

  it('uploads the photo as multipart and shows the new picture', async () => {
    stubObjectUrls();

    const calls: RecordedCall[] = [];

    mockFetch((call) => {
      calls.push(call);

      return profileResponse(call);
    });

    renderApp(PROFILE_PATH);

    const avatar = await screen.findByRole('img', { name: 'Foto de perfil' });
    const firstSource = avatar.getAttribute('src');

    const file = new File([PHOTO_BYTES], 'foto.jpg', { type: 'image/jpeg' });

    await userEvent.upload(screen.getByLabelText('Archivo de la foto'), file);
    await userEvent.click(screen.getByRole('button', { name: /subir foto|reemplazar foto/i }));

    await waitFor(() => {
      const upload = calls.find((call) => call.url === PHOTO_PATH && call.method === 'POST');

      expect(upload).toBeDefined();
      expect(upload?.body).toBeInstanceOf(FormData);
      expect((upload?.body as FormData).get('file')).toBe(file);
    });

    await waitFor(() => {
      const current = screen.getByRole('img', { name: 'Foto de perfil' }).getAttribute('src');

      expect(current).not.toBe(firstSource);
      expect(current).toBe('blob:flashdrop/2');
    });
  });

  it('shows the literal message of the backend when the upload fails', async () => {
    stubObjectUrls();

    mockFetch((call) => {
      if (call.url === PHOTO_PATH && call.method === 'POST') {
        return jsonResponse(
          {
            code: 'INVALID_PROFILE_PHOTO',
            service: 'auth-service',
            trace_id: '9f1c',
            message: 'La foto debe ser una imagen válida',
          },
          400,
        );
      }

      return profileResponse(call);
    });

    renderApp(PROFILE_PATH);

    await screen.findByRole('img', { name: 'Foto de perfil' });

    const file = new File([PHOTO_BYTES], 'foto.jpg', { type: 'image/jpeg' });

    await userEvent.upload(screen.getByLabelText('Archivo de la foto'), file);
    await userEvent.click(screen.getByRole('button', { name: /subir foto|reemplazar foto/i }));

    expect(await screen.findByText('La foto debe ser una imagen válida')).toBeInTheDocument();
  });

  it('deletes the photo and takes the picture away', async () => {
    stubObjectUrls();

    const calls: RecordedCall[] = [];

    mockFetch((call) => {
      calls.push(call);

      return profileResponse(call);
    });

    renderApp(PROFILE_PATH);

    await screen.findByRole('img', { name: 'Foto de perfil' });

    await userEvent.click(screen.getByRole('button', { name: 'Quitar foto' }));

    await waitFor(() => {
      const deletion = calls.find((call) => call.url === PHOTO_PATH && call.method === 'DELETE');

      expect(deletion).toBeDefined();
    });

    await waitFor(() => {
      expect(screen.queryByRole('img', { name: 'Foto de perfil' })).toBeNull();
    });
  });

  it('shows no picture and no error when the profile has no photo', async () => {
    stubObjectUrls();

    mockFetch((call) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === PHOTO_PATH && call.method === 'GET') {
        return jsonResponse({ status: 404, message: 'No encontrado' }, 404);
      }

      return profileResponse(call);
    });

    renderApp(PROFILE_PATH);

    await screen.findByRole('heading', { level: 1, name: /Carlos Morales Valenzuela/i });

    await waitFor(() => {
      expect(screen.queryByRole('img', { name: 'Foto de perfil' })).toBeNull();
    });

    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('releases the object URL of the picture when it is replaced and when the block goes away', async () => {
    const { revokeObjectURL } = stubObjectUrls();

    mockFetch((call) => profileResponse(call));

    const { unmount } = renderApp(PROFILE_PATH);

    const avatar = await screen.findByRole('img', { name: 'Foto de perfil' });
    const firstSource = avatar.getAttribute('src');

    const file = new File([PHOTO_BYTES], 'foto.jpg', { type: 'image/jpeg' });

    await userEvent.upload(screen.getByLabelText('Archivo de la foto'), file);
    await userEvent.click(screen.getByRole('button', { name: /subir foto|reemplazar foto/i }));

    await waitFor(() => {
      expect(revokeObjectURL).toHaveBeenCalledWith(firstSource);
    });

    const secondSource = screen.getByRole('img', { name: 'Foto de perfil' }).getAttribute('src');

    unmount();

    expect(revokeObjectURL).toHaveBeenCalledWith(secondSource);
  });
});
