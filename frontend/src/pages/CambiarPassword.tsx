import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { KeyRound } from "lucide-react";
import { cambiarPassword } from "../api/auth";
import { useAuth } from "../auth/AuthContext";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card, CardBody } from "../components/ui/Card";
import Field, { inputClass } from "../components/ui/Field";
import PageHeader from "../components/ui/PageHeader";

/**
 * Cambio de la propia contrasena. Con {@code obligatorio} se muestra sola, sin
 * menu, tras una contrasena temporal generada por ADMIN (DEC-L024).
 */
export default function CambiarPassword({ obligatorio = false }: { obligatorio?: boolean }) {
  const { refrescar, cerrarSesion } = useAuth();
  const navigate = useNavigate();
  const [actual, setActual] = useState("");
  const [nueva, setNueva] = useState("");
  const [confirmacion, setConfirmacion] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [exito, setExito] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  async function onGuardar(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (nueva !== confirmacion) {
      setError("La confirmación no coincide con la nueva contraseña.");
      return;
    }
    setGuardando(true);
    try {
      const res = await cambiarPassword(actual, nueva);
      if (!res.ok) {
        setError(res.message);
        return;
      }
      setActual("");
      setNueva("");
      setConfirmacion("");
      if (obligatorio) {
        await refrescar();
        navigate("/inicio");
      } else {
        setExito(res.message);
      }
    } finally {
      setGuardando(false);
    }
  }

  const formulario = (
    <form onSubmit={onGuardar} className="flex flex-col gap-4">
      <Field label={obligatorio ? "Contraseña temporal" : "Contraseña actual"} required>
        <input
          type="password"
          autoComplete="current-password"
          required
          value={actual}
          onChange={(e) => setActual(e.target.value)}
          className={`${inputClass} w-full`}
        />
      </Field>
      <Field label="Nueva contraseña" required>
        <input
          type="password"
          autoComplete="new-password"
          required
          minLength={8}
          value={nueva}
          onChange={(e) => setNueva(e.target.value)}
          placeholder="Mínimo 8, con letras y números"
          className={`${inputClass} w-full`}
        />
      </Field>
      <Field label="Confirmar nueva contraseña" required>
        <input
          type="password"
          autoComplete="new-password"
          required
          value={confirmacion}
          onChange={(e) => setConfirmacion(e.target.value)}
          className={`${inputClass} w-full`}
        />
      </Field>
      {error && <Alert tone="danger">{error}</Alert>}
      {exito && <Alert tone="good">{exito}</Alert>}
      <Button type="submit" loading={guardando} className="self-start">
        Guardar contraseña
      </Button>
    </form>
  );

  if (!obligatorio) {
    return (
      <div className="max-w-md">
        <PageHeader title="Cambiar contraseña" />
        <Card>
          <CardBody>{formulario}</CardBody>
        </Card>
      </div>
    );
  }

  return (
    <div className="flex min-h-dvh items-center justify-center bg-slate-100 px-4">
      <Card className="w-full max-w-sm">
        <CardBody className="flex flex-col gap-5">
          <div className="flex items-center gap-3">
            <span className="flex size-9 items-center justify-center rounded-md bg-brand-700 text-white">
              <KeyRound className="size-4.5" />
            </span>
            <div>
              <h1 className="font-semibold text-slate-900">Crea tu contraseña</h1>
              <p className="text-xs text-slate-500">Reemplaza la contraseña temporal para continuar.</p>
            </div>
          </div>
          {formulario}
          <button
            type="button"
            onClick={() => cerrarSesion()}
            className="self-start text-xs font-medium text-slate-500 hover:text-slate-800"
          >
            Cerrar sesión
          </button>
        </CardBody>
      </Card>
    </div>
  );
}
