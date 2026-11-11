/**
 * Fotos de producto (Supabase Storage, bucket publico). Se suben tal cual
 * (hasta varios MB), pero se muestran chicas: se piden al servicio de
 * transformacion de Supabase al tamano mostrado (x2 para pantallas de alta
 * densidad). Responde WebP si el navegador lo acepta, y con cache publica de
 * un ano: al reemplazar una foto cambia su URL, asi que nunca queda vieja.
 * Una URL que no es del bucket (p. ej. vista previa local) se deja igual.
 */
const ORIGINAL = "/storage/v1/object/public/";
const TRANSFORMADA = "/storage/v1/render/image/public/";

export function urlImagen(url: string, ancho: number, alto?: number): string {
  if (!url.includes(ORIGINAL)) return url;
  const q = new URLSearchParams({ width: String(ancho), quality: "75" });
  if (alto) {
    q.set("height", String(alto));
    q.set("resize", "cover");
  } else {
    q.set("resize", "contain");
  }
  return `${url.replace(ORIGINAL, TRANSFORMADA)}?${q}`;
}

/** Si la version transformada falla, vuelve a la original una sola vez. */
export function respaldoOriginal(original: string) {
  return (e: React.SyntheticEvent<HTMLImageElement>) => {
    const img = e.currentTarget;
    if (img.src !== original) img.src = original;
  };
}
