import { Component, type ErrorInfo, type ReactNode } from "react";
import { RotateCw, TriangleAlert } from "lucide-react";
import { reportarError } from "../registro/reporteErrores";

/**
 * Si una pantalla falla al dibujarse, en vez de quedar en blanco muestra un
 * aviso con "Recargar" y reporta el error al registro (DEC-L060). Tambien
 * cubre el caso tipico tras un despliegue: la pestana vieja pide un archivo
 * que ya no existe; recargar lo resuelve.
 */
export default class PantallaError extends Component<{ children: ReactNode }, { fallo: boolean }> {
  state = { fallo: false };

  static getDerivedStateFromError() {
    return { fallo: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    reportarError(error.message, `${error.stack ?? ""}\n${info.componentStack ?? ""}`);
  }

  render() {
    if (!this.state.fallo) return this.props.children;
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 p-4">
        <div className="flex w-full max-w-sm flex-col items-center gap-4 rounded-xl border border-slate-200 bg-white p-8 text-center shadow-superficie">
          <div className="flex size-11 items-center justify-center rounded-xl bg-amber-50">
            <TriangleAlert className="size-5 text-amber-600" />
          </div>
          <div>
            <p className="font-medium text-slate-900">Algo salió mal en esta pantalla</p>
            <p className="mt-1 text-sm text-slate-500">Ya quedó registrado. Recarga para continuar.</p>
          </div>
          <button
            type="button"
            onClick={() => window.location.reload()}
            className="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700"
          >
            <RotateCw className="size-4" />
            Recargar
          </button>
        </div>
      </div>
    );
  }
}
