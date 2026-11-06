/** Lado mayor de la foto publicada: suficiente para la ficha del catalogo y el zoom. */
const LADO_MAXIMO = 1400;
const CALIDAD = 0.85;
/** Formatos que acepta el backend (RequerimientoService.subirImagenProducto). */
const ACEPTADOS = ["image/jpeg", "image/png", "image/webp", "image/gif"];

export function esImagen(file: File): boolean {
  return file.type.startsWith("image/") || /\.(jpe?g|png|webp|gif|heic|heif)$/i.test(file.name);
}

/**
 * Reduce la foto en el navegador antes de subirla: una foto de celular de
 * 4-8 MB queda en ~200-400 KB (JPEG, fondo blanco si tenia transparencia), asi
 * la subida es rapida y no choca con el limite de 8 MB del servidor. Si no se
 * puede procesar (formato que el navegador no lee) o no gana nada, devuelve la
 * original cuando el servidor la acepta.
 */
/** @param lado lado mayor final (las cotizaciones usan mas para que el OCR lea bien la letra chica). */
export async function comprimirImagen(file: File, lado = LADO_MAXIMO): Promise<File> {
  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
  } catch {
    if (ACEPTADOS.includes(file.type)) return file;
    throw new Error("Formato no soportado: usa JPG, PNG o WEBP.");
  }
  const escala = Math.min(1, lado / Math.max(bitmap.width, bitmap.height));
  const ancho = Math.max(1, Math.round(bitmap.width * escala));
  const alto = Math.max(1, Math.round(bitmap.height * escala));
  const canvas = document.createElement("canvas");
  canvas.width = ancho;
  canvas.height = alto;
  const ctx = canvas.getContext("2d");
  if (!ctx) {
    bitmap.close();
    return file;
  }
  ctx.fillStyle = "#ffffff";
  ctx.fillRect(0, 0, ancho, alto);
  ctx.drawImage(bitmap, 0, 0, ancho, alto);
  bitmap.close();
  const blob = await new Promise<Blob | null>((r) => canvas.toBlob(r, "image/jpeg", CALIDAD));
  if (!blob || (blob.size >= file.size && ACEPTADOS.includes(file.type))) return file;
  const nombre = file.name.replace(/\.[^.]+$/, "") + ".jpg";
  return new File([blob], nombre, { type: "image/jpeg" });
}
