import { asList } from './asList';
import { request } from './client';
import type { ProductImageUpload, ProductResponse } from './types';

/**
 * Body of the owner endpoints of the catalog.
 *
 * The service derives the owner from the token, so `restaurantId` never
 * travels in the body. Every field is optional because the endpoints accept
 * different shapes: the creation accepts a partial product and the update
 * requires `categoryId`, `name`, `price` and `available`.
 */
export interface OwnerProductPayload {
  /** Category the product belongs to. */
  categoryId?: number;
  /** Name of the product, up to 100 characters. */
  name?: string;
  /** Description of the product, up to 255 characters. */
  description?: string;
  /** Price of the product, in CLP. */
  price?: number;
  /** Key of the object of the picture, not its URL. */
  image?: string;
  /** Whether the product is offered to the customers. */
  available?: boolean;
}

/**
 * Calls of the catalog of the owner (`catalog-service`).
 *
 * The service answers with the bare DTO — no `{ success, message, data }`
 * envelope — so every call declares `responseShape: 'bare'`. The calls carry
 * the session: the owner is resolved from the token, never from the body.
 */
export const storeCatalogApi = {
  /**
   * `GET /api/catalog/my/products` — the products of the restaurant of the
   * token, including the ones that are not available.
   */
  listMyProducts(): Promise<ProductResponse[]> {
    return request<unknown>({
      path: '/api/catalog/my/products',
      method: 'GET',
      responseShape: 'bare',
    }).then((answer) => asList<ProductResponse>(answer));
  },

  /**
   * `POST /api/catalog/my/products` — creates a product of the restaurant of
   * the token.
   */
  createProduct(payload: OwnerProductPayload): Promise<ProductResponse> {
    return request<ProductResponse>({
      path: '/api/catalog/my/products',
      method: 'POST',
      body: payload,
      responseShape: 'bare',
    });
  },

  /**
   * `PUT /api/catalog/my/products/{productId}` — replaces a product.
   *
   * There is no `PATCH` in this endpoint: the request is a full
   * replacement, so the caller sends the whole product.
   */
  updateProduct(
    productId: number,
    payload: OwnerProductPayload,
  ): Promise<ProductResponse> {
    return request<ProductResponse>({
      path: `/api/catalog/my/products/${productId}`,
      method: 'PUT',
      body: payload,
      responseShape: 'bare',
    });
  },

  /**
   * `DELETE /api/catalog/my/products/{productId}` — removes a product.
   *
   * The endpoint answers with a 204 and no body.
   */
  deleteProduct(productId: number): Promise<void> {
    return request<null>({
      path: `/api/catalog/my/products/${productId}`,
      method: 'DELETE',
      responseShape: 'bare',
    }).then(() => undefined);
  },

  /**
   * `POST /api/catalog/my/products/image` — uploads the picture of a product.
   *
   * The body is multipart with the part `file`; the client must not write the
   * `Content-Type` header, because the browser adds the boundary. The answer
   * carries the key of the object, and that key is the `image` of the
   * product.
   */
  uploadImage(file: File): Promise<ProductImageUpload> {
    const form = new FormData();

    form.append('file', file);

    return request<ProductImageUpload>({
      path: '/api/catalog/my/products/image',
      method: 'POST',
      body: form,
      responseShape: 'bare',
    });
  },
};
