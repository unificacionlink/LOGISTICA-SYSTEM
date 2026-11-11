/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

/** Version actual, tomada de src/novedades.ts en vite.config.ts. */
declare const __VERSION_APP__: string;
