import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { sessionStore } from '@/session';

import { CATEGORIES, PRODUCTS } from './support/catalogMock';
import { jsonResponse, mockFetch, noContentResponse } from './support/fetchMock';
import type { RecordedCall } from './support/fetchMock';
import { renderApp, signIn } from './support/renderApp';

const CATALOG_PATH = '/tienda/catalogo';
const OBJECT_KEY =
  'products/2026/10/550e8400-e29b-41d4-a716-446655440000.webp';

const OWNER_PRODUCTS = PRODUCTS.map((product) => ({
  ...product,
  restaurantId: 1,
}));

function ownerCatalogResponse(call: RecordedCall): Response {
  const { pathname } = new URL(call.url, 'http://flashdrop.test');

  if (pathname === '/api/catalog/my/products') {
    return jsonResponse(OWNER_PRODUCTS);
  }

  if (pathname === '/catalog/categories') {
    return jsonResponse(CATEGORIES);
  }

  return jsonResponse(
    { status: 404, error: 'NOT_FOUND', message: 'Recurso no encontrado' },
    404,
  );
}

async function findProductRow(name: string): Promise<HTMLElement> {
  const row = await screen.findByRole('row', { name: new RegExp(name) });

  return row;
}

describe('store catalog management', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
    signIn({ activeRole: 'RESTAURANTE' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('renders every product with the shape returned by the owner catalog endpoint', async () => {
    mockFetch(ownerCatalogResponse);
    renderApp(CATALOG_PATH);

    const table = await screen.findByRole('table', { name: 'Catálogo de productos' });
    const availableRow = await findProductRow('Doble Smash Cheddar Bacon');
    const unavailableRow = await findProductRow('Hamburguesa Clásica');

    expect(within(availableRow).getByText('ID 10')).toBeInTheDocument();
    expect(within(availableRow).getByText('Hamburguesas')).toBeInTheDocument();
    expect(within(availableRow).getByText('$7.990')).toBeInTheDocument();
    expect(
      within(availableRow).getByRole('checkbox', {
        name: 'Disponibilidad de Doble Smash Cheddar Bacon',
      }),
    ).toBeChecked();

    expect(within(unavailableRow).getByText('ID 13')).toBeInTheDocument();
    expect(
      within(unavailableRow).getByRole('checkbox', {
        name: 'Disponibilidad de Hamburguesa Clásica',
      }),
    ).not.toBeChecked();
    expect(within(unavailableRow).getByText('Agotado')).toBeInTheDocument();
    expect(within(table).getByRole('button', { name: 'Editar Doble Smash Cheddar Bacon' })).toBeInTheDocument();
    expect(within(table).getByRole('button', { name: 'Duplicar Doble Smash Cheddar Bacon' })).toBeInTheDocument();
    expect(within(table).getByRole('button', { name: 'Eliminar Doble Smash Cheddar Bacon' })).toBeInTheDocument();
  });

  it('updates availability with a full product replacement and follows the backend response', async () => {
    const fetchMock = mockFetch((call) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/api/catalog/my/products/10' && call.method === 'PUT') {
        return jsonResponse({ ...OWNER_PRODUCTS[0], available: false });
      }

      return ownerCatalogResponse(call);
    });

    renderApp(CATALOG_PATH);

    const toggle = await screen.findByRole('checkbox', {
      name: 'Disponibilidad de Doble Smash Cheddar Bacon',
    });
    await userEvent.click(toggle);

    await waitFor(() => {
      const update = fetchMock.calls.find((call) => call.method === 'PUT');

      expect(update?.body).toEqual({
        categoryId: 3,
        name: 'Doble Smash Cheddar Bacon',
        price: 7990,
        available: false,
      });
    });

    await waitFor(() => expect(toggle).not.toBeChecked());

    const toggledRow = await findProductRow('Doble Smash Cheddar Bacon');

    expect(within(toggledRow).getByText('Agotado')).toBeInTheDocument();
  });

  it('restores the real availability and shows the literal error when a toggle is rejected', async () => {
    mockFetch((call) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/api/catalog/my/products/10' && call.method === 'PUT') {
        return jsonResponse(
          {
            status: 400,
            error: 'BAD_REQUEST',
            message: 'El precio del producto no puede ser negativo',
          },
          400,
        );
      }

      return ownerCatalogResponse(call);
    });

    renderApp(CATALOG_PATH);

    const toggle = await screen.findByRole('checkbox', {
      name: 'Disponibilidad de Doble Smash Cheddar Bacon',
    });
    await userEvent.click(toggle);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'El precio del producto no puede ser negativo',
    );
    expect(toggle).toBeChecked();

    const toggledRow = await findProductRow('Doble Smash Cheddar Bacon');

    expect(within(toggledRow).getByText('Disponible')).toBeInTheDocument();
  });

  it('filters the table by category and by availability', async () => {
    mockFetch(ownerCatalogResponse);
    renderApp(CATALOG_PATH);

    await screen.findByRole('table', { name: 'Catálogo de productos' });

    await userEvent.selectOptions(
      screen.getByRole('combobox', { name: 'Filtrar por categoría' }),
      '3',
    );

    expect(screen.queryByText('Pizza Napolitana Da Luigi')).not.toBeInTheDocument();
    expect(screen.getByText('Doble Smash Cheddar Bacon')).toBeInTheDocument();

    await userEvent.selectOptions(
      screen.getByRole('combobox', { name: 'Filtrar por categoría' }),
      '',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Solo disponibles' }));

    expect(screen.getByText('Pizza Napolitana Da Luigi')).toBeInTheDocument();
    expect(screen.queryByText('Hamburguesa Clásica')).not.toBeInTheDocument();
  });

  it('uploads the image as multipart file and stores its object key in the product', async () => {
    const fetchMock = mockFetch((call) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/api/catalog/my/products/image') {
        return jsonResponse(
          { objectKey: OBJECT_KEY, url: `/catalog/images/${OBJECT_KEY}` },
          201,
        );
      }

      if (pathname === '/api/catalog/my/products' && call.method === 'POST') {
        return jsonResponse(
          {
            ...OWNER_PRODUCTS[0],
            id: 20,
            name: 'Nuevo plato',
            image: OBJECT_KEY,
          },
          201,
        );
      }

      return ownerCatalogResponse(call);
    });

    renderApp(CATALOG_PATH);

    await userEvent.click(await screen.findByRole('button', { name: 'Nuevo producto' }));
    const dialog = await screen.findByRole('dialog', { name: 'Nuevo producto' });
    const file = new File(['image-bytes'], 'plato.webp', { type: 'image/webp' });

    await userEvent.upload(within(dialog).getByLabelText('Imagen del producto'), file);
    expect(await within(dialog).findByText('Imagen lista')).toBeInTheDocument();

    const upload = fetchMock.calls.find((call) => call.url.includes('/products/image'));
    expect(upload?.body).toBeInstanceOf(FormData);
    expect((upload?.body as FormData).get('file')).toBeInstanceOf(File);
    expect((upload?.body as FormData).get('file')).toHaveProperty('name', 'plato.webp');

    await userEvent.selectOptions(within(dialog).getByLabelText('Categoría'), '3');
    await userEvent.type(within(dialog).getByLabelText('Nombre del producto'), 'Nuevo plato');
    await userEvent.type(within(dialog).getByLabelText('Precio (CLP)'), '7990');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Guardar producto' }));

    await waitFor(() => {
      const create = fetchMock.calls.find(
        (call) => call.method === 'POST' && call.url.endsWith('/api/catalog/my/products'),
      );

      expect(create?.body).toMatchObject({ image: OBJECT_KEY });
    });
  });

  it('does not send restaurantId when a product is created', async () => {
    const fetchMock = mockFetch((call) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/api/catalog/my/products' && call.method === 'POST') {
        return jsonResponse({ ...OWNER_PRODUCTS[0], id: 20, name: 'Nuevo plato' }, 201);
      }

      return ownerCatalogResponse(call);
    });

    renderApp(CATALOG_PATH);

    await userEvent.click(await screen.findByRole('button', { name: 'Nuevo producto' }));
    const dialog = await screen.findByRole('dialog', { name: 'Nuevo producto' });

    await userEvent.selectOptions(within(dialog).getByLabelText('Categoría'), '3');
    await userEvent.type(within(dialog).getByLabelText('Nombre del producto'), 'Nuevo plato');
    await userEvent.type(within(dialog).getByLabelText('Precio (CLP)'), '7990');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Guardar producto' }));

    await waitFor(() => {
      const create = fetchMock.calls.find((call) => call.method === 'POST');

      expect(create).toBeDefined();
      expect(create?.body).not.toHaveProperty('restaurantId');
    });
  });

  it('shows the backend message when the image content type does not match', async () => {
    mockFetch((call) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/api/catalog/my/products/image') {
        return jsonResponse(
          {
            status: 400,
            error: 'BAD_REQUEST',
            message: 'El Content-Type no coincide con el contenido de la imagen',
          },
          400,
        );
      }

      return ownerCatalogResponse(call);
    });

    renderApp(CATALOG_PATH);

    await userEvent.click(await screen.findByRole('button', { name: 'Nuevo producto' }));
    const dialog = await screen.findByRole('dialog', { name: 'Nuevo producto' });
    const file = new File(['not-an-image'], 'plato.png', { type: 'image/png' });

    await userEvent.upload(within(dialog).getByLabelText('Imagen del producto'), file);

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'El Content-Type no coincide con el contenido de la imagen',
    );
  });

  it('shows the literal backend message for an empty product update', async () => {
    const fetchMock = mockFetch((call) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/api/catalog/my/products/10' && call.method === 'PUT') {
        return jsonResponse(
          {
            status: 400,
            error: 'BAD_REQUEST',
            message: 'Debe enviar al menos un campo para actualizar',
          },
          400,
        );
      }

      return ownerCatalogResponse(call);
    });

    renderApp(CATALOG_PATH);

    await userEvent.click(
      await screen.findByRole('button', { name: 'Editar Doble Smash Cheddar Bacon' }),
    );
    const dialog = await screen.findByRole('dialog', { name: 'Editar producto' });

    await userEvent.selectOptions(within(dialog).getByLabelText('Categoría'), '');
    await userEvent.clear(within(dialog).getByLabelText('Nombre del producto'));
    await userEvent.clear(within(dialog).getByLabelText('Descripción'));
    await userEvent.clear(within(dialog).getByLabelText('Precio (CLP)'));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Guardar cambios' }));

    await waitFor(() => {
      const update = fetchMock.calls.find((call) => call.method === 'PUT');

      expect(update?.body).toEqual({});
    });
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Debe enviar al menos un campo para actualizar',
    );
  });

  it('replaces the whole product when the form is edited', async () => {
    const fetchMock = mockFetch((call) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/api/catalog/my/products/10' && call.method === 'PUT') {
        return jsonResponse({ ...OWNER_PRODUCTS[0], name: 'Doble Smash Editado' });
      }

      return ownerCatalogResponse(call);
    });

    renderApp(CATALOG_PATH);

    await userEvent.click(
      await screen.findByRole('button', { name: 'Editar Doble Smash Cheddar Bacon' }),
    );
    const dialog = await screen.findByRole('dialog', { name: 'Editar producto' });

    await userEvent.clear(within(dialog).getByLabelText('Nombre del producto'));
    await userEvent.type(within(dialog).getByLabelText('Nombre del producto'), 'Doble Smash Editado');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Guardar cambios' }));

    await waitFor(() => {
      const update = fetchMock.calls.find((call) => call.method === 'PUT');

      expect(update?.body).toEqual({
        categoryId: 3,
        name: 'Doble Smash Editado',
        description: 'Dos hamburguesas de 110g con cheddar fundido y tocino crujiente.',
        price: 7990,
        available: true,
      });
    });
  });

  it('never renders unsupported per-product sales or margin metrics', async () => {
    mockFetch(ownerCatalogResponse);
    renderApp(CATALOG_PATH);

    await screen.findByRole('table', { name: 'Catálogo de productos' });

    const rendered = document.body.textContent ?? '';
    const salesLabel = ['Ventas', '7D'].join(' ');
    const marginLabel = ['Mar', 'gen'].join('');

    expect(rendered).not.toContain(salesLabel);
    expect(rendered).not.toContain(marginLabel);
  });

  it('confirms deletion and reloads the owner catalog after a 204 response', async () => {
    let deleted = false;
    const fetchMock = mockFetch((call) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/api/catalog/my/products/10' && call.method === 'DELETE') {
        deleted = true;
        return noContentResponse();
      }

      if (pathname === '/api/catalog/my/products' && call.method === 'GET') {
        return jsonResponse(deleted ? [] : OWNER_PRODUCTS);
      }

      return ownerCatalogResponse(call);
    });
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    renderApp(CATALOG_PATH);

    await userEvent.click(
      await screen.findByRole('button', { name: 'Eliminar Doble Smash Cheddar Bacon' }),
    );

    await waitFor(() =>
      expect(fetchMock.calls.some((call) => call.method === 'DELETE')).toBe(true),
    );
    expect(await screen.findByText('Tu catálogo está vacío')).toBeInTheDocument();
    expect(window.confirm).toHaveBeenCalledWith(
      '¿Eliminar “Doble Smash Cheddar Bacon” del catálogo?',
    );
  });

  it('opens a prefilled create form when a product is duplicated', async () => {
    mockFetch(ownerCatalogResponse);
    renderApp(CATALOG_PATH);

    await userEvent.click(
      await screen.findByRole('button', { name: 'Duplicar Doble Smash Cheddar Bacon' }),
    );

    const dialog = await screen.findByRole('dialog', { name: 'Nuevo producto' });
    expect(within(dialog).getByLabelText('Nombre del producto')).toHaveValue(
      'Doble Smash Cheddar Bacon',
    );
    expect(within(dialog).getByLabelText('Precio (CLP)')).toHaveValue('7990');
  });

  it('shows the loading state while the owner catalog is loading', async () => {
    mockFetch(() => new Promise<Response>(() => undefined));
    renderApp(CATALOG_PATH);

    expect(await screen.findByRole('status', { name: 'Cargando catálogo' })).toBeInTheDocument();
  });

  it('shows the empty state when the owner catalog has no products', async () => {
    mockFetch((call) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/api/catalog/my/products') {
        return jsonResponse([]);
      }

      return ownerCatalogResponse(call);
    });

    renderApp(CATALOG_PATH);

    expect(await screen.findByText('Tu catálogo está vacío')).toBeInTheDocument();
  });

  it('shows the literal backend message when the owner catalog fails', async () => {
    mockFetch((call) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/api/catalog/my/products') {
        return jsonResponse(
          {
            status: 500,
            error: 'INTERNAL_ERROR',
            message: 'No fue posible leer el catálogo de la tienda',
          },
          500,
        );
      }

      return ownerCatalogResponse(call);
    });

    renderApp(CATALOG_PATH);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No fue posible leer el catálogo de la tienda',
    );
  });
});
