'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useAuth } from '../../../lib/hooks/useAuth';
import Input from '../../../components/ui/Input';
import Button from '../../../components/ui/Button';
import { Shield } from 'lucide-react';

export default function LoginPage() {
  const router = useRouter();
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [totpCode, setTotpCode] = useState('');
  const [needs2FA, setNeeds2FA] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      await login(email, password, totpCode || undefined);
      router.push('/home');
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string; requires2FA?: boolean } } };
      if (axiosErr.response?.data?.requires2FA) {
        setNeeds2FA(true);
        setLoading(false);
        return;
      }
      setError(axiosErr.response?.data?.message || 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-ova-50 flex items-center justify-center px-4">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-10">
          <Link href="/" className="ova-logo text-4xl" aria-label="Ova home">
            ova
          </Link>
          <p className="mt-3 text-body-sm text-ova-500">Sign in to your account</p>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-5">
          {error && (
            <div className="p-3 bg-ova-red-light border border-ova-red/20 rounded-xl text-body-sm text-ova-red">
              {error}
            </div>
          )}

          <Input
            label="Email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@example.com"
            required
          />

          <Input
            label="Password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Enter your password"
            required
          />

          {/* 2FA: only shown when server says it's required */}
          {needs2FA && (
            <Input
              label="2FA Code"
              type="text"
              value={totpCode}
              onChange={(e) => setTotpCode(e.target.value)}
              placeholder="6-digit code"
              autoFocus
            />
          )}

          <Button type="submit" fullWidth loading={loading}>
            {needs2FA ? 'Verify & sign in' : 'Sign in'}
          </Button>
        </form>

        {/* Links */}
        <div className="mt-6 text-center">
          <Link href="/forgot-password" className="text-body-sm text-ova-blue hover:underline">
            Forgot your password?
          </Link>
        </div>

        <p className="mt-4 text-center text-body-sm text-ova-500">
          Don&apos;t have an account?{' '}
          <Link href="/signup" className="text-ova-blue font-medium hover:underline">
            Sign up
          </Link>
        </p>

        {/* Trust signal */}
        <div className="mt-8 flex items-center justify-center gap-2 text-caption text-ova-400">
          <Shield size={14} strokeWidth={1.5} />
          <span>Secured with bank-grade encryption</span>
        </div>
      </div>
    </div>
  );
}
