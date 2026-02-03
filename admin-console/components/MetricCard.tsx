"use client";

import clsx from "clsx";

interface MetricCardProps {
  title: string;
  value: string | number;
  trend?: number;
  trendLabel?: string;
  icon?: React.ReactNode;
  variant?: "default" | "accent" | "success" | "warning" | "danger";
}

export default function MetricCard({
  title,
  value,
  trend,
  trendLabel = "vs last period",
  icon,
  variant = "default",
}: MetricCardProps) {
  const variantStyles = {
    default: "border-admin-border",
    accent: "border-l-4 border-l-admin-accent border-t-admin-border border-r-admin-border border-b-admin-border",
    success: "border-l-4 border-l-admin-success border-t-admin-border border-r-admin-border border-b-admin-border",
    warning: "border-l-4 border-l-admin-warning border-t-admin-border border-r-admin-border border-b-admin-border",
    danger: "border-l-4 border-l-admin-danger border-t-admin-border border-r-admin-border border-b-admin-border",
  };

  const iconBgStyles = {
    default: "bg-gray-100 text-admin-text-secondary",
    accent: "bg-admin-accent-bg text-admin-accent",
    success: "bg-admin-success-light text-admin-success",
    warning: "bg-admin-warning-light text-admin-warning",
    danger: "bg-admin-danger-light text-admin-danger",
  };

  return (
    <div
      className={clsx(
        "admin-card p-6 transition-shadow duration-200 hover:shadow-card-hover",
        variantStyles[variant]
      )}
    >
      <div className="flex items-start justify-between">
        <div className="flex-1">
          <p className="text-sm font-medium text-admin-text-secondary">{title}</p>
          <p className="mt-2 text-3xl font-semibold text-admin-text">{value}</p>

          {trend !== undefined && (
            <div className="mt-3 flex items-center gap-1.5">
              {trend > 0 ? (
                <svg className="w-4 h-4 text-admin-success" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 18 9 11.25l4.306 4.306a11.95 11.95 0 0 1 5.814-5.518l2.74-1.22m0 0-5.94-2.281m5.94 2.28-2.28 5.941" />
                </svg>
              ) : trend < 0 ? (
                <svg className="w-4 h-4 text-admin-danger" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 6 9 12.75l4.286-4.286a11.948 11.948 0 0 1 4.306 6.43l.776 2.898m0 0 3.182-5.511m-3.182 5.51-5.511-3.181" />
                </svg>
              ) : (
                <svg className="w-4 h-4 text-admin-text-muted" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M5 12h14" />
                </svg>
              )}
              <span
                className={clsx(
                  "text-sm font-medium",
                  trend > 0 && "text-admin-success",
                  trend < 0 && "text-admin-danger",
                  trend === 0 && "text-admin-text-muted"
                )}
              >
                {trend > 0 ? "+" : ""}
                {trend}%
              </span>
              <span className="text-xs text-admin-text-muted">{trendLabel}</span>
            </div>
          )}
        </div>

        {icon && (
          <div className={clsx("p-3 rounded-lg shrink-0", iconBgStyles[variant])}>
            {icon}
          </div>
        )}
      </div>
    </div>
  );
}
