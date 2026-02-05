'use client';

import Sidebar from '../../components/layout/Sidebar';
import Header from '../../components/layout/Header';
import AuthGuard from '../../components/layout/AuthGuard';

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <AuthGuard>
      <div className="min-h-screen bg-gray-50">
        <Sidebar />
        <Header />
        <main className="ml-64 pt-16 p-8">{children}</main>
      </div>
    </AuthGuard>
  );
}
