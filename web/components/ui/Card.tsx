import { ReactNode } from "react";
import { clsx } from "clsx";

interface CardProps {
  children: ReactNode;
  header?: string;
  className?: string;
  padding?: "standard" | "generous" | "compact";
  hover?: boolean;
}

interface BalanceCardProps {
  currency: string;
  amount: string;
  subtitle?: string;
  className?: string;
}

export default function Card({ children, header, className, padding = "standard", hover }: CardProps) {
  return (
    <div
      className={clsx(
        "bg-white border border-ova-200/60 rounded-2xl",
        hover && "hover:border-ova-300 hover:shadow-card-hover cursor-pointer transition-all duration-base",
        className,
      )}
    >
      {header && (
        <div className="px-6 py-4 border-b border-ova-100">
          <h3 className="text-body-sm font-semibold text-ova-900 font-display">{header}</h3>
        </div>
      )}
      <div className={
        padding === "generous" ? "p-8" :
        padding === "compact" ? "p-4" : "p-6"
      }>{children}</div>
    </div>
  );
}

export function BalanceCard({ currency, amount, subtitle = "Available balance", className }: BalanceCardProps) {
  return (
    <div
      className={clsx(
        "bg-white border border-ova-200/60 rounded-2xl p-6 hover:border-ova-300 hover:shadow-card-hover transition-all duration-base cursor-pointer group",
        className,
      )}
    >
      <span className="micro-label">
        {currency}
      </span>
      <p className="text-h1 font-display text-ova-900 mt-2 tracking-tight">
        {amount}
      </p>
      <span className="text-body-sm text-ova-400 mt-1 block">
        {subtitle}
      </span>
    </div>
  );
}
