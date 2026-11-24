import { sessionStore } from '@/session/tokenStore';

import { request } from './client';
import type {
  AuthResult,
  LoginRequest,
  RefreshRequest,
  RegisterRequest,
  RegisterUserResult,
  TokenClaims,
  TokenPair,
  UpdateProfileRequest,
  UserProfile,
} from './types';

/**
 * Body of `DELETE /auth/account`.
 *
 * It carries the password of the account and nothing else: the user always
 * comes from the token.
 */
export interface DeleteAccountRequest {
  password: string;
}

/**
 * Calls of `auth-service`.
 *
 * None of them uses the `{ success, message, data }` envelope: the service
 * answers with the bare DTO, so every call declares `responseShape: 'bare'`.
 */
export const authApi = {
  /** `POST /auth/register` — creates an account and answers with its id. */
  register(user: RegisterRequest): Promise<RegisterUserResult> {
    return request<RegisterUserResult>({
      path: '/auth/register',
      method: 'POST',
      body: user,
      responseShape: 'bare',
      session: 'public',
    });
  },

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

  /** `POST /auth/password/forgot` — answers 202 whether or not the account exists. */
  async forgotPassword(email: string): Promise<void> {
    await request<null>({
      path: '/auth/password/forgot',
      method: 'POST',
      body: { email },
      responseShape: 'bare',
      session: 'public',
    });
  },

  /** `POST /auth/password/reset` — sets the new password with the emailed token; answers 204. */
  async resetPassword(token: string, newPassword: string): Promise<void> {
    await request<null>({
      path: '/auth/password/reset',
      method: 'POST',
      body: { token, newPassword },
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

  /**
   * `DELETE /auth/account` — deletes the account of the signed in user.
   *
   * The account is disabled at once and the data of the other services is
   * erased in the background, so the answer is a 202 with an empty body: the
   * call declares the bare shape, because the envelope shape would read the
   * empty body as a broken answer.
   *
   * The body carries only the password: the user always comes from the token,
   * so the call never sends an id.
   *
   * The `Authorization` header is written by hand and the call is declared
   * public, because a 401 here means two different things: the password is
   * wrong (the account stays) or the access token expired (the session is
   * gone). The client treats every 401 of an authenticated call as an expired
   * session, so it would forget the session and send the customer to the
   * login route after a wrong password. Here the answer of the backend is
   * shown as it comes and the session is only forgotten when the deletion
   * succeeds.
   */
  async deleteAccount(password: string): Promise<void> {
    const tokens = sessionStore.getTokens();

    await request<null>({
      path: '/auth/account',
      method: 'DELETE',
      body: { password } satisfies DeleteAccountRequest,
      responseShape: 'bare',
      session: 'public',
      headers: tokens === null ? {} : { Authorization: `Bearer ${tokens.accessToken}` },
    });
  },
};
