import { conProgreso } from "./progreso";

/**
 * Base de la API. En desarrollo, el backend corre aparte (VITE_API_URL en
 * .env.local, o :8080). En produccion Spring sirve el frontend y la API en el
 * mismo dominio: sin VITE_API_URL se usa el origen de la pagina (antes caia en
 * localhost:8080 y el navegador no llegaba al backend).
 */
export const API_URL =
  import.meta.env.VITE_API_URL ?? (import.meta.env.DEV ? "http://localhost:8080" : window.location.origin);

export interface ApiResponse<T> {
  ok: boolean;
  result: T | null;
  message: string;
  code: string | null;
  error: string | null;
}

async function parsearRespuesta<T>(res: Response): Promise<ApiResponse<T>> {
  const texto = await res.text();
  if (!texto) {
    return { ok: false, result: null, message: "No tienes sesion activa.", code: "NO_AUTORIZADO", error: null };
  }
  return JSON.parse(texto) as ApiResponse<T>;
}

// fetch() rechaza (TypeError) si el backend no esta corriendo, no responde, o
// CORS bloquea la request — sin este catch, esos casos quedaban sin manejar
// en ningun lado: el boton de turno volvia a su estado normal (el `finally`
// de cada pantalla) pero no se mostraba ningun mensaje de error.
function fetchSeguro<T>(input: string, init: RequestInit): Promise<ApiResponse<T>> {
  const tipo = !init.method || init.method === "GET" ? "cargando" : "guardando";
  return conProgreso(tipo, () => fetchSinProgreso<T>(input, init));
}

async function fetchSinProgreso<T>(input: string, init: RequestInit): Promise<ApiResponse<T>> {
  let res: Response;
  try {
    res = await fetch(input, init);
  } catch {
    return {
      ok: false,
      result: null,
      message: "No se pudo conectar con el servidor. Verifica tu conexion o que el backend este corriendo.",
      code: "SIN_CONEXION",
      error: null,
    };
  }
  return parsearRespuesta<T>(res);
}

async function request<T>(path: string, options?: RequestInit): Promise<ApiResponse<T>> {
  return fetchSeguro<T>(`${API_URL}${path}`, {
    ...options,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(options?.headers ?? {}),
    },
  });
}

// Descarga un archivo (Excel/PDF) con la cookie de sesion y lo guarda con el
// nombre indicado. Si el backend responde un error JSON, se devuelve su mensaje.
function descargar(path: string, nombreArchivo: string): Promise<ApiResponse<null>> {
  return conProgreso("descargando", () => descargarSinProgreso(path, nombreArchivo));
}

async function descargarSinProgreso(path: string, nombreArchivo: string): Promise<ApiResponse<null>> {
  let res: Response;
  try {
    res = await fetch(`${API_URL}${path}`, { method: "GET", credentials: "include" });
  } catch {
    return {
      ok: false,
      result: null,
      message: "No se pudo conectar con el servidor. Verifica tu conexion o que el backend este corriendo.",
      code: "SIN_CONEXION",
      error: null,
    };
  }
  if (!res.ok || (res.headers.get("Content-Type") ?? "").includes("application/json")) {
    const error = await parsearRespuesta<null>(res);
    return { ...error, ok: false };
  }
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement("a");
  a.href = url;
  a.download = nombreArchivo;
  a.click();
  URL.revokeObjectURL(url);
  return { ok: true, result: null, message: "Descarga lista.", code: null, error: null };
}

export const http = {
  get: <T>(path: string) => request<T>(path, { method: "GET" }),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "POST", body: body !== undefined ? JSON.stringify(body) : undefined }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "PUT", body: body !== undefined ? JSON.stringify(body) : undefined }),
  patch: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "PATCH", body: body !== undefined ? JSON.stringify(body) : undefined }),
  // Sin header Content-Type manual: el navegador arma el boundary de multipart.
  postForm: <T>(path: string, form: FormData) =>
    fetchSeguro<T>(`${API_URL}${path}`, { method: "POST", credentials: "include", body: form }),
  /** Igual que postForm pero sin la capa que bloquea la pantalla: para lotes con su propio progreso. */
  postFormEnLote: <T>(path: string, form: FormData) =>
    conProgreso("cargando", () =>
      fetchSinProgreso<T>(`${API_URL}${path}`, { method: "POST", credentials: "include", body: form }),
    ),
  descargar,
};
