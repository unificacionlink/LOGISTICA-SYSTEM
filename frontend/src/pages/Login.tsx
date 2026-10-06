import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { Lock, Mail } from "lucide-react";
import * as authApi from "../api/auth";
import { useAuth } from "../auth/AuthContext";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import Field, { inputClass } from "../components/ui/Field";
import EscenaEntrega from "../components/EscenaEntrega";

export default function Login() {
  const [correo, setCorreo] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const navigate = useNavigate();
  const { refrescar } = useAuth();

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      const res = await authApi.login(correo, password);
      if (!res.ok) {
        setError(res.message);
        return;
      }
      await refrescar();
      navigate("/inicio");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="grid min-h-dvh bg-slate-100 lg:grid-cols-[1.15fr_1fr]">
      {/* Escena animada (rubro electrico): arriba en movil, a la izquierda en escritorio. */}
      {/* Cielo nocturno con estrellas tenues; la escena apoya en una franja de suelo hasta el borde. */}
      <section
        className="flex flex-col justify-end overflow-hidden bg-brand-950 pt-6"
        style={{
          backgroundImage:
            "radial-gradient(1px 1px at 12% 18%, rgb(255 255 255 / 0.55), transparent 60%)," +
            "radial-gradient(1px 1px at 38% 9%, rgb(255 255 255 / 0.4), transparent 60%)," +
            "radial-gradient(1.5px 1.5px at 64% 22%, rgb(255 255 255 / 0.45), transparent 60%)," +
            "radial-gradient(1px 1px at 82% 12%, rgb(255 255 255 / 0.4), transparent 60%)," +
            "radial-gradient(1px 1px at 26% 36%, rgb(255 255 255 / 0.32), transparent 60%)," +
            "radial-gradient(1px 1px at 90% 40%, rgb(255 255 255 / 0.32), transparent 60%)," +
            "radial-gradient(1.5px 1.5px at 50% 30%, rgb(255 255 255 / 0.3), transparent 60%)",
        }}
      >
        <EscenaEntrega className="block h-auto w-full" />
        <div className="h-10 bg-[#0a2442] sm:h-16 lg:h-[16vh]" />
      </section>

      <div className="flex items-center justify-center px-5 py-12">
        <div className="w-full max-w-sm">
          <div className="mb-9">
            <h1 className="text-[26px] font-semibold tracking-[0.06em] text-brand-900">ERP LOGÍSTICO</h1>
            <p className="mt-1.5 text-xs font-medium tracking-[0.18em] text-slate-500">TDEM · GOYDEL · METRIN</p>
          </div>

          <form onSubmit={onSubmit} className="flex flex-col gap-4">
            <Field label="Correo">
              <div className="relative">
                <Mail className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                <input
                  type="email"
                  required
                  autoComplete="email"
                  placeholder="tu.correo@tdem.com"
                  value={correo}
                  onChange={(e) => setCorreo(e.target.value)}
                  className={`${inputClass} w-full pl-9`}
                />
              </div>
            </Field>
            <Field label="Contraseña">
              <div className="relative">
                <Lock className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                <input
                  type="password"
                  required
                  autoComplete="current-password"
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className={`${inputClass} w-full pl-9`}
                />
              </div>
            </Field>

            {error && <Alert tone="danger">{error}</Alert>}

            <Button type="submit" loading={enviando} className="mt-2 w-full py-2.5">
              {enviando ? "Ingresando…" : "Ingresar"}
            </Button>
          </form>
        </div>
      </div>
    </div>
  );
}
