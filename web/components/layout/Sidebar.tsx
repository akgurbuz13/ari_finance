'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Home, ArrowUpRight, Wallet, Clock, Settings, X, Car } from 'lucide-react';
import { clsx } from 'clsx';
import { useAuth } from '../../lib/hooks/useAuth';

const mainNav = [
  { href: '/home' as const, label: 'Home', icon: Home },
  { href: '/transfer' as const, label: 'Transfer', icon: ArrowUpRight },
  { href: '/accounts' as const, label: 'Accounts', icon: Wallet },
  { href: '/vehicles' as const, label: 'Vehicles', icon: Car },
  { href: '/history' as const, label: 'History', icon: Clock },
];

const bottomNav = [
  { href: '/settings' as const, label: 'Settings', icon: Settings },
];

export default function Sidebar({ onClose }: { onClose?: () => void }) {
  const pathname = usePathname();
  const { user } = useAuth();

  const initials = user
    ? `${(user.firstName || user.email)?.[0] || ''}`.toUpperCase()
    : '?';
  const displayName = user?.firstName || user?.email?.split('@')[0] || '';

  return (
    <aside className="flex h-screen w-60 flex-col bg-ova-navy">
      {/* Logo + close button */}
      <div className="px-6 py-6 flex items-center justify-between">
        <Link href="/home" className="ova-logo text-2xl !text-white" aria-label="ARI dashboard">
          ARI
        </Link>
        {onClose && (
          <button
            onClick={onClose}
            className="lg:hidden flex h-8 w-8 items-center justify-center rounded-full hover:bg-white/10 transition-colors duration-fast"
            aria-label="Close menu"
          >
            <X size={18} strokeWidth={1.5} className="text-white/60" />
          </button>
        )}
      </div>

      {/* Main navigation */}
      <nav className="flex-1 py-2">
        {mainNav.map((item) => {
          const isActive = pathname?.startsWith(item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              className={clsx(
                "flex items-center gap-3 px-6 py-3 text-body-sm font-medium transition-colors duration-fast",
                isActive
                  ? "border-l-[3px] border-white text-white bg-white/10"
                  : "border-l-[3px] border-transparent text-white/60 hover:text-white/85 hover:bg-white/5"
              )}
            >
              <item.icon size={20} strokeWidth={1.5} />
              {item.label}
            </Link>
          );
        })}
      </nav>

      {/* User section */}
      <div className="px-6 py-4 border-t border-white/10">
        <div className="flex items-center gap-3">
          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-white/20 text-caption text-white font-medium">
            {initials}
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-body-sm text-white truncate">{displayName}</p>
            <p className="text-caption text-white/40 truncate">{user?.email}</p>
          </div>
        </div>
      </div>

      {/* Bottom navigation */}
      <div className="border-t border-white/10 py-2">
        {bottomNav.map((item) => {
          const isActive = pathname?.startsWith(item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              className={clsx(
                "flex items-center gap-3 px-6 py-3 text-body-sm font-medium transition-colors duration-fast",
                isActive
                  ? "border-l-[3px] border-white text-white bg-white/10"
                  : "border-l-[3px] border-transparent text-white/60 hover:text-white/85 hover:bg-white/5"
              )}
            >
              <item.icon size={20} strokeWidth={1.5} />
              {item.label}
            </Link>
          );
        })}
      </div>
    </aside>
  );
}
