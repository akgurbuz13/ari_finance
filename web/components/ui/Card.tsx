import { ReactNode } from "react";
import { clsx } from "clsx";

interface CardProps {
  children: ReactNode;
  header?: string;
  className?: string;
  padding?: "standard" | "generous";
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
        "bg-white border border-ova-200 rounded-2xl shadow-card",
        hover && "hover:shadow-card-hover cursor-pointer transition-shadow duration-fast",
        className,
      )}
    >
      {header && (
        <div className="px-6 py-4 border-b border-ova-100">
          <h3 className="text-h3 text-ova-900">{header}</h3>
        </div>
      )}
      <div className={padding === "generous" ? "p-8" : "p-6"}>{children}</div>
    </div>
  );
}

export function BalanceCard({ currency, amount, subtitle = "Available balance", className }: BalanceCardProps) {
  return (
    <div
      className={clsx(
        "bg-white border border-ova-200 border-l-4 border-l-ova-navy rounded-2xl p-6 shadow-card-hover",
        className,
      )}
    >
      <span className="text-caption uppercase text-ova-500 tracking-wide">
        {currency}
      </span>
      <p className="amount-display text-ova-navy mt-1">
        {amount}
      </p>
      <span className="text-body-sm text-ova-500 mt-1 block">
        {subtitle}
      </span>
    </div>
  );
}
