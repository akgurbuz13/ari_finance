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
          <label className="block text-body-sm font-medium text-ova-700 mb-2">
            {label}
          </label>
        )}
        <input
          ref={ref}
          className={clsx(
            "w-full h-11 px-4 bg-ova-50 border rounded-xl text-ova-900 placeholder:text-ova-400 transition-all duration-base ease-out",
            "focus:outline-none focus:bg-white focus:border-ova-900 focus:ring-1 focus:ring-ova-900/10",
            error
              ? "border-ova-red bg-ova-red-light/30 ring-1 ring-ova-red/20"
              : "border-ova-200",
            props.disabled && "bg-ova-100 text-ova-400 cursor-not-allowed",
            className,
          )}
          {...props}
        />
        {error && (
          <p className="mt-1.5 text-body-sm text-ova-red">{error}</p>
        )}
      </div>
    );
  },
);

Input.displayName = "Input";

export default Input;
