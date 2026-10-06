import type { HTMLAttributes, ReactNode } from "react";

export function Card({ children, className = "", ...rest }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={`rounded-xl border border-slate-200 bg-white ${className}`}
      {...rest}
    >
      {children}
    </div>
  );
}

export function CardBody({ children, className = "", ...rest }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={`p-5 sm:p-6 ${className}`} {...rest}>
      {children}
    </div>
  );
}

export function CardHeader({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <div className={`border-b border-slate-200 px-5 py-3.5 sm:px-6 ${className}`}>{children}</div>;
}
