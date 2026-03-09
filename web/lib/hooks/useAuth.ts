'use client';

import { useState, useEffect, useCallback } from 'react';
import api from '../api/client';
import type { User, AuthTokens } from '../api/types';

export function useAuth() {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  const fetchUser = useCallback(async () => {
    try {
      const token = localStorage.getItem('accessToken');
      if (!token) {
        setLoading(false);
        return;
      }
      const { data } = await api.get<User>('/users/me');
      setUser(data);
      setIsAuthenticated(true);
    } catch {
      setIsAuthenticated(false);
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchUser();
  }, [fetchUser]);

  const login = async (email: string, password: string, totpCode?: string) => {
    const { data } = await api.post<AuthTokens>('/auth/login', { email, password, totpCode });
    localStorage.setItem('accessToken', data.accessToken);
    localStorage.setItem('refreshToken', data.refreshToken);
    localStorage.setItem('userId', data.userId);
    setIsAuthenticated(true);
    await fetchUser();
    return data;
  };

  const signup = async (email: string, phone: string, password: string, region: string) => {
    const { data } = await api.post<AuthTokens>('/auth/signup', { email, phone, password, region });
    localStorage.setItem('accessToken', data.accessToken);
    localStorage.setItem('refreshToken', data.refreshToken);
    localStorage.setItem('userId', data.userId);
    setIsAuthenticated(true);
    await fetchUser();
    return data;
  };

  const logout = async () => {
    try {
      await api.post('/auth/logout');
    } catch {
      // Ignore backend errors — we're clearing local state regardless
    }
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('userId');
    setUser(null);
    setIsAuthenticated(false);
    window.dispatchEvent(new Event('auth-logout'));
  };

  return { user, loading, isAuthenticated, login, signup, logout, refetch: fetchUser };
}
