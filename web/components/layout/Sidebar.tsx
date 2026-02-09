'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Home, ArrowUpRight, Wallet, Clock, Settings } from 'lucide-react';
import { clsx } from 'clsx';

const mainNav = [
  { href: '/home' as const, label: 'Home', icon: Home },
  { href: '/transfer' as const, label: 'Transfer', icon: ArrowUpRight },
  { href: '/accounts' as const, label: 'Accounts', icon: Wallet },
  { href: '/history' as const, label: 'History', icon: Clock },
];

const bottomNav = [
  { href: '/settings' as const, label: 'Settings', icon: Settings },
];

export default function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="fixed left-0 top-0 flex h-screen w-60 flex-col bg-ova-navy">
      {/* Logo */}
      <div className="px-6 py-6">
        <Link href="/home" className="ova-logo text-2xl !text-white" aria-label="Ova dashboard">
          ova
        </Link>
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
                  ? "border-l-[3px] border-white text-white"
                  : "border-l-[3px] border-transparent text-white/60 hover:text-white/85"
              )}
            >
              <item.icon size={20} strokeWidth={1.5} />
              {item.label}
            </Link>
          );
        })}
      </nav>

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
                  ? "border-l-[3px] border-white text-white"
                  : "border-l-[3px] border-transparent text-white/60 hover:text-white/85"
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
