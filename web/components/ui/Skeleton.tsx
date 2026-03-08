import { clsx } from "clsx";

interface SkeletonProps {
  className?: string;
  variant?: "text" | "circular" | "rectangular";
  width?: string;
  height?: string;
}

export default function Skeleton({
  className,
  variant = "text",
  width,
  height,
}: SkeletonProps) {
  return (
    <div
      className={clsx(
        "skeleton-shimmer",
        variant === "text" && "h-4 rounded",
        variant === "circular" && "rounded-full",
        variant === "rectangular" && "rounded-xl",
        className,
      )}
      style={{ width, height }}
    />
  );
}

export function SkeletonCard({ className }: { className?: string }) {
  return (
    <div className={clsx("bg-white border border-ari-200 rounded-2xl p-6 shadow-card", className)}>
      <Skeleton variant="text" className="w-20 h-3 mb-3" />
      <Skeleton variant="text" className="w-40 h-8 mb-2" />
      <Skeleton variant="text" className="w-28 h-3" />
    </div>
  );
}
