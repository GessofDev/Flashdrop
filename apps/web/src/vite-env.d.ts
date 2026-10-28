/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Base URL of the API.
   *
   * Empty by default: the app calls the API through the relative paths the
   * dev server proxies to the gateway (`/api`, `/auth`, `/catalog`).
   */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
