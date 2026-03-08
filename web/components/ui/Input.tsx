import { InputHTMLAttributes, forwardRef } from "react";
import { clsx } from "clsx";

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
}

const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, className, ...props }, ref) => {
    return (
      <div className="w-full">
        {label && (
          <label className="block text-body-sm font-medium text-ari-700 mb-2">
            {label}
          </label>
        )}
        <input
          ref={ref}
          className={clsx(
            "w-full h-11 px-4 bg-ari-50 border rounded-xl text-ari-900 placeholder:text-ari-400 transition-all duration-base ease-out",
            "focus:outline-none focus:bg-white focus:border-ari-900 focus:ring-1 focus:ring-ari-900/10",
            error
              ? "border-ari-red bg-ari-red-light/30 ring-1 ring-ari-red/20"
              : "border-ari-200",
            props.disabled && "bg-ari-100 text-ari-400 cursor-not-allowed",
            className,
          )}
          {...props}
        />
        {error && (
          <p className="mt-1.5 text-body-sm text-ari-red">{error}</p>
        )}
      </div>
    );
  },
);

Input.displayName = "Input";

export default Input;
