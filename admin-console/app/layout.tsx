import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import './globals.css';
import { AdminAuthProvider } from '../lib/auth-context';
import AdminShell from '../components/AdminShell';

const inter = Inter({ subsets: ['latin'] });

export const metadata: Metadata = {
  title: 'ARI Admin Console',
  description: 'Internal admin tool for ARI platform',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className={inter.className}>
        <AdminAuthProvider>
          <AdminShell>{children}</AdminShell>
        </AdminAuthProvider>
      </body>
    </html>
  );
}
