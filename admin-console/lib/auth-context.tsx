'use client';

import { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react';
import axios from 'axios';

const AUTH_API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

interface AdminUserInfo {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  role: string;
}

interface AuthContextType {
  user: AdminUserInfo | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string, totpCode?: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  isAuthenticated: false,
  isLoading: true,
  login: async () => {},
  logout: () => {},
});

export function useAdminAuth() {
  return useContext(AuthContext);
}

export function AdminAuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AdminUserInfo | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const fetchUser = useCallback(async () => {
    const token = typeof window !== 'undefined' ? localStorage.getItem('ari_admin_token') : null;
    if (!token) {
      setIsLoading(false);
      return;
    }

    try {
      const { data } = await axios.get(`${AUTH_API_URL}/api/v1/users/me`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      setUser({
        id: data.id,
        email: data.email,
        firstName: data.firstName,
        lastName: data.lastName,
        role: data.role || 'admin',
      });
    } catch {
      localStorage.removeItem('ari_admin_token');
      setUser(null);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchUser();
  }, [fetchUser]);

  const login = async (email: string, password: string, totpCode?: string) => {
    const payload: Record<string, string> = { email, password };
    if (totpCode) payload.totpCode = totpCode;

    const { data } = await axios.post(`${AUTH_API_URL}/api/v1/auth/login`, payload);

    localStorage.setItem('ari_admin_token', data.accessToken);

    const profileRes = await axios.get(`${AUTH_API_URL}/api/v1/users/me`, {
      headers: { Authorization: `Bearer ${data.accessToken}` },
    });

    setUser({
      id: profileRes.data.id,
      email: profileRes.data.email,
      firstName: profileRes.data.firstName,
      lastName: profileRes.data.lastName,
      role: profileRes.data.role || 'admin',
    });
  };

  const logout = () => {
    localStorage.removeItem('ari_admin_token');
    setUser(null);
    if (typeof window !== 'undefined') {
      window.location.href = '/login';
    }
  };

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, isLoading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}
