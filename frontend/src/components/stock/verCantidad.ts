import { useAuth } from "../../auth/AuthContext";

/**
 * Solo Logistica/Admin ven cuanto stock hay; quien pide ve si hay o no
 * (DEC-L059). El backend tampoco le manda la cantidad: le llega 1 = "con stock".
 */
export function useVerCantidadStock() {
  return useAuth().tieneAlgunRol("LOGISTICA", "ADMIN");
}
