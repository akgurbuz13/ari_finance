'use client';

import { usePathname } from 'next/navigation';
import { motion } from 'framer-motion';
import Header from '../../components/layout/Header';
import AuthGuard from '../../components/layout/AuthGuard';

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();

  return (
    <AuthGuard>
      <div className="min-h-screen bg-ova-50">
        <Header />
        <main className="max-w-7xl mx-auto px-4 sm:px-6 pt-24 pb-12">
          <motion.div
            key={pathname}
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.2, ease: 'easeOut' }}
          >
            {children}
          </motion.div>
        </main>
      </div>
    </AuthGuard>
  );
}
