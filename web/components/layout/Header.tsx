'use client';

import { useState, useEffect } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import Link from 'next/link';
import { Bell, Menu } from 'lucide-react';
import { useAuth } from '../../lib/hooks/useAuth';

const pageTitle: Record<string, string> = {
  '/home': 'Dashboard',
  '/transfer': 'Transfer',
  '/accounts': 'Accounts',
  '/history': 'History',
  '/settings': 'Settings',
};

export default function Header({ onMenuToggle }: { onMenuToggle?: () => void }) {
  const { user, logout } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const [dropdownOpen, setDropdownOpen] = useState(false);

  const title = pageTitle[pathname || ''] || 'Dashboard';

  const handleLogout = async () => {
    await logout();
    router.push('/login');
  };

  const initials = user
    ? `${(user.firstName || user.email)?.[0] || ''}`.toUpperCase()
    : '?';

  useEffect(() => {
    const handleClickOutside = () => {
      if (dropdownOpen) setDropdownOpen(false);
    };
    document.addEventListener('click', handleClickOutside);
    return () => document.removeEventListener('click', handleClickOutside);
  }, [dropdownOpen]);

  return (
    <header className="fixed top-0 left-0 lg:left-60 right-0 z-10 flex h-16 items-center justify-between border-b border-ova-200 bg-white px-4 sm:px-6">
      {/* Left side: hamburger + page title */}
      <div className="flex items-center gap-3">
        {onMenuToggle && (
          <button
            className="lg:hidden flex h-9 w-9 items-center justify-center rounded-full hover:bg-ova-100 transition-colors duration-fast"
            onClick={onMenuToggle}
            aria-label="Open menu"
          >
            <Menu size={20} strokeWidth={1.5} className="text-ova-500" />
          </button>
        )}
        <span className="text-h3 text-ova-900">{title}</span>
      </div>

      {/* Right side */}
      <div className="flex items-center gap-3">
        {/* Notification bell */}
        <button
          className="flex h-9 w-9 items-center justify-center rounded-full hover:bg-ova-100 transition-colors duration-fast"
          aria-label="Notifications"
        >
          <Bell size={18} strokeWidth={1.5} className="text-ova-500" />
        </button>

        {/* Avatar dropdown */}
        <div className="relative">
          <button
            onClick={(e) => {
              e.stopPropagation();
              setDropdownOpen(!dropdownOpen);
            }}
            className="flex h-9 w-9 items-center justify-center rounded-full bg-ova-navy text-caption font-medium text-white"
            title={user?.email || 'User'}
            aria-label="User menu"
          >
            {initials}
          </button>
          {dropdownOpen && (
            <div className="absolute right-0 top-full mt-2 w-48 bg-white border border-ova-200 rounded-xl shadow-md py-1 z-50 animate-scale-in">
              <Link
                href="/settings"
                className="block px-4 py-2.5 text-body-sm text-ova-700 hover:bg-ova-50"
              >
                Settings
              </Link>
              <button
                onClick={handleLogout}
                className="block w-full text-left px-4 py-2.5 text-body-sm text-ova-red hover:bg-ova-red-light"
              >
                Logout
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
