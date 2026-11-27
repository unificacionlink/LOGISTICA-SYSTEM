/**
 * Esqueleto de la pantalla para el modo reporte (DEC-L104): recorre el
 * contenido y arma los bloques que se dibujan (tarjetas, tablas, filas,
 * titulos, botones, campos...), cada uno con el nombre con que se reconoce
 * ("Tarjeta «Pagos» › Botón «Registrar pago»"). Solo lee textos visibles;
 * nunca guarda el HTML ni los valores escritos en los campos.
 */

export type TipoBloque =
  | "tarjeta"
  | "tabla"
  | "fila"
  | "pestanas"
  | "titulo"
  | "etiqueta"
  | "texto"
  | "boton"
  | "campo"
  | "imagen"
  | "grafico";

export interface Bloque {
  el: Element;
  tipo: TipoBloque;
  nombre: string;
  /** Indice del bloque contenedor (tarjeta, tabla, pestañas) o null. */
  padre: number | null;
  /** Ancestro con overflow que recorta al elemento (tablas con scroll, tarjetas). */
  recorte: Element | null;
  /** Fila: que celdas ya tienen bloques propios (las vacias se dibujan como barra). */
  celdasLlenas: boolean[];
  /** Tabla: las cabeceras de columna (para alinear las barras de cada fila y rotularlas). */
  cabeceras: Element[];
}

export interface Caja {
  x: number;
  y: number;
  w: number;
  h: number;
}

export const ETIQUETA_TIPO: Record<TipoBloque, string> = {
  tarjeta: "Tarjeta",
  tabla: "Tabla",
  fila: "Fila",
  pestanas: "Pestañas",
  titulo: "Título",
  etiqueta: "Rótulo",
  texto: "Texto",
  boton: "Botón",
  campo: "Campo",
  imagen: "Imagen",
  grafico: "Gráfico",
};

/** Los bloques hoja no se recorren por dentro (un boton con su icono es un solo bloque). */
const HOJAS = new Set<TipoBloque>(["titulo", "etiqueta", "texto", "boton", "campo", "imagen", "grafico"]);
const CONTENEDORES = new Set<TipoBloque>(["tarjeta", "tabla", "pestanas", "fila"]);
const MAX_BLOQUES = 900;

function tipoDe(el: Element): TipoBloque | null {
  const tag = el.tagName.toLowerCase();
  if (el.getAttribute("role") === "tablist") return "pestanas";
  if (tag === "table") return "tabla";
  if (tag === "tr") return el.closest("thead") ? null : "fila";
  if (/^h[1-4]$/.test(tag)) return "titulo";
  // El rotulo de un campo (Field): orienta en el esqueleto ("CECO", "Prioridad").
  if (tag === "label" && !el.querySelector("input, select, textarea")) return "etiqueta";
  if (tag === "button" || (tag === "a" && el.hasAttribute("href")) || el.getAttribute("role") === "button") return "boton";
  if (tag === "input") return (el as HTMLInputElement).type === "hidden" ? null : "campo";
  if (tag === "select" || tag === "textarea") return "campo";
  if (tag === "img") return "imagen";
  if (tag === "canvas") return "grafico";
  if (tag === "svg") {
    const r = el.getBoundingClientRect();
    return r.width >= 80 && r.height >= 60 ? "grafico" : null;
  }
  if (tag === "p" || tag === "dl") return "texto";
  if (el.hasAttribute("data-esqueleto") || el.classList.contains("shadow-superficie")) return "tarjeta";
  return null;
}

function limpio(t: string | null | undefined, max = 60): string {
  const s = (t ?? "").replace(/\s+/g, " ").trim();
  return s.length > max ? `${s.slice(0, max - 1)}…` : s;
}

/** Texto propio de un elemento sin el de los controles que contiene (opciones de un select, etc.). */
function textoVisible(el: Element, max = 60): string {
  let t = "";
  const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, {
    acceptNode: (n) =>
      n.parentElement?.closest("select, option, textarea, script, style, [aria-hidden='true']")
        ? NodeFilter.FILTER_REJECT
        : NodeFilter.FILTER_ACCEPT,
  });
  while (walker.nextNode() && t.length < max * 2) t += ` ${walker.currentNode.textContent ?? ""}`;
  return limpio(t, max);
}

function nombreCampo(el: Element): string {
  const control = el as HTMLInputElement;
  const etiqueta = control.labels?.[0] ?? el.closest("label");
  const porLabel = etiqueta ? textoVisible(etiqueta, 40) : "";
  if (porLabel) return porLabel;
  // Field: la etiqueta es el <label> hermano anterior dentro del mismo bloque.
  const hermano = el.parentElement?.querySelector(":scope > label");
  if (hermano && hermano !== el) return textoVisible(hermano, 40);
  return limpio(el.getAttribute("aria-label") || el.getAttribute("placeholder") || el.getAttribute("title"), 40);
}

function nombreDe(el: Element, tipo: TipoBloque): string {
  switch (tipo) {
    case "titulo":
    case "etiqueta":
    case "texto":
      return textoVisible(el);
    case "boton":
      return limpio(el.getAttribute("aria-label")) || textoVisible(el, 40) || limpio(el.getAttribute("title"), 40);
    case "campo":
      return nombreCampo(el);
    case "tabla": {
      const cabeceras = [...el.querySelectorAll("thead th")].map((th) => textoVisible(th, 20)).filter(Boolean);
      return cabeceras.slice(0, 3).join(", ");
    }
    case "fila": {
      const celdas = [...el.children].map((td) => textoVisible(td, 30)).filter(Boolean);
      return limpio(celdas.slice(0, 2).join(" · "), 60);
    }
    case "pestanas": {
      const tabs = [...el.querySelectorAll("[role='tab'], button")].map((b) => textoVisible(b, 20)).filter(Boolean);
      return tabs.slice(0, 3).join(" / ");
    }
    case "tarjeta": {
      const titulo = el.querySelector("h1, h2, h3, h4, .rotulo, legend");
      return titulo ? textoVisible(titulo, 50) : "";
    }
    case "imagen":
      return limpio(el.getAttribute("alt"), 40);
    case "grafico":
      return limpio(el.getAttribute("aria-label"), 40);
  }
}

export function etiquetaBloque(b: Bloque): string {
  return b.nombre ? `${ETIQUETA_TIPO[b.tipo]} «${b.nombre}»` : ETIQUETA_TIPO[b.tipo];
}

/** "Tarjeta «Órdenes» › Tabla (Código, Total) › Fila «OC-0012 · …»": lo que se guarda como zona. */
export function rutaBloque(bloques: Bloque[], i: number): string {
  const partes: string[] = [];
  let actual: number | null = i;
  while (actual !== null && partes.length < 4) {
    const b: Bloque = bloques[actual];
    // Un contenedor sin nombre ("Tarjeta") no ayuda a ubicar la zona.
    if (actual === i || b.nombre) {
      partes.unshift(b.tipo === "tabla" && b.nombre ? `Tabla (${b.nombre})` : etiquetaBloque(b));
    }
    actual = b.padre;
  }
  return partes.join(" › ");
}

/** La columna de la tabla bajo el puntero, para una fila senalada ("Columna «Disponible»"). */
export function columnaEn(bloques: Bloque[], i: number, x: number): string | null {
  const b = bloques[i];
  const tabla = b.tipo === "fila" && b.padre !== null ? bloques[b.padre] : null;
  if (!tabla || tabla.tipo !== "tabla") return null;
  const th = tabla.cabeceras.find((c) => {
    const r = c.getBoundingClientRect();
    return x >= r.left && x <= r.right;
  });
  const texto = th ? limpio(th.textContent, 30) : "";
  return texto ? `Columna «${texto}»` : null;
}

function recortaDentro(el: Element, raiz: Element, cache: Map<Element, Element | null>): Element | null {
  const padre = el.parentElement;
  if (!padre || padre === raiz || !raiz.contains(padre)) return null;
  const guardado = cache.get(padre);
  if (guardado !== undefined) return guardado;
  const estilo = getComputedStyle(padre);
  const recorta = estilo.overflowX !== "visible" || estilo.overflowY !== "visible";
  const resultado = recorta ? padre : recortaDentro(padre, raiz, cache);
  cache.set(padre, resultado);
  return resultado;
}

/** Arma los bloques visibles dentro de la raiz (el contenido de la pantalla). */
export function armarEsqueleto(raiz: Element): Bloque[] {
  const bloques: Bloque[] = [];
  const recortes = new Map<Element, Element | null>();

  function recorrer(el: Element, padre: number | null) {
    for (const hijo of el.children) {
      if (bloques.length >= MAX_BLOQUES) return;
      if (hijo.hasAttribute("data-esqueleto-ignorar") || hijo.getAttribute("aria-hidden") === "true") continue;
      const tipo = tipoDe(hijo);
      let siguiente = padre;
      if (tipo) {
        const r = hijo.getBoundingClientRect();
        if (r.width < 3 || r.height < 3) {
          if (HOJAS.has(tipo) || tipo === "tarjeta") continue;
        } else {
          bloques.push({
            el: hijo,
            tipo,
            nombre: nombreDe(hijo, tipo),
            padre,
            recorte: recortaDentro(hijo, raiz, recortes),
            celdasLlenas: [],
            cabeceras: tipo === "tabla" ? [...hijo.querySelectorAll("thead tr:last-child > th")] : [],
          });
          if (CONTENEDORES.has(tipo)) siguiente = bloques.length - 1;
        }
      }
      const desde = bloques.length;
      if (!tipo || !HOJAS.has(tipo)) recorrer(hijo, siguiente);
      if (tipo === "fila" && siguiente !== padre) {
        const celdas = [...hijo.children];
        const llenas = celdas.map(() => false);
        for (let k = desde; k < bloques.length; k++) {
          const celda = bloques[k].el.closest("td, th");
          const idx = celda ? celdas.indexOf(celda) : -1;
          if (idx >= 0) llenas[idx] = true;
        }
        bloques[siguiente as number].celdasLlenas = llenas;
      }
    }
  }

  recorrer(raiz, null);
  return bloques;
}

function interseccion(a: Caja, b: DOMRect | Caja): Caja | null {
  const bx = "x" in b ? b.x : 0;
  const x1 = Math.max(a.x, bx);
  const y1 = Math.max(a.y, b.y);
  const x2 = Math.min(a.x + a.w, bx + ("w" in b ? b.w : b.width));
  const y2 = Math.min(a.y + a.h, b.y + ("h" in b ? b.h : b.height));
  return x2 - x1 >= 2 && y2 - y1 >= 2 ? { x: x1, y: y1, w: x2 - x1, h: y2 - y1 } : null;
}

/** Cajas en pantalla (coordenadas de la ventana), recortadas por su contenedor y el area visible. */
export function medir(bloques: Bloque[], area: Caja): (Caja | null)[] {
  const clips = new Map<Element, DOMRect>();
  return bloques.map((b) => {
    const r = b.el.getBoundingClientRect();
    let caja = interseccion(area, r);
    if (caja && b.recorte) {
      let clip = clips.get(b.recorte);
      if (!clip) {
        clip = b.recorte.getBoundingClientRect();
        clips.set(b.recorte, clip);
      }
      caja = interseccion(caja, clip);
    }
    return caja;
  });
}

/** Ancho real del texto (un titulo es un bloque de todo el ancho; su texto no). */
export function anchoTexto(el: Element): number {
  const rango = document.createRange();
  rango.selectNodeContents(el);
  return rango.getBoundingClientRect().width;
}

/** El bloque mas chico bajo el puntero: el que el usuario quiere senalar. */
export function bloqueEn(cajas: (Caja | null)[], x: number, y: number): number | null {
  let mejor: number | null = null;
  let area = Infinity;
  cajas.forEach((c, i) => {
    if (!c || x < c.x || x > c.x + c.w || y < c.y || y > c.y + c.h) return;
    const a = c.w * c.h;
    if (a <= area) {
      area = a;
      mejor = i;
    }
  });
  return mejor;
}
