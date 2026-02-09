'use client';

import { useRouter } from 'next/navigation';
import { useAuth } from '../../lib/hooks/useAuth';

export default function Header() {
  const { user, logout } = useAuth();
  const router = useRouter();

  const handleLogout = async () => {
    await logout();
    router.push('/login');
  };

  const initials = user
    ? `${(user.firstName || user.email)?.[0] || ''}`.toUpperCase()
    : '?';

  return (
    <header className="fixed top-0 left-60 right-0 z-10 flex h-16 items-center justify-end border-b border-ova-200 bg-white px-6">
      <div className="flex items-center gap-3">
        {user && (
          <span className="text-body-sm text-ova-700">
            {user.firstName || user.email}
          </span>
        )}
        <button
          className="flex h-9 w-9 items-center justify-center rounded-full bg-ova-navy text-caption font-medium text-white"
          title={user?.email || 'User'}
        >
          {initials}
        </button>
        <button
          onClick={handleLogout}
          className="text-body-sm text-ova-500 hover:text-ova-900 transition-colors duration-fast"
        >
          Logout
        </button>
      </div>
    </header>
  );
}
