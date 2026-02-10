"use client";

import { clsx } from "clsx";
import { ButtonHTMLAttributes, forwardRef } from "react";

type ButtonVariant = "primary" | "secondary" | "ghost" | "danger" | "link";
type ButtonSize = "sm" | "md" | "lg";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  fullWidth?: boolean;
}

const variantStyles: Record<ButtonVariant, string> = {
  primary:
    "bg-ova-navy text-white hover:bg-ova-navy-light hover:shadow-sm active:scale-[0.98] disabled:bg-ova-300 disabled:text-ova-500 disabled:cursor-not-allowed",
  secondary:
    "bg-white text-ova-900 border border-ova-300 hover:bg-ova-50 hover:border-ova-400 active:bg-ova-100 disabled:bg-ova-50 disabled:text-ova-400 disabled:border-ova-200",
  ghost:
    "bg-transparent text-ova-700 hover:bg-ova-100 active:bg-ova-200 disabled:text-ova-400",
  danger:
    "bg-ova-red text-white hover:bg-ova-red/90 active:bg-ova-red/80 disabled:bg-ova-red/40",
  link:
    "bg-transparent text-ova-blue hover:underline font-medium p-0 h-auto",
};

const sizeStyles: Record<ButtonSize, string> = {
  sm: "px-4 h-10 text-body-sm",
  md: "px-6 h-12 text-body-sm",
  lg: "px-8 h-14 text-body",
};

function LoadingSpinner() {
  return (
    <svg
      className="animate-spin -ml-1 mr-2 h-4 w-4"
      xmlns="http://www.w3.org/2000/svg"
      fill="none"
      viewBox="0 0 24 24"
    >
      <circle
        className="opacity-25"
        cx="12"
        cy="12"
        r="10"
        stroke="currentColor"
        strokeWidth="4"
      />
      <path
        className="opacity-75"
        fill="currentColor"
        d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
      />
    </svg>
  );
}

const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      className,
      variant = "primary",
      size = "md",
      loading = false,
      fullWidth = false,
      disabled,
      children,
      ...props
    },
    ref,
  ) => {
    return (
      <button
        ref={ref}
        disabled={disabled || loading}
        className={clsx(
          "inline-flex items-center justify-center font-medium rounded-xl transition-all duration-base ease-out focus:outline-none focus:ring-2 focus:ring-ova-blue focus:ring-offset-2 cursor-pointer disabled:cursor-not-allowed",
          variant !== "link" && sizeStyles[size],
          variantStyles[variant],
          fullWidth && "w-full",
          className,
        )}
        {...props}
      >
        {loading && <LoadingSpinner />}
        {children}
      </button>
    );
  },
);

Button.displayName = "Button";

export default Button;
