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
          <label className="block text-body-sm font-medium text-ova-700 mb-3">
            {label}
          </label>
        )}
        <input
          ref={ref}
          className={clsx(
            "w-full h-12 px-4 bg-white border rounded-xl text-ova-900 placeholder:text-ova-500 transition-all duration-base ease-out",
            "focus:outline-none focus:border-ova-blue focus:ring-2 focus:ring-ova-blue/20",
            error
              ? "border-ova-red ring-2 ring-ova-red/20"
              : "border-ova-300",
            props.disabled && "bg-ova-100 text-ova-500 cursor-not-allowed",
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
