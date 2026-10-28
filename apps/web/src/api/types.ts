/**
 * DTOs of the FlashDrop backend that the web client consumes today.
 *
 * Every shape mirrors the Java record it comes from; the source of truth is
 * the service itself:
 *
 * - `auth-service`: `TokenPair`, `AuthResult`, `LoginRequest`,
 *   `RefreshRequest`, `TokenClaims`, `UserProfile`.
 * - `orders-service` / `delivery-service`: the `{ success, message, data }`
 *   envelope.
 *
 * The marketplace, KDS and courier DTOs are deliberately absent: they belong
 * to the tasks that build those screens.
 */

/**
 * Response envelope of `orders-service` and `delivery-service`.
 *
 * `message` is present on both the success and the failure shape, so it is
 * the field the client reads when the backend reports a problem.
 */
export interface ApiEnvelope<T> {
  success: boolean;
  message?: string | null;
  data?: T | null;
}

/** `auth-service` — `TokenPair`. */
export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
}

/** `auth-service` — `AuthResult`, the answer of `POST /auth/login`. */
export interface AuthResult {
  userId: number;
  name: string;
  email: string;
  /** Role names as the backend spells them: `CLIENTE`, `RESTAURANTE`, `REPARTIDOR`. */
  roles: string[];
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
}

/** `auth-service` — `LoginRequest`. The `login` field carries the identifier. */
export interface LoginRequest {
  login: string;
  password: string;
}

/** `auth-service` — `RegisterRequest`, the body of `POST /auth/register`. */
export interface RegisterRequest {
  email: string;
  password: string;
  rut?: string | null;
  name?: string | null;
  lastName?: string | null;
  phone?: string | null;
}

/** `auth-service` — `RegisterUserResult`, the answer of `POST /auth/register`. */
export interface RegisterUserResult {
  userId: number;
}

/** `auth-service` — `RefreshRequest`. */
export interface RefreshRequest {
  refreshToken: string;
}

/** `auth-service` — `TokenClaims`, the answer of `GET /auth/validate`. */
export interface TokenClaims {
  userId: number;
  email: string;
  roles: string[];
}

/** `auth-service` — `UserProfile`, the answer of `GET|PUT /auth/profile`. */
export interface UserProfile {
  userId: number;
  name: string;
  lastName: string;
  email: string;
  phone: string | null;
  rut: string | null;
  photo: string | null;
  roles: string[];
}

/** `auth-service` — `UpdateProfileRequest`, the body of `PUT /auth/profile`. */
export interface UpdateProfileRequest {
  name: string;
  lastName: string;
  phone?: string | null;
  photo?: string | null;
}

/** Value accepted in a query string. */
export type QueryValue = string | number | boolean | null | undefined;

/** Query string parameters of a request. */
export type QueryParams = Record<string, QueryValue>;
