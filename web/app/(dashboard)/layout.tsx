'use client';

import { useState } from 'react';
import { usePathname } from 'next/navigation';
import { motion } from 'framer-motion';
import { clsx } from 'clsx';
import Sidebar from '../../components/layout/Sidebar';
import Header from '../../components/layout/Header';
import AuthGuard from '../../components/layout/AuthGuard';

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <AuthGuard>
      <div className="min-h-screen bg-ova-50">
        {/* Mobile overlay */}
        {sidebarOpen && (
          <div
            className="fixed inset-0 bg-black/50 z-40 lg:hidden"
            onClick={() => setSidebarOpen(false)}
          />
        )}

        {/* Sidebar */}
        <div className={clsx(
          "fixed left-0 top-0 h-screen w-60 bg-ova-navy z-50 transition-transform duration-slow lg:translate-x-0",
          sidebarOpen ? "translate-x-0" : "-translate-x-full"
        )}>
          <Sidebar onClose={() => setSidebarOpen(false)} />
        </div>

        <Header onMenuToggle={() => setSidebarOpen(true)} />
        <main className="lg:ml-60 pt-16 p-4 sm:p-6 lg:p-8">
          <motion.div
            key={pathname}
            initial={{ opacity: 0, y: 4 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.15, ease: 'easeOut' }}
          >
            {children}
          </motion.div>
        </main>
      </div>
    </AuthGuard>
  );
}
