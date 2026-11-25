/** Minusculas y sin tildes, para comparar lo que escribe el usuario con los datos. */
export function normalizar(texto: string): string {
  return texto.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase();
}

/**
 * Prepara una busqueda por palabras sueltas, en cualquier orden y sin importar
 * tildes: "camisa denim tdem" encuentra "CAMISA DENIM AZUL G48 LOGO TDEM".
 * Devuelve el filtro para cada texto; sin palabras, todo coincide.
 */
export function buscador(consulta: string): (texto: string) => boolean {
  const palabras = normalizar(consulta).split(/\s+/).filter(Boolean);
  if (palabras.length === 0) return () => true;
  return (texto) => {
    const t = normalizar(texto);
    return palabras.every((p) => t.includes(p));
  };
}
