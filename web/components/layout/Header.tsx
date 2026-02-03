'use client';

import Link from 'next/link';
import { useAuth } from '../../lib/hooks/useAuth';

export default function Header() {
  const { user, logout } = useAuth();

  return (
    <header className="h-16 bg-white border-b border-gray-200 flex items-center justify-between px-8 fixed top-0 left-64 right-0 z-10">
      <div />
      <div className="flex items-center gap-4">
        {user && (
          <span className="text-sm text-gray-600">
            {user.firstName || user.email}
          </span>
        )}
        <button
          onClick={logout}
          className="text-sm text-gray-500 hover:text-black transition-colors"
        >
          Logout
        </button>
      </div>
    </header>
  );
}
