/**
 * Preference about the session of the tab.
 *
 * The tokens never leave `sessionStorage`: they are credentials and they must
 * not outlive the tab. What this flag decides is narrower — whether the app
 * restores the session when the page is reloaded **inside the same tab**.
 *
 * It lives in `localStorage` because it is a preference and not a credential.
 */

/** Key of the preference in `localStorage`. */
export const REMEMBER_SESSION_KEY = 'flashdrop.rememberSession.v1';

/**
 * Whether the session has to survive a reload of the same tab.
 *
 * The default is `true`: the session lives in `sessionStorage`, so it is
 * forgotten when the tab closes anyway.
 */
export function isRememberSessionEnabled(): boolean {
  if (typeof window === 'undefined') {
    return true;
  }

  try {
    const raw = window.localStorage.getItem(REMEMBER_SESSION_KEY);

    if (raw === null) {
      return true;
    }

    return raw !== 'false';
  } catch {
    return true;
  }
}

/** Remember the session of the tab, or forget it on the next reload. */
export function setRememberSessionEnabled(enabled: boolean): void {
  if (typeof window === 'undefined') {
    return;
  }

  try {
    window.localStorage.setItem(REMEMBER_SESSION_KEY, enabled ? 'true' : 'false');
  } catch {
    // A storage that refuses to write must not break the login.
  }
}
