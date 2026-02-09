import { clsx } from "clsx";

type StatusPillVariant = "success" | "warning" | "error" | "info" | "neutral";

interface StatusPillProps {
  variant: StatusPillVariant;
  children: React.ReactNode;
  className?: string;
}

const variantStyles: Record<StatusPillVariant, string> = {
  success: "bg-ova-green-light text-ova-green",
  warning: "bg-ova-amber-light text-ova-amber",
  error: "bg-ova-red-light text-ova-red",
  info: "bg-ova-blue-light text-ova-blue",
  neutral: "bg-ova-100 text-ova-700",
};

export default function StatusPill({ variant, children, className }: StatusPillProps) {
  return (
    <span
      className={clsx(
        "inline-flex items-center text-caption font-medium rounded-full px-2.5 py-1",
        variantStyles[variant],
        className,
      )}
    >
      {children}
    </span>
  );
}
