import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import * as authApi from "../api/auth";
import type { Usuario } from "../api/types";

interface AuthContextValue {
  usuario: Usuario | null;
  cargando: boolean;
  tieneAlgunRol: (...roles: string[]) => boolean;
  refrescar: () => Promise<void>;
  cerrarSesion: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<Usuario | null>(null);
  const [cargando, setCargando] = useState(true);

  async function refrescar() {
    const res = await authApi.me();
    setUsuario(res.ok ? res.result : null);
  }

  useEffect(() => {
    refrescar().finally(() => setCargando(false));
  }, []);

  async function cerrarSesion() {
    await authApi.logout();
    setUsuario(null);
  }

  function tieneAlgunRol(...roles: string[]) {
    return usuario ? roles.some((r) => usuario.roles.includes(r)) : false;
  }

  return (
    <AuthContext.Provider value={{ usuario, cargando, tieneAlgunRol, refrescar, cerrarSesion }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth debe usarse dentro de AuthProvider.");
  return ctx;
}
