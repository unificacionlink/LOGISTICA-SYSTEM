import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useLocation } from "react-router-dom";
import { Bug, Maximize, X } from "lucide-react";
import { NAV_GRUPOS } from "../navegacion";
import {
  anchoTexto,
  armarEsqueleto,
  bloqueEn,
  columnaEn,
  etiquetaBloque,
  medir,
  rutaBloque,
  type Bloque,
  type Caja,
} from "./esqueleto";
import FormReporte from "./FormReporte";

/**
 * Modo reporte (DEC-L104): la pantalla se vuelve un esqueleto (cada tarjeta,
 * tabla, fila, boton y campo dibujado como bloque) y el usuario senala la zona
 * con la observacion. La zona senalada se ve real a traves del esqueleto
 * mientras escribe el reporte.
 */

const COLOR = {
  fondo: "#f1f5f9",
  superficie: "#ffffff",
  borde: "#e2e8f0",
  bordeFuerte: "#cbd5e1",
  barra: "#e2e8f0",
  barraFuerte: "#cbd5e1",
  texto: "#94a3b8",
};

function moduloDe(pathname: string): string | null {
  let mejor: { to: string; label: string } | null = null;
  for (const g of NAV_GRUPOS) {
    for (const item of g.items) {
      if ((pathname === item.to || pathname.startsWith(`${item.to}/`)) && (!mejor || item.to.length > mejor.to.length)) {
        mejor = item;
      }
    }
  }
  return mejor?.label ?? null;
}

function recortarTexto(ctx: CanvasRenderingContext2D, texto: string, ancho: number): string {
  if (ctx.measureText(texto).width <= ancho) return texto;
  let t = texto;
  while (t.length > 1 && ctx.measureText(`${t}…`).width > ancho) t = t.slice(0, -1);
  return t.length > 1 ? `${t}…` : "";
}

function rect(ctx: CanvasRenderingContext2D, c: Caja, radio: number, relleno: string | null, borde: string | null) {
  ctx.beginPath();
  ctx.roundRect(c.x, c.y, c.w, c.h, Math.min(radio, c.h / 2, c.w / 2));
  if (relleno) {
    ctx.fillStyle = relleno;
    ctx.fill();
  }
  if (borde) {
    ctx.strokeStyle = borde;
    ctx.lineWidth = 1;
    ctx.stroke();
  }
}

/**
 * Dibuja un bloque segun su tipo: contenedores como superficies, hojas como
 * barras o pastillas. `columnas` son las cabeceras de la tabla (de la propia
 * tabla o de la tabla de la fila) para alinear las barras con las columnas reales.
 */
function dibujar(ctx: CanvasRenderingContext2D, b: Bloque, c: Caja, columnas: DOMRect[]) {
  switch (b.tipo) {
    case "tarjeta":
      rect(ctx, c, 12, COLOR.superficie, COLOR.borde);
      return;
    case "tabla": {
      rect(ctx, c, 6, COLOR.superficie, null);
      // Cabeceras rotuladas: orientan en el esqueleto ("la columna Disponible").
      ctx.font = "500 11px Geist, system-ui, sans-serif";
      ctx.fillStyle = COLOR.texto;
      ctx.textBaseline = "middle";
      b.cabeceras.forEach((th, k) => {
        const r = columnas[k];
        const texto = (th.textContent ?? "").replace(/\s+/g, " ").trim();
        if (!r || !texto || r.top < c.y || r.bottom > c.y + c.h) return;
        ctx.fillText(recortarTexto(ctx, texto, r.width - 16), r.left + 12, r.top + r.height / 2);
      });
      ctx.fillStyle = COLOR.borde;
      const ultima = columnas[columnas.length - 1];
      if (ultima && ultima.bottom > c.y && ultima.bottom < c.y + c.h) ctx.fillRect(c.x, ultima.bottom - 1, c.w, 1);
      return;
    }
    case "fila": {
      ctx.fillStyle = COLOR.borde;
      ctx.fillRect(c.x, c.y + c.h - 1, c.w, 1);
      const alto = Math.min(9, c.h * 0.35);
      const y = c.y + (c.h - alto) / 2;
      if (columnas.length > 0) {
        columnas.forEach((r, k) => {
          if (b.celdasLlenas[k] || r.width < 20) return;
          rect(ctx, { x: r.left + 12, y, w: Math.max(8, Math.min(r.width - 24, r.width * 0.6)), h: alto }, 4, COLOR.barra, null);
        });
        return;
      }
      const n = Math.max(1, Math.min(b.celdasLlenas.length, 8));
      const slot = c.w / n;
      for (let i = 0; i < n; i++) {
        if (b.celdasLlenas[i]) continue;
        rect(ctx, { x: c.x + i * slot + 12, y, w: Math.max(4, slot * 0.5 - 12), h: alto }, 4, COLOR.barra, null);
      }
      return;
    }
    case "pestanas":
      ctx.fillStyle = COLOR.borde;
      ctx.fillRect(c.x, c.y + c.h - 1, c.w, 1);
      return;
    case "titulo": {
      const tam = Math.max(11, Math.min(22, c.h * 0.55));
      ctx.font = `500 ${tam}px Geist, system-ui, sans-serif`;
      ctx.fillStyle = COLOR.texto;
      ctx.textBaseline = "middle";
      ctx.fillText(recortarTexto(ctx, b.nombre, c.w), c.x, c.y + c.h / 2);
      return;
    }
    case "etiqueta":
      ctx.font = "500 12px Geist, system-ui, sans-serif";
      ctx.fillStyle = COLOR.texto;
      ctx.textBaseline = "middle";
      ctx.fillText(recortarTexto(ctx, b.nombre, c.w), c.x, c.y + c.h / 2);
      return;
    case "texto": {
      const ancho = Math.min(c.w, anchoTexto(b.el) || c.w);
      const lineas = Math.max(1, Math.round(c.h / 20));
      const alto = Math.min(8, (c.h / lineas) * 0.45);
      for (let i = 0; i < lineas; i++) {
        const w = i === lineas - 1 && lineas > 1 ? ancho * 0.6 : ancho;
        rect(ctx, { x: c.x, y: c.y + (c.h / lineas) * (i + 0.5) - alto / 2, w, h: alto }, 4, COLOR.barra, null);
      }
      return;
    }
    case "boton": {
      rect(ctx, c, 6, COLOR.barraFuerte, null);
      if (b.nombre && c.h >= 18 && c.w >= 40) {
        ctx.font = "500 11px Geist, system-ui, sans-serif";
        ctx.fillStyle = "#64748b";
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillText(recortarTexto(ctx, b.nombre, c.w - 12), c.x + c.w / 2, c.y + c.h / 2);
        ctx.textAlign = "left";
      }
      return;
    }
    case "campo":
      rect(ctx, c, 6, COLOR.superficie, COLOR.bordeFuerte);
      return;
    case "imagen":
    case "grafico":
      rect(ctx, c, 8, COLOR.barra, null);
      return;
  }
}

interface Geometria {
  area: Caja;
  hover: { caja: Caja; etiqueta: string } | null;
  sel: { caja: Caja; etiqueta: string } | null;
}

type Seleccion = { tipo: "zona"; indice: number; zona: string; etiqueta: string } | { tipo: "pantalla" };

export default function ModoReporte({ onSalir }: { onSalir: (mensaje?: string) => void }) {
  const { pathname, search } = useLocation();
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const bloques = useRef<Bloque[]>([]);
  const cajas = useRef<(Caja | null)[]>([]);
  const marco = useRef<number | null>(null);
  const [hover, setHover] = useState<number | null>(null);
  const [seleccion, setSeleccion] = useState<Seleccion | null>(null);
  // Lo que se dibuja en el DOM (capa del puntero y contornos): se recalcula al mover o desplazar.
  const [geo, setGeo] = useState<Geometria>({ area: { x: 0, y: 0, w: 0, h: 0 }, hover: null, sel: null });
  const estado = useRef<{ hover: number | null; seleccion: Seleccion | null }>({ hover: null, seleccion: null });
  useEffect(() => {
    estado.current = { hover, seleccion };
  }, [hover, seleccion]);

  const pintar = useCallback(() => {
    marco.current = null;
    const canvas = canvasRef.current;
    const raiz = document.getElementById("contenido");
    if (!canvas || !raiz) return;
    const r = raiz.getBoundingClientRect();
    const top = Math.max(0, r.top);
    const a: Caja = { x: r.left, y: top, w: r.width, h: Math.max(0, Math.min(window.innerHeight, r.bottom) - top) };
    cajas.current = medir(bloques.current, a);

    const dpr = window.devicePixelRatio || 1;
    const ancho = Math.round(a.w * dpr);
    const alto = Math.round(a.h * dpr);
    if (canvas.width !== ancho || canvas.height !== alto) {
      canvas.width = ancho;
      canvas.height = alto;
    }
    canvas.style.left = `${a.x}px`;
    canvas.style.top = `${a.y}px`;
    canvas.style.width = `${a.w}px`;
    canvas.style.height = `${a.h}px`;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    ctx.setTransform(dpr, 0, 0, dpr, -a.x * dpr, -a.y * dpr);
    ctx.clearRect(a.x, a.y, a.w, a.h);
    ctx.fillStyle = COLOR.fondo;
    ctx.fillRect(a.x, a.y, a.w, a.h);
    // Cabeceras de cada tabla visible, medidas una vez por cuadro.
    const columnas = new Map<number, DOMRect[]>();
    const columnasDe = (i: number | null): DOMRect[] => {
      if (i === null) return [];
      let cols = columnas.get(i);
      if (!cols) {
        cols = bloques.current[i].cabeceras.map((th) => th.getBoundingClientRect());
        columnas.set(i, cols);
      }
      return cols;
    };
    bloques.current.forEach((b, i) => {
      const c = cajas.current[i];
      if (!c) return;
      const tabla = b.tipo === "tabla" ? i : b.tipo === "fila" && b.padre !== null && bloques.current[b.padre].tipo === "tabla" ? b.padre : null;
      dibujar(ctx, b, c, columnasDe(tabla));
    });
    // La zona bajo el puntero y la senalada se ven reales a traves del esqueleto.
    const { hover: h, seleccion: s } = estado.current;
    const indiceSel = s?.tipo === "zona" ? s.indice : null;
    const cajaHover = h !== null && !s ? cajas.current[h] : null;
    const cajaSel = indiceSel !== null ? cajas.current[indiceSel] : null;
    for (const c of [cajaHover, cajaSel]) {
      if (c) ctx.clearRect(c.x - 2, c.y - 2, c.w + 4, c.h + 4);
    }
    setGeo({
      area: a,
      hover: cajaHover && h !== null ? { caja: cajaHover, etiqueta: etiquetaBloque(bloques.current[h]) } : null,
      sel: cajaSel && s?.tipo === "zona" ? { caja: cajaSel, etiqueta: s.etiqueta } : null,
    });
  }, []);

  const repintar = useCallback(() => {
    if (marco.current === null) marco.current = requestAnimationFrame(pintar);
  }, [pintar]);

  // Arma el esqueleto al entrar, al cambiar de pantalla y cuando cambia el contenido (datos que llegan).
  useEffect(() => {
    const raiz = document.getElementById("contenido");
    if (!raiz) return;
    let espera: ReturnType<typeof setTimeout> | null = null;
    const armar = () => {
      bloques.current = armarEsqueleto(raiz);
      setHover(null);
      repintar();
    };
    armar();
    const observador = new MutationObserver(() => {
      if (espera) clearTimeout(espera);
      espera = setTimeout(armar, 200);
    });
    observador.observe(raiz, { childList: true, subtree: true, characterData: true });
    window.addEventListener("scroll", repintar, { capture: true, passive: true });
    window.addEventListener("resize", repintar);
    return () => {
      observador.disconnect();
      if (espera) clearTimeout(espera);
      window.removeEventListener("scroll", repintar, { capture: true });
      window.removeEventListener("resize", repintar);
      if (marco.current !== null) cancelAnimationFrame(marco.current);
      marco.current = null;
    };
  }, [pathname, repintar]);

  useEffect(() => {
    repintar();
  }, [hover, seleccion, repintar]);

  useEffect(() => {
    // Con el formulario abierto, Escape lo cierra el panel (vuelve a senalar).
    const tecla = (e: KeyboardEvent) => e.key === "Escape" && !estado.current.seleccion && onSalir();
    document.addEventListener("keydown", tecla);
    document.documentElement.classList.add("modo-reporte");
    return () => {
      document.removeEventListener("keydown", tecla);
      document.documentElement.classList.remove("modo-reporte");
    };
  }, [onSalir]);

  function alMover(e: React.PointerEvent) {
    const i = bloqueEn(cajas.current, e.clientX, e.clientY);
    if (i !== hover) setHover(i);
  }

  function alTocar(e: React.MouseEvent) {
    const i = bloqueEn(cajas.current, e.clientX, e.clientY);
    if (i === null) return;
    const columna = columnaEn(bloques.current, i, e.clientX);
    const ruta = rutaBloque(bloques.current, i);
    setSeleccion({
      tipo: "zona",
      indice: i,
      zona: columna ? `${ruta} › ${columna}` : ruta,
      etiqueta: etiquetaBloque(bloques.current[i]),
    });
  }

  const a = geo.area;
  const modulo = moduloDe(pathname);
  const h1 = document.querySelector("#contenido h1");
  const pantalla = h1 ? (h1.textContent ?? "").replace(/\s+/g, " ").trim().slice(0, 200) || null : null;

  return createPortal(
    <>
      <canvas ref={canvasRef} aria-hidden="true" className="pointer-events-none fixed z-[44]" />
      {/* Capa que recibe el puntero: la pantalla real no se opera en modo reporte. */}
      <div
        role="presentation"
        className="fixed z-[45] cursor-crosshair"
        style={{ left: a.x, top: a.y, width: a.w, height: a.h }}
        onPointerMove={alMover}
        onPointerLeave={() => setHover(null)}
        onClick={alTocar}
      />
      {geo.hover && <Contorno caja={geo.hover.caja} etiqueta={geo.hover.etiqueta} />}
      {geo.sel && <Contorno caja={geo.sel.caja} etiqueta={geo.sel.etiqueta} fijo />}

      <div
        className="fixed top-3 z-[46] flex -translate-x-1/2 items-center gap-1 whitespace-nowrap rounded-full border border-slate-200 bg-white py-1 pl-3 pr-1 shadow-flotante"
        style={{ left: a.x + a.w / 2 }}
      >
        <Bug className="size-4 text-brand-600" />
        <span className="mr-2 text-[0.8125rem] font-medium text-slate-800">
          Señala<span className="hidden sm:inline"> la zona</span>
        </span>
        <button
          type="button"
          onClick={() => setSeleccion({ tipo: "pantalla" })}
          className="flex h-7 items-center gap-1.5 rounded-full px-2.5 text-xs font-medium text-slate-600 hover:bg-slate-100 hover:text-slate-900"
        >
          <Maximize className="size-3.5" />
          Toda la pantalla
        </button>
        <button
          type="button"
          onClick={() => onSalir()}
          aria-label="Salir del modo reporte"
          title="Salir (Esc)"
          className="flex size-7 items-center justify-center rounded-full text-slate-500 hover:bg-slate-100 hover:text-slate-900"
        >
          <X className="size-4" />
        </button>
      </div>

      {seleccion && (
        <FormReporte
          modulo={modulo}
          pantalla={pantalla}
          ruta={pathname + search}
          zona={seleccion.tipo === "zona" ? seleccion.zona : null}
          onCambiarZona={() => setSeleccion(null)}
          onEnviado={(mensaje) => onSalir(mensaje)}
        />
      )}
    </>,
    document.body,
  );
}

function Contorno({ caja, etiqueta, fijo = false }: { caja: Caja; etiqueta: string; fijo?: boolean }) {
  const arriba = caja.y > 30;
  return (
    <div
      aria-hidden="true"
      className={`pointer-events-none fixed z-[45] rounded-md ring-2 ${fijo ? "ring-brand-600" : "ring-brand-400"}`}
      style={{ left: caja.x - 2, top: caja.y - 2, width: caja.w + 4, height: caja.h + 4 }}
    >
      <span
        className={`absolute left-0 max-w-[24rem] truncate rounded px-1.5 py-0.5 text-[0.6875rem] font-medium text-white ${
          fijo ? "bg-brand-700" : "bg-brand-600"
        } ${arriba ? "-top-6" : "top-1 left-1"}`}
      >
        {etiqueta}
      </span>
    </div>
  );
}
