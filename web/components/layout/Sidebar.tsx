'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

const navItems = [
  { href: '/home', label: 'Home', icon: '◉' },
  { href: '/transfer', label: 'Transfer', icon: '↗' },
  { href: '/accounts', label: 'Accounts', icon: '▤' },
  { href: '/history', label: 'History', icon: '☰' },
  { href: '/settings', label: 'Settings', icon: '⚙' },
];

export default function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="w-64 h-screen bg-black text-white flex flex-col fixed left-0 top-0">
      <div className="p-6 border-b border-gray-800">
        <Link href="/home" className="text-2xl font-bold tracking-tight">
          Ova
        </Link>
      </div>

      <nav className="flex-1 py-6">
        {navItems.map((item) => {
          const isActive = pathname?.startsWith(item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              className={`flex items-center gap-3 px-6 py-3 text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-white/10 text-white border-r-2 border-white'
                  : 'text-gray-400 hover:text-white hover:bg-white/5'
              }`}
            >
              <span className="text-lg">{item.icon}</span>
              {item.label}
            </Link>
          );
        })}
      </nav>

      <div className="p-6 border-t border-gray-800">
        <p className="text-xs text-gray-500">Ova Platform v0.1</p>
      </div>
    </aside>
  );
}
