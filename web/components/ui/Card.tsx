import { ReactNode } from 'react';

interface CardProps {
  children: ReactNode;
  header?: string;
  className?: string;
}

export default function Card({ children, header, className = '' }: CardProps) {
  return (
    <div className={`bg-white border border-gray-200 rounded-xl ${className}`}>
      {header && (
        <div className="px-6 py-4 border-b border-gray-100">
          <h3 className="text-lg font-semibold text-black">{header}</h3>
        </div>
      )}
      <div className="p-6">{children}</div>
    </div>
  );
}
