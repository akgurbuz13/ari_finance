'use client';

import { useEffect, useState } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import Skeleton from '../ui/Skeleton';

const PUBLIC_ROUTES = ['/', '/login', '/signup', '/kyc', '/forgot-password', '/reset-password'];
const AUTH_REDIRECT_ROUTES = ['/login', '/signup'];

export default function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [isChecking, setIsChecking] = useState(true);
  const [isAuthorized, setIsAuthorized] = useState(false);

  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    const isPublicRoute = PUBLIC_ROUTES.includes(pathname);
    const isAuthRoute = AUTH_REDIRECT_ROUTES.includes(pathname);

    if (!token && !isPublicRoute) {
      // Unauthenticated user trying to access a protected route
      router.replace('/login');
      return;
    }

    if (token && isAuthRoute) {
      // Authenticated user trying to access login/signup — redirect to home
      router.replace('/home');
      return;
    }

    setIsAuthorized(true);
    setIsChecking(false);
  }, [pathname, router]);

  if (isChecking && !isAuthorized) {
    return (
      <div className="min-h-screen bg-ova-50 flex items-center justify-center">
        <div className="flex flex-col items-center gap-4">
          <Skeleton variant="text" className="w-16 h-10" />
          <Skeleton variant="text" className="w-32 h-4" />
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
