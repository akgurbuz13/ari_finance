import { clsx } from "clsx";

type StatusPillVariant = "success" | "warning" | "error" | "info" | "neutral";

interface StatusPillProps {
  variant: StatusPillVariant;
  children: React.ReactNode;
  className?: string;
  dot?: boolean;
}

const variantStyles: Record<StatusPillVariant, string> = {
  success: "bg-ova-green/10 text-ova-green",
  warning: "bg-ova-amber/10 text-ova-amber",
  error: "bg-ova-red/10 text-ova-red",
  info: "bg-ova-900/5 text-ova-700",
  neutral: "bg-ova-100 text-ova-500",
};

const dotColors: Record<StatusPillVariant, string> = {
  success: "bg-ova-green",
  warning: "bg-ova-amber",
  error: "bg-ova-red",
  info: "bg-ova-700",
  neutral: "bg-ova-400",
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
