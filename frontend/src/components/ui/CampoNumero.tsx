import type { InputHTMLAttributes } from "react";
import { limpiarNumero } from "../../utils/numeros";

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, "type" | "value" | "onChange" | "min" | "max" | "step"> & {
  value: string;
  onChange: (valor: string) => void;
  /** 0 = solo enteros (cantidades, bultos, dias). */
  decimales?: number;
  /** Tope de digitos de la parte entera. */
  maxEnteros?: number;
  /** Si se escribe mas, se deja en este valor (ej. lo pendiente por recibir). */
  max?: number;
};

/**
 * Campo numerico: solo acepta lo que el dato admite mientras se escribe (sin
 * letras, signos ni "e", sin decimales si es una cantidad, largo acotado).
 */
export default function CampoNumero({ value, onChange, decimales = 0, maxEnteros = 6, max, ...rest }: Props) {
  return (
    <input
      {...rest}
      type="text"
      inputMode={decimales > 0 ? "decimal" : "numeric"}
      autoComplete="off"
      value={value}
      onChange={(e) => {
        let v = limpiarNumero(e.target.value, decimales, maxEnteros);
        if (max != null && v !== "" && Number(v) > max) v = String(max);
        onChange(v);
      }}
    />
  );
}
