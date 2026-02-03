import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import './globals.css';
import AdminSidebar from '../components/AdminSidebar';

const inter = Inter({ subsets: ['latin'] });

export const metadata: Metadata = {
  title: 'Ova Admin Console',
  description: 'Internal admin tool for Ova platform',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className={inter.className}>
        <div className="min-h-screen bg-gray-50 flex">
          <AdminSidebar />
          <main className="flex-1 ml-60 p-8">{children}</main>
        </div>
      </body>
    </html>
  );
}
