'use client';

import { useState, useEffect } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import Link from 'next/link';
import { Bell, Settings, Menu, X, Home, ArrowUpRight, Wallet, Car, Clock } from 'lucide-react';
import { clsx } from 'clsx';
import { useAuth } from '../../lib/hooks/useAuth';

const navItems = [
  { href: '/home' as const, label: 'Dashboard', icon: Home },
  { href: '/transfer' as const, label: 'Transfer', icon: ArrowUpRight },
  { href: '/accounts' as const, label: 'Accounts', icon: Wallet },
  { href: '/vehicles' as const, label: 'Vehicles', icon: Car },
  { href: '/history' as const, label: 'History', icon: Clock },
];

export default function Header() {
  const { user, logout } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const handleLogout = async () => {
    await logout();
    router.push('/login');
  };

  const initials = user
    ? `${(user.firstName || user.email)?.[0] || ''}`.toUpperCase()
    : '?';

  const displayName = user?.firstName || user?.email?.split('@')[0] || '';

  useEffect(() => {
    const handleClickOutside = () => {
      if (dropdownOpen) setDropdownOpen(false);
    };
    document.addEventListener('click', handleClickOutside);
    return () => document.removeEventListener('click', handleClickOutside);
  }, [dropdownOpen]);

  // Close mobile menu on navigation
  useEffect(() => {
    setMobileMenuOpen(false);
  }, [pathname]);

  return (
    <>
      <header className="fixed top-0 left-0 right-0 z-50 h-16 bg-white/95 backdrop-blur-sm border-b border-ari-200/60">
        <div className="max-w-7xl mx-auto h-full px-4 sm:px-6 flex items-center justify-between">
          {/* Left: Logo */}
          <div className="flex items-center gap-8">
            <Link href="/home" className="ari-logo text-xl !text-ari-navy" aria-label="ARI dashboard">
              ARI
            </Link>

            {/* Desktop nav */}
            <nav className="hidden lg:flex items-center gap-1">
              {navItems.map((item) => {
                const isActive = pathname?.startsWith(item.href);
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    className={clsx(
                      "relative px-3.5 py-2 text-body-sm font-medium rounded-lg transition-colors duration-fast",
                      isActive
                        ? "text-ari-900 bg-ari-100"
                        : "text-ari-500 hover:text-ari-900 hover:bg-ari-50"
                    )}
                  >
                    {item.label}
                  </Link>
                );
              })}
            </nav>
          </div>

          {/* Right: Actions */}
          <div className="flex items-center gap-2">
            {/* Notification bell */}
            <button
              className="flex h-9 w-9 items-center justify-center rounded-lg hover:bg-ari-100 transition-colors duration-fast"
              aria-label="Notifications"
            >
              <Bell size={18} strokeWidth={1.5} className="text-ari-400" />
            </button>

            {/* Settings */}
            <Link
              href="/settings"
              className="hidden sm:flex h-9 w-9 items-center justify-center rounded-lg hover:bg-ari-100 transition-colors duration-fast"
              aria-label="Settings"
            >
              <Settings size={18} strokeWidth={1.5} className="text-ari-400" />
            </Link>

            {/* Divider */}
            <div className="hidden sm:block w-px h-6 bg-ari-200 mx-1" />

            {/* Avatar dropdown */}
            <div className="relative">
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  setDropdownOpen(!dropdownOpen);
                }}
                className="flex items-center gap-2.5 rounded-full pl-1 pr-3 py-1 hover:bg-ari-50 transition-colors duration-fast"
                title={user?.email || 'User'}
                aria-label="User menu"
              >
                <div className="flex h-8 w-8 items-center justify-center rounded-full bg-ari-navy text-micro text-white font-semibold">
                  {initials}
                </div>
                <span className="hidden sm:block text-body-sm font-medium text-ari-700">{displayName}</span>
              </button>
              {dropdownOpen && (
                <div className="absolute right-0 top-full mt-2 w-48 bg-white border border-ari-200/60 rounded-xl shadow-md py-1.5 z-50 animate-scale-in">
                  <div className="px-4 py-2 border-b border-ari-100">
                    <p className="text-body-sm font-medium text-ari-900 truncate">{displayName}</p>
                    <p className="text-caption text-ari-400 truncate">{user?.email}</p>
                  </div>
                  <Link
                    href="/settings"
                    className="block px-4 py-2.5 text-body-sm text-ari-700 hover:bg-ari-50"
                  >
                    Settings
                  </Link>
                  <button
                    onClick={handleLogout}
                    className="block w-full text-left px-4 py-2.5 text-body-sm text-ari-red hover:bg-ari-red-light"
                  >
                    Log out
                  </button>
                </div>
              )}
            </div>

            {/* Mobile menu toggle */}
            <button
              className="lg:hidden flex h-9 w-9 items-center justify-center rounded-lg hover:bg-ari-100 transition-colors duration-fast ml-1"
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              aria-label="Toggle menu"
            >
              {mobileMenuOpen ? (
                <X size={20} strokeWidth={1.5} className="text-ari-500" />
              ) : (
                <Menu size={20} strokeWidth={1.5} className="text-ari-500" />
              )}
            </button>
          </div>
        </div>
      </header>

      {/* Mobile menu */}
      {mobileMenuOpen && (
        <>
          <div
            className="fixed inset-0 bg-black/20 z-40 lg:hidden"
            onClick={() => setMobileMenuOpen(false)}
          />
          <div className="fixed top-16 left-0 right-0 z-40 lg:hidden bg-white border-b border-ari-200 shadow-lg animate-fade-in">
            <nav className="max-w-7xl mx-auto px-4 py-3 space-y-1">
              {navItems.map((item) => {
                const isActive = pathname?.startsWith(item.href);
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    className={clsx(
                      "flex items-center gap-3 px-4 py-3 rounded-xl text-body-sm font-medium transition-colors duration-fast",
                      isActive
                        ? "bg-ari-100 text-ari-900"
                        : "text-ari-500 hover:bg-ari-50 hover:text-ari-900"
                    )}
                  >
                    <item.icon size={20} strokeWidth={1.5} />
                    {item.label}
                  </Link>
                );
              })}
              <Link
                href="/settings"
                className="flex items-center gap-3 px-4 py-3 rounded-xl text-body-sm font-medium text-ari-500 hover:bg-ari-50 hover:text-ari-900 transition-colors duration-fast"
              >
                <Settings size={20} strokeWidth={1.5} />
                Settings
              </Link>
            </nav>
          </div>
        </>
      )}
    </>
  );
}
