import { useCallback, useEffect, useLayoutEffect, useRef, useState, type CSSProperties } from "react";
import { createPortal } from "react-dom";
import { useLocation, useNavigate } from "react-router-dom";
import { GraduationCap, X } from "lucide-react";
import { marcarTutorial } from "../../api/auth";
import { useAuth } from "../../auth/AuthContext";
import Button from "../ui/Button";
import { EVENTO_RECORRIDO, RECORRIDOS, recorridoInicial, type Recorrido } from "./recorridos";

/**
 * Recorrido guiado (DEC-L055): oscurece la pantalla menos el elemento del paso
 * (`data-tutorial`) y explica que hacer en un globo. Es solo explicativo: la
 * capa bloquea los clics, no se crea nada. Lo visto o saltado se guarda por
 * usuario; en el primer ingreso se ofrece con una tarjeta de bienvenida.
 */
/** Tras cambiar de pantalla las tarjetas muestran esqueletos; se espera al ancla hasta este tope. */
const ESPERA_MAXIMA = 2500;
const MARGEN = 16;
const HOLGURA = 6;
const SEPARACION = 12;
const ANCHO_GLOBO = 352;

interface Caja {
  top: number;
  left: number;
  width: number;
  height: number;
}

function iguales(a: Caja | null, b: Caja | null) {
  return !!a && !!b && a.top === b.top && a.left === b.left && a.width === b.width && a.height === b.height;
}

/** Misma pantalla y mismos parametros que pide el paso (la pantalla puede agregar otros). */
function enRuta(ruta: string, pathname: string, search: string) {
  const destino = new URL(ruta, window.location.origin);
  if (destino.pathname !== pathname) return false;
  const actuales = new URLSearchParams(search);
  for (const [k, v] of destino.searchParams) {
    if (actuales.get(k) !== v) return false;
  }
  return true;
}

/** Rectangulo del ancla si se ve; null si esta fuera de pantalla (p. ej. el menu lateral en el celular). */
function cajaVisible(el: Element): Caja | null {
  const r = el.getBoundingClientRect();
  if (r.width === 0 || r.height === 0 || r.right <= 0 || r.left >= window.innerWidth) return null;
  return { top: r.top, left: r.left, width: r.width, height: r.height };
}

/** Debajo del ancla; si no cabe, encima; si tampoco, a su derecha; si no, al pie de la pantalla. */
function ubicarGlobo(caja: Caja | null, alto: number): CSSProperties {
  const vw = document.documentElement.clientWidth;
  const vh = window.innerHeight;
  const ancho = Math.min(ANCHO_GLOBO, vw - 2 * MARGEN);
  const limitar = (v: number, min: number, max: number) => Math.max(min, Math.min(v, max));
  if (!caja) {
    return { width: ancho, left: (vw - ancho) / 2, top: Math.max(MARGEN, (vh - alto) / 2) };
  }
  const left = limitar(caja.left - HOLGURA, MARGEN, vw - ancho - MARGEN);
  const abajo = caja.top + caja.height + HOLGURA + SEPARACION;
  if (abajo + alto <= vh - MARGEN) return { width: ancho, left, top: abajo };
  const arriba = caja.top - HOLGURA - SEPARACION - alto;
  if (arriba >= MARGEN) return { width: ancho, left, top: arriba };
  const derecha = caja.left + caja.width + HOLGURA + SEPARACION;
  if (derecha + ancho <= vw - MARGEN) {
    return { width: ancho, left: derecha, top: limitar(caja.top, MARGEN, vh - alto - MARGEN) };
  }
  return { width: ancho, left, top: Math.max(MARGEN, vh - alto - MARGEN) };
}

export default function RecorridoGuiado() {
  const { usuario, tieneAlgunRol, refrescar } = useAuth();
  const navigate = useNavigate();
  const { pathname, search } = useLocation();

  const [actual, setActual] = useState<{ recorrido: Recorrido; indice: number } | null>(null);
  // Lo que se sabe del ancla del paso; vale solo para el paso de su `clave`.
  const [seguimiento, setSeguimiento] = useState<{ clave: string | null; caja: Caja | null; listo: boolean }>({
    clave: null,
    caja: null,
    listo: false,
  });
  const [altoGlobo, setAltoGlobo] = useState(0);
  // La bienvenida se ofrece una vez por sesion aunque el guardado falle.
  const [bienvenidaCerrada, setBienvenidaCerrada] = useState(false);
  const globo = useRef<HTMLDivElement>(null);
  const navegado = useRef<string | null>(null);

  const paso = actual ? actual.recorrido.pasos[actual.indice] : null;
  const clavePaso = actual ? `${actual.recorrido.id}:${actual.indice}` : null;
  const vigente = seguimiento.clave === clavePaso;
  const caja = vigente ? seguimiento.caja : null;
  const listo = !!paso && (!paso.ancla || (vigente && seguimiento.listo));

  useEffect(() => {
    const abrir = (e: Event) => {
      const r = RECORRIDOS.find((x) => x.id === (e as CustomEvent<string>).detail);
      if (!r) return;
      setBienvenidaCerrada(true);
      setActual({ recorrido: r, indice: 0 });
    };
    window.addEventListener(EVENTO_RECORRIDO, abrir);
    return () => window.removeEventListener(EVENTO_RECORRIDO, abrir);
  }, []);

  // Lleva a la pantalla del paso (una vez por paso: la pantalla puede reescribir sus parametros).
  useEffect(() => {
    if (!paso?.ruta || !clavePaso || navegado.current === clavePaso) return;
    navegado.current = clavePaso;
    if (!enRuta(paso.ruta, pathname, search)) navigate(paso.ruta);
  }, [paso, clavePaso, pathname, search, navigate]);

  // Sigue al ancla cuadro a cuadro: aparece tras los esqueletos, el menu se despliega, la pagina se mueve.
  useEffect(() => {
    if (!paso?.ancla || !clavePaso) return;
    const clave = clavePaso;
    const selector = `[data-tutorial="${CSS.escape(paso.ancla)}"]`;
    const suave = !window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const inicio = performance.now();
    let cuadro = 0;
    let desplazado = false;
    let lateral: HTMLElement | null = null;

    const seguir = () => {
      const el = document.querySelector(selector);
      const menu = el?.closest<HTMLElement>(".sidebar") ?? null;
      if (menu && menu !== lateral) {
        lateral = menu;
        menu.dataset.recorrido = "";
      }
      const c = el ? cajaVisible(el) : null;
      if (el && c && !desplazado) {
        desplazado = true;
        el.scrollIntoView({ block: "center", behavior: suave ? "smooth" : "auto" });
      }
      const vencido = performance.now() - inicio > ESPERA_MAXIMA;
      setSeguimiento((prev) => {
        const listoAhora = !!c || vencido || (prev.clave === clave && prev.listo);
        if (prev.clave === clave && prev.listo === listoAhora && (iguales(prev.caja, c) || (!prev.caja && !c))) {
          return prev;
        }
        return { clave, caja: c, listo: listoAhora };
      });
      cuadro = requestAnimationFrame(seguir);
    };
    cuadro = requestAnimationFrame(seguir);
    return () => {
      cancelAnimationFrame(cuadro);
      if (lateral) delete lateral.dataset.recorrido;
    };
  }, [paso, clavePaso]);

  useLayoutEffect(() => {
    const el = globo.current;
    if (!el) return;
    const medir = () => setAltoGlobo(el.offsetHeight);
    medir();
    const observador = new ResizeObserver(medir);
    observador.observe(el);
    return () => observador.disconnect();
  }, [clavePaso]);

  const terminar = useCallback(
    async (estado: "COMPLETADO" | "OMITIDO") => {
      const recorrido = actual?.recorrido;
      setActual(null);
      navegado.current = null;
      if (!recorrido) return;
      const res = await marcarTutorial(recorrido.id, estado);
      if (res.ok) await refrescar();
    },
    [actual, refrescar],
  );

  const ultimo = actual ? actual.indice === actual.recorrido.pasos.length - 1 : false;

  const siguiente = useCallback(() => {
    if (!actual) return;
    if (ultimo) void terminar("COMPLETADO");
    else setActual({ ...actual, indice: actual.indice + 1 });
  }, [actual, ultimo, terminar]);

  const atras = useCallback(() => {
    if (actual && actual.indice > 0) setActual({ ...actual, indice: actual.indice - 1 });
  }, [actual]);

  useEffect(() => {
    if (!actual) return;
    const tecla = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement | null;
      if (t?.closest("input, textarea, select")) return;
      if (e.key === "Escape") {
        e.preventDefault();
        void terminar("OMITIDO");
      } else if (e.key === "ArrowRight") {
        e.preventDefault();
        siguiente();
      } else if (e.key === "ArrowLeft") {
        e.preventDefault();
        atras();
      }
    };
    document.addEventListener("keydown", tecla, true);
    return () => document.removeEventListener("keydown", tecla, true);
  }, [actual, siguiente, atras, terminar]);

  // El foco va al boton principal de cada paso (teclado y lectores de pantalla).
  useEffect(() => {
    if (listo) globo.current?.querySelector<HTMLButtonElement>("[data-principal]")?.focus({ preventScroll: true });
  }, [listo, clavePaso]);

  if (!usuario) return null;

  if (!actual || !paso) {
    const inicial = recorridoInicial(tieneAlgunRol);
    if (bienvenidaCerrada || usuario.tutorialesVistos.includes(inicial.id)) return null;
    return (
      <Bienvenida
        onEmpezar={() => {
          setBienvenidaCerrada(true);
          setActual({ recorrido: inicial, indice: 0 });
        }}
        onAhoraNo={() => {
          setBienvenidaCerrada(true);
          void marcarTutorial(inicial.id, "OMITIDO").then((res) => {
            if (res.ok) void refrescar();
          });
        }}
      />
    );
  }

  const total = actual.recorrido.pasos.length;
  const posicion = ubicarGlobo(caja, altoGlobo);

  return createPortal(
    <div role="dialog" aria-modal="true" aria-labelledby="recorrido-titulo" aria-describedby="recorrido-texto">
      {/* Bloquea los clics: el recorrido solo muestra. Sin ancla, la capa misma oscurece. */}
      <div className={`fixed inset-0 z-[80] ${caja ? "" : "bg-slate-900/55"}`} />
      {caja && (
        <div
          aria-hidden="true"
          className="pointer-events-none fixed z-[81] rounded-lg ring-2 ring-white/85 transition-[top,left,width,height] duration-300 ease-(--ease-suave) motion-reduce:transition-none"
          style={{
            top: caja.top - HOLGURA,
            left: caja.left - HOLGURA,
            width: caja.width + 2 * HOLGURA,
            height: caja.height + 2 * HOLGURA,
            boxShadow: "0 0 0 9999px rgb(15 27 45 / 0.55)",
          }}
        />
      )}
      <div
        ref={globo}
        style={{ ...posicion, visibility: listo ? "visible" : "hidden" }}
        className="fixed z-[82] rounded-xl border border-slate-200 bg-white p-4 shadow-flotante"
      >
        <div className="flex items-center justify-between gap-3">
          <p className="text-xs font-medium text-brand-600">
            Paso {actual.indice + 1} de {total}
          </p>
          <button
            type="button"
            onClick={() => void terminar("OMITIDO")}
            aria-label="Cerrar recorrido"
            className="-mr-1 rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
          >
            <X className="size-4" />
          </button>
        </div>
        <h2 id="recorrido-titulo" className="mt-1 text-[0.9375rem] font-semibold text-slate-900">
          {paso.titulo}
        </h2>
        <p id="recorrido-texto" className="mt-1 text-sm leading-relaxed text-slate-600">
          {paso.texto}
        </p>
        <div className="mt-4 flex items-center gap-2">
          <div className="flex flex-1 gap-1" aria-hidden="true">
            {actual.recorrido.pasos.map((_, i) => (
              <span
                key={i}
                className={`h-1 rounded-full ${i === actual.indice ? "w-4 bg-brand-600" : "w-1.5 bg-slate-200"}`}
              />
            ))}
          </div>
          {actual.indice > 0 && (
            <Button size="sm" variant="secondary" onClick={atras}>
              Atrás
            </Button>
          )}
          <Button size="sm" data-principal="" onClick={siguiente}>
            {ultimo ? "Terminar" : "Siguiente"}
          </Button>
        </div>
      </div>
    </div>,
    document.body,
  );
}

function Bienvenida({ onEmpezar, onAhoraNo }: { onEmpezar: () => void; onAhoraNo: () => void }) {
  return createPortal(
    <div
      role="dialog"
      aria-labelledby="bienvenida-titulo"
      className="animate-capa-in fixed inset-x-4 bottom-4 z-[60] sm:left-auto sm:right-6 sm:bottom-6 sm:w-[22rem]"
    >
      <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-flotante">
        <div className="flex gap-3">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
            <GraduationCap className="size-5" />
          </span>
          <div className="min-w-0">
            <h2 id="bienvenida-titulo" className="text-[0.9375rem] font-semibold text-slate-900">
              ¿Te mostramos cómo usar el sistema?
            </h2>
            <p className="mt-0.5 text-sm text-slate-600">Un recorrido de 2 minutos por lo principal.</p>
          </div>
        </div>
        <div className="mt-4 flex justify-end gap-2">
          <Button size="sm" variant="ghost" onClick={onAhoraNo}>
            Ahora no
          </Button>
          <Button size="sm" onClick={onEmpezar}>
            Empezar
          </Button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
