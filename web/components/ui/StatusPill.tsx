import { clsx } from "clsx";

type StatusPillVariant = "success" | "warning" | "error" | "info" | "neutral";

interface StatusPillProps {
  variant: StatusPillVariant;
  children: React.ReactNode;
  className?: string;
  dot?: boolean;
}

const variantStyles: Record<StatusPillVariant, string> = {
  success: "bg-ari-green/10 text-ari-green",
  warning: "bg-ari-amber/10 text-ari-amber",
  error: "bg-ari-red/10 text-ari-red",
  info: "bg-ari-900/5 text-ari-700",
  neutral: "bg-ari-100 text-ari-500",
};

const dotColors: Record<StatusPillVariant, string> = {
  success: "bg-ari-green",
  warning: "bg-ari-amber",
  error: "bg-ari-red",
  info: "bg-ari-700",
  neutral: "bg-ari-400",
};

export default function StatusPill({ variant, children, className, dot }: StatusPillProps) {
  return (
    <span
      className={clsx(
        "inline-flex items-center gap-1.5 text-caption font-medium rounded-full px-2.5 py-1",
        variantStyles[variant],
        className,
      )}
    >
      {dot && <span className={clsx("w-1.5 h-1.5 rounded-full", dotColors[variant])} />}
      {children}
    </span>
  );
}
