'use client';

import { clsx } from 'clsx';

function getStrength(password: string): { level: 0 | 1 | 2 | 3; label: string } {
  if (password.length < 8) return { level: 0, label: 'Too short' };
  let score = 0;
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score++;
  if (/\d/.test(password)) score++;
  if (/[^a-zA-Z0-9]/.test(password)) score++;
  if (password.length >= 12) score++;
  if (score <= 1) return { level: 1, label: 'Weak' };
  if (score <= 2) return { level: 2, label: 'Medium' };
  return { level: 3, label: 'Strong' };
}

const colors = ['bg-ova-red', 'bg-ova-red', 'bg-ova-amber', 'bg-ova-green'];
const textColors = ['text-ova-red', 'text-ova-red', 'text-ova-amber', 'text-ova-green'];

export default function PasswordStrength({ password }: { password: string }) {
  const { level, label } = getStrength(password);

  return (
    <div className="mt-2">
      <div className="flex gap-1">
        {[0, 1, 2].map((i) => (
          <div
            key={i}
            className={clsx(
              'h-1 flex-1 rounded-full transition-colors duration-fast',
              i < level ? colors[level] : 'bg-ova-200'
            )}
          />
        ))}
      </div>
      <p className={clsx('text-caption mt-1', textColors[level])}>{label}</p>
    </div>
  );
}
