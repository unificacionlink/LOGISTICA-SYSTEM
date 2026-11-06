import { useEffect, useState, type ReactNode } from "react";
import * as authApi from "../api/auth";
import type { Usuario } from "../api/types";
import { AuthContext } from "./AuthContext";

/** Sesion del usuario: se pide /api/auth/me al cargar la app. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<Usuario | null>(null);
  const [cargando, setCargando] = useState(true);

  async function refrescar() {
    const res = await authApi.me();
    setUsuario(res.ok ? res.result : null);
  }

  useEffect(() => {
    let vigente = true;
    authApi.me().then((res) => {
      if (!vigente) return;
      setUsuario(res.ok ? res.result : null);
      setCargando(false);
    });
    return () => {
      vigente = false;
    };
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
