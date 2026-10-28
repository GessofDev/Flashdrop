import { request } from './client';
import type {
  AuthResult,
  LoginRequest,
  RefreshRequest,
  TokenClaims,
  TokenPair,
  UpdateProfileRequest,
  UserProfile,
} from './types';

/**
 * Calls of `auth-service`.
 *
 * None of them uses the `{ success, message, data }` envelope: the service
 * answers with the bare DTO, so every call declares `responseShape: 'bare'`.
 */
export const authApi = {
  /** `POST /auth/login` — answers an `AuthResult` with the token pair. */
  login(credentials: LoginRequest): Promise<AuthResult> {
    return request<AuthResult>({
      path: '/auth/login',
      method: 'POST',
      body: credentials,
      responseShape: 'bare',
      session: 'public',
    });
  },

  /** `POST /auth/refresh` — rotates the refresh token. */
  refresh(refreshToken: string): Promise<TokenPair> {
    return request<TokenPair>({
      path: '/auth/refresh',
      method: 'POST',
      body: { refreshToken } satisfies RefreshRequest,
      responseShape: 'bare',
      session: 'public',
    });
  },

  /** `POST /auth/logout` — revokes the refresh token; answers 204. */
  async logout(refreshToken: string): Promise<void> {
    await request<null>({
      path: '/auth/logout',
      method: 'POST',
      body: { refreshToken } satisfies RefreshRequest,
      responseShape: 'bare',
      session: 'public',
    });
  },

  /** `GET /auth/validate` — the claims of the access token. */
  validate(): Promise<TokenClaims> {
    return request<TokenClaims>({
      path: '/auth/validate',
      responseShape: 'bare',
    });
  },

  /** `GET /auth/profile` — the profile of the signed in user. */
  profile(): Promise<UserProfile> {
    return request<UserProfile>({
      path: '/auth/profile',
      responseShape: 'bare',
    });
  },

  /** `PUT /auth/profile` — replaces the editable data of the profile. */
  updateProfile(profile: UpdateProfileRequest): Promise<UserProfile> {
    return request<UserProfile>({
      path: '/auth/profile',
      method: 'PUT',
      body: profile,
      responseShape: 'bare',
    });
  },
};
