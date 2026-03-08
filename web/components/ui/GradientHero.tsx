import { ReactNode } from "react";
import { clsx } from "clsx";

interface GradientHeroProps {
  children: ReactNode;
  className?: string;
}

export default function GradientHero({ children, className }: GradientHeroProps) {
  return (
    <div className={clsx("gradient-card rounded-2xl p-8 text-white", className)}>
      <div className="relative z-10">{children}</div>
    </div>
  );
}
