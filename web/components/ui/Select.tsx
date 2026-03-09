'use client';

import { useState, useRef, useEffect } from 'react';
import { ChevronDown, Check } from 'lucide-react';
import { clsx } from 'clsx';

interface SelectOption {
  value: string;
  label: string;
}

interface SelectProps {
  label?: string;
  placeholder?: string;
  options: SelectOption[];
  value: string;
  onChange: (value: string) => void;
  error?: string;
  disabled?: boolean;
}

export default function Select({
  label,
  placeholder = 'Select...',
  options,
  value,
  onChange,
  error,
  disabled,
}: SelectProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const selected = options.find((o) => o.value === value);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  return (
    <div className="w-full" ref={ref}>
      {label && (
        <label className="block text-body-sm font-medium text-ari-700 mb-2">
          {label}
        </label>
      )}
      <div className="relative">
        <button
          type="button"
          onClick={() => !disabled && setOpen(!open)}
          className={clsx(
            'w-full h-11 px-4 bg-ari-50 border rounded-xl text-left transition-all duration-base ease-out flex items-center justify-between',
            'focus:outline-none focus:bg-white focus:border-ari-900 focus:ring-1 focus:ring-ari-900/10',
            error
              ? 'border-ari-red bg-ari-red-light/30 ring-1 ring-ari-red/20'
              : open
                ? 'border-ari-900 bg-white ring-1 ring-ari-900/10'
                : 'border-ari-200',
            disabled && 'bg-ari-100 text-ari-400 cursor-not-allowed',
          )}
        >
          <span className={clsx('text-body-sm truncate', selected ? 'text-ari-900' : 'text-ari-400')}>
            {selected ? selected.label : placeholder}
          </span>
          <ChevronDown
            size={16}
            className={clsx(
              'text-ari-400 shrink-0 ml-2 transition-transform duration-fast',
              open && 'rotate-180',
            )}
          />
        </button>

        {open && (
          <div className="absolute z-50 mt-1.5 w-full bg-white border border-ari-200/80 rounded-xl shadow-lg shadow-ari-900/8 py-1.5 max-h-60 overflow-auto animate-fade-in">
            {options.map((option) => (
              <button
                key={option.value}
                type="button"
                onClick={() => {
                  onChange(option.value);
                  setOpen(false);
                }}
                className={clsx(
                  'w-full px-4 py-2.5 text-left text-body-sm flex items-center justify-between transition-colors duration-fast',
                  option.value === value
                    ? 'text-ari-900 bg-ari-50 font-medium'
                    : 'text-ari-600 hover:bg-ari-50 hover:text-ari-900',
                )}
              >
                {option.label}
                {option.value === value && (
                  <Check size={14} className="text-ari-navy shrink-0" />
                )}
              </button>
            ))}
          </div>
        )}
      </div>
      {error && (
        <p className="mt-1.5 text-body-sm text-ari-red">{error}</p>
      )}
    </div>
  );
}
