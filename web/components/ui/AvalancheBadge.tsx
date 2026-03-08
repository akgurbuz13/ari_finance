import { clsx } from "clsx";

interface AvalancheBadgeProps {
  className?: string;
  size?: "sm" | "md";
  label?: string;
}

export default function AvalancheBadge({ className, size = "sm", label = "On Avalanche" }: AvalancheBadgeProps) {
  return (
    <span
      className={clsx(
        "inline-flex items-center gap-1.5 font-medium rounded-full border border-ari-200/60",
        size === "sm" ? "text-micro px-2 py-0.5" : "text-caption px-3 py-1",
        "bg-white text-ari-700",
        className,
      )}
    >
      <svg viewBox="0 0 24 24" className={size === "sm" ? "w-3 h-3" : "w-4 h-4"} fill="none">
        <path d="M12 2L2 19.5h6.5L12 13l3.5 6.5H22L12 2z" fill="#E84142" />
      </svg>
      {label}
    </span>
  );
}

export function AvalancheVerified({ txHash, className }: { txHash?: string | null; className?: string }) {
  return (
    <div className={clsx("flex items-center gap-2 p-3 bg-ari-50 rounded-xl border border-ari-200/60", className)}>
      <svg viewBox="0 0 24 24" className="w-5 h-5 shrink-0" fill="none">
        <path d="M12 2L2 19.5h6.5L12 13l3.5 6.5H22L12 2z" fill="#E84142" />
      </svg>
      <div className="min-w-0 flex-1">
        <p className="text-caption font-semibold text-ari-900">Verified on Avalanche</p>
        {txHash && (
          <p className="text-micro text-ari-400 font-mono truncate mt-0.5">{txHash}</p>
        )}
      </div>
      <svg className="w-4 h-4 text-ari-green shrink-0" viewBox="0 0 20 20" fill="currentColor">
        <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
      </svg>
    </div>
  );
}
