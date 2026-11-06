import { createContext, useContext } from "react";
import type { Usuario } from "../api/types";

export interface AuthContextValue {
  usuario: Usuario | null;
  cargando: boolean;
  tieneAlgunRol: (...roles: string[]) => boolean;
  refrescar: () => Promise<void>;
  cerrarSesion: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth debe usarse dentro de AuthProvider.");
  return ctx;
}
