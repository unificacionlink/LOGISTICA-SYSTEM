import { useEffect, useRef } from "react";

/*
 * Escena del login (rubro electrico): un operario sale del almacen con una
 * caja, camina bajo el tendido electrico y la entrega en la subestacion; se
 * enciende el farol y aparece el check de entregado. Se dibuja en tiempo real
 * (requestAnimationFrame) moviendo atributos del SVG, sin re-render de React.
 * Se anima SIEMPRE, aun con prefers-reduced-motion (DEC-L021): es la pieza
 * central del login, el movimiento es pequeno (sin zoom ni parallax) y muchas
 * PCs corporativas tienen los efectos de animacion de Windows apagados, lo que
 * la dejaba congelada.
 */

const CICLO = 11; // segundos
const SUELO = 420;
// Operario y caja se dibujan a escala 1.5 para que se lean bien en el panel.
const ESCALA = 1.5;
const X_ALMACEN = 124; // sale por la puerta del almacen
const X_ENTREGA = 505;
const CAJA_SUELO = { x: 545, y: SUELO - 22 * ESCALA };

// Tramos de la linea de tiempo (segundos).
const T = { aparece: 0.6, llega: 6.8, suelta: 7.6, luz: 9.6 };

const COLOR = {
  torre: "#2c5487",
  cable: "#3a669c",
  pulso: "#ffd166",
  edificio: "#0d2c50",
  borde: "#3b6aa3",
  persona: "#e6edf6",
  casco: "#f2b33d",
  caja: "#e0a43a",
  rayo: "#0a2547",
  luz: "#ffd166",
  ok: "#5bbf8a",
};

const clamp = (v: number, a = 0, b = 1) => Math.min(b, Math.max(a, v));
const tramo = (t: number, desde: number, hasta: number) => clamp((t - desde) / (hasta - desde));
const suave = (p: number) => p * p * (3 - 2 * p); // smoothstep
const mezcla = (a: number, b: number, p: number) => a + (b - a) * p;

/** Torre de alta tension en celosia, con la base centrada en x. */
function Torre({ x, alto, escala = 1 }: { x: number; alto: number; escala?: number }) {
  const top = SUELO - alto;
  const base = 26 * escala;
  const cima = 7 * escala;
  const brazo = 34 * escala;
  const tramos = 5;
  const lineas = [];
  for (let i = 0; i < tramos; i++) {
    const y1 = SUELO - (alto * i) / tramos;
    const y2 = SUELO - (alto * (i + 1)) / tramos;
    const w1 = mezcla(base, cima, i / tramos);
    const w2 = mezcla(base, cima, (i + 1) / tramos);
    lineas.push(`M${x - w1} ${y1} L${x + w2} ${y2} M${x + w1} ${y1} L${x - w2} ${y2} M${x - w2} ${y2} L${x + w2} ${y2}`);
  }
  return (
    <g stroke={COLOR.torre} strokeWidth={1.6} fill="none" strokeLinecap="round">
      <path d={`M${x - base} ${SUELO} L${x - cima} ${top} M${x + base} ${SUELO} L${x + cima} ${top}`} strokeWidth={2.2} />
      <path d={lineas.join(" ")} opacity={0.8} />
      {/* Crucetas */}
      <path d={`M${x - brazo} ${top + 14 * escala} L${x + brazo} ${top + 14 * escala} M${x - brazo * 0.75} ${top + 34 * escala} L${x + brazo * 0.75} ${top + 34 * escala}`} strokeWidth={2} />
      <path d={`M${x - cima} ${top} L${x} ${top - 12 * escala} L${x + cima} ${top}`} />
    </g>
  );
}

/** Cable con catenaria entre dos puntos (curva cuadratica). */
function cable(x1: number, y1: number, x2: number, y2: number, flecha = 26) {
  return `M${x1} ${y1} Q${(x1 + x2) / 2} ${(y1 + y2) / 2 + flecha} ${x2} ${y2}`;
}

export default function EscenaEntrega({ className = "" }: { className?: string }) {
  const persona = useRef<SVGGElement>(null);
  const piernaA = useRef<SVGGElement>(null);
  const piernaB = useRef<SVGGElement>(null);
  const brazo = useRef<SVGLineElement>(null);
  const caja = useRef<SVGGElement>(null);
  const halo = useRef<SVGCircleElement>(null);
  const foco = useRef<SVGCircleElement>(null);
  const check = useRef<SVGGElement>(null);

  useEffect(() => {
    function dibujar(t: number) {
      // Caminata: arranca y frena suave.
      const pCamino = suave(tramo(t, T.aparece, T.llega));
      const x = mezcla(X_ALMACEN, X_ENTREGA, pCamino);
      const caminando = t > T.aparece && t < T.llega;
      const fase = (t - T.aparece) * 2 * Math.PI * 1.55; // ~1.5 pasos por segundo
      const amplitud = caminando ? 26 * Math.min(1, pCamino * 6, (1 - pCamino) * 6) : 0;
      const angulo = Math.sin(fase) * amplitud;
      const rebote = caminando ? -Math.abs(Math.sin(fase)) * 3 * ESCALA * (amplitud / 26) : 0;

      const aparecer = tramo(t, 0, T.aparece);
      const desvanecer = 1 - tramo(t, T.luz, CICLO - 0.2);
      const opacidad = Math.min(aparecer, desvanecer);

      persona.current?.setAttribute("transform", `translate(${x} ${SUELO + rebote}) scale(${ESCALA})`);
      persona.current?.setAttribute("opacity", String(opacidad));
      piernaA.current?.setAttribute("transform", `rotate(${angulo} 0 -34)`);
      piernaB.current?.setAttribute("transform", `rotate(${-angulo} 0 -34)`);

      // Brazo: adelante mientras carga; baja al soltar la caja.
      const pSuelta = suave(tramo(t, T.llega, T.suelta));
      brazo.current?.setAttribute("x2", String(mezcla(18, 7, pSuelta)));
      brazo.current?.setAttribute("y2", String(mezcla(-47, -32, pSuelta)));

      // Caja: en las manos (con el balanceo) y luego al suelo en arco.
      const enManos = { x: x + 10 * ESCALA, y: SUELO + rebote - 62 * ESCALA };
      const cx = mezcla(enManos.x, CAJA_SUELO.x, pSuelta);
      const cy = mezcla(enManos.y, CAJA_SUELO.y, pSuelta) - Math.sin(pSuelta * Math.PI) * 10;
      caja.current?.setAttribute("transform", `translate(${cx} ${cy}) scale(${ESCALA})`);
      caja.current?.setAttribute("opacity", String(opacidad));

      // Entrega: farol encendido + check.
      const pLuz = suave(tramo(t, T.suelta, T.suelta + 0.8)) * desvanecer;
      halo.current?.setAttribute("opacity", String(pLuz));
      halo.current?.setAttribute("r", String(mezcla(10, 40, pLuz)));
      foco.current?.setAttribute("opacity", String(mezcla(0.25, 1, pLuz)));
      const pCheck = suave(tramo(t, T.suelta + 0.3, T.suelta + 0.9)) * desvanecer;
      check.current?.setAttribute("opacity", String(pCheck));
      check.current?.setAttribute(
        "transform",
        `translate(${CAJA_SUELO.x + 14 * ESCALA} ${CAJA_SUELO.y - 30}) scale(${mezcla(0.6, 1, pCheck) * 1.3})`,
      );
    }

    let raf = 0;
    const inicio = performance.now();
    const cuadro = (ahora: number) => {
      dibujar(((ahora - inicio) / 1000) % CICLO);
      raf = requestAnimationFrame(cuadro);
    };
    raf = requestAnimationFrame(cuadro);
    return () => cancelAnimationFrame(raf);
  }, []);

  // Tendido: torres de fondo y cables entre crucetas.
  const torres = [
    { x: 250, alto: 250, escala: 1 },
    { x: 470, alto: 230, escala: 0.92 },
  ];
  const cables = [
    cable(90, 238, 250 - 34, SUELO - 250 + 14),
    cable(250 + 34, SUELO - 250 + 14, 470 - 31, SUELO - 230 + 13),
    cable(470 + 31, SUELO - 230 + 13, 700, 300, 18),
    cable(250 + 25, SUELO - 250 + 34, 470 - 23, SUELO - 230 + 31, 22),
  ];

  return (
    <svg
      viewBox="20 140 780 300"
      preserveAspectRatio="xMidYMax meet"
      className={className}
      role="img"
      aria-label="Un operario lleva una caja desde el almacén hasta la subestación eléctrica"
    >
      <defs>
        {/* Brillo difuso del farol (no un disco plano). */}
        <radialGradient id="brillo-farol">
          <stop offset="0%" stopColor={COLOR.luz} stopOpacity={0.9} />
          <stop offset="45%" stopColor={COLOR.luz} stopOpacity={0.35} />
          <stop offset="100%" stopColor={COLOR.luz} stopOpacity={0} />
        </radialGradient>
      </defs>

      {/* Tendido electrico con pulsos de energia */}
      {torres.map((t) => (
        <Torre key={t.x} {...t} />
      ))}
      <g fill="none" stroke={COLOR.cable} strokeWidth={1.3} opacity={0.75}>
        {cables.map((d, i) => (
          <path key={i} id={`cable-${i}`} d={d} />
        ))}
      </g>
      <g>
        {cables.map((_, i) => (
          <circle key={i} r={2.6} fill={COLOR.pulso} opacity={0}>
            <animateMotion dur={`${2.6 + i * 0.4}s`} repeatCount="indefinite" begin={`${i * 0.7}s`}>
              <mpath href={`#cable-${i}`} />
            </animateMotion>
            <animate attributeName="opacity" values="0;1;1;0" dur={`${2.6 + i * 0.4}s`} repeatCount="indefinite" begin={`${i * 0.7}s`} />
          </circle>
        ))}
      </g>

      {/* Suelo: relleno hasta el borde inferior (empalma con la franja del login) */}
      <rect x={0} y={SUELO} width={820} height={40} fill="#0a2442" />
      <line x1={0} y1={SUELO} x2={820} y2={SUELO} stroke={COLOR.borde} strokeWidth={1.5} />
      <g stroke={COLOR.torre} strokeWidth={1.5} strokeLinecap="round" opacity={0.6}>
        {[240, 300, 360, 420, 480].map((x) => (
          <line key={x} x1={x} y1={SUELO + 14} x2={x + 22} y2={SUELO + 14} />
        ))}
      </g>

      {/* Almacen */}
      <g stroke={COLOR.borde} strokeWidth={1.6} strokeLinejoin="round">
        <rect x={40} y={322} width={150} height={98} fill={COLOR.edificio} />
        <path d="M32 322 L115 282 L198 322 Z" fill={COLOR.edificio} />
        <rect x={100} y={356} width={48} height={64} fill="#0a2442" />
        <path d="M100 368 H148 M100 380 H148 M100 392 H148 M100 404 H148" opacity={0.6} />
        <rect x={52} y={342} width={30} height={20} fill="#0a2442" />
      </g>

      {/* Subestacion: transformador, aisladores, cerco y farol */}
      <g stroke={COLOR.borde} strokeWidth={1.6} strokeLinejoin="round">
        <rect x={622} y={346} width={82} height={74} rx={4} fill={COLOR.edificio} />
        <path d="M634 358 V408 M646 358 V408 M658 358 V408" opacity={0.55} />
        {[676, 690].map((x) => (
          <g key={x}>
            <line x1={x} y1={346} x2={x} y2={326} />
            <circle cx={x} cy={330} r={3.5} fill={COLOR.edificio} />
            <circle cx={x} cy={338} r={3.5} fill={COLOR.edificio} />
          </g>
        ))}
        <path d="M676 324 Q700 300 700 300" fill="none" stroke={COLOR.cable} />
        <path
          d="M681 364 l-8 16 h8 l-4 14 l14 -20 h-8 l5 -10 z"
          fill={COLOR.luz}
          stroke="none"
          opacity={0.9}
        />
        <g opacity={0.7}>
          {[600, 612, 724, 736, 748, 760].map((x) => (
            <line key={x} x1={x} y1={SUELO} x2={x} y2={392} />
          ))}
          <line x1={600} y1={398} x2={612} y2={398} />
          <line x1={724} y1={398} x2={760} y2={398} />
        </g>
        <line x1={774} y1={SUELO} x2={774} y2={292} strokeWidth={2.2} />
        <path d="M774 294 h-14" strokeWidth={2.2} />
      </g>
      <circle ref={halo} cx={758} cy={300} r={8} fill="url(#brillo-farol)" opacity={0} />
      <circle ref={foco} cx={758} cy={300} r={5} fill={COLOR.luz} opacity={0.25} />

      {/* Caja (se dibuja antes que la persona: queda detras del brazo) */}
      <g ref={caja}>
        <rect width={28} height={22} rx={2} fill={COLOR.caja} />
        <path d="M0 7 H28" stroke="#b97f22" strokeWidth={1.2} />
        <path d="M15 9 l-5 7 h4 l-2 5 l7 -8 h-4 l3 -4 z" fill={COLOR.rayo} />
      </g>

      {/* Operario con casco; origen en los pies */}
      <g ref={persona} stroke={COLOR.persona} strokeLinecap="round" fill="none">
        <g ref={piernaA}>
          <line x1={0} y1={-34} x2={0} y2={0} strokeWidth={5} />
        </g>
        <g ref={piernaB} opacity={0.8}>
          <line x1={0} y1={-34} x2={0} y2={0} strokeWidth={5} />
        </g>
        <line x1={0} y1={-34} x2={2} y2={-62} strokeWidth={6} />
        <line ref={brazo} x1={2} y1={-58} x2={18} y2={-47} strokeWidth={4.5} />
        <circle cx={4} cy={-72} r={8} fill={COLOR.persona} stroke="none" />
        <path d="M-5 -74 a9 9 0 0 1 18 0 z" fill={COLOR.casco} stroke="none" />
        <path d="M-7 -74 H16" stroke={COLOR.casco} strokeWidth={2.5} />
      </g>

      {/* Check de entregado */}
      <g ref={check} opacity={0}>
        <circle r={11} fill={COLOR.ok} />
        <path d="M-5 0 l3.5 3.5 l6.5 -7" stroke="#07301b" strokeWidth={2.4} fill="none" strokeLinecap="round" strokeLinejoin="round" />
      </g>
    </svg>
  );
}
