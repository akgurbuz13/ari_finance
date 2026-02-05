'use client';

import { useState } from 'react';
import Link from 'next/link';
import api from '../../../lib/api/client';
import Input from '../../../components/ui/Input';
import Button from '../../../components/ui/Button';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      await api.post('/auth/forgot-password', { email });
      setSubmitted(true);
    } catch {
      // Always show success message to prevent email enumeration
      setSubmitted(true);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-white flex items-center justify-center px-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-10">
          <Link href="/" className="text-4xl font-bold text-black tracking-tight">
            Ova
          </Link>
          <p className="mt-3 text-gray-500">Reset your password</p>
        </div>

        {submitted ? (
          <div className="space-y-6">
            <div className="p-4 bg-gray-50 border border-gray-200 rounded-lg text-sm text-gray-700">
              If an account exists with this email, we&apos;ve sent a reset link. Please check your
              inbox and spam folder.
            </div>
            <div className="text-center">
              <Link href="/login" className="text-sm text-black font-medium hover:underline">
                Back to sign in
              </Link>
            </div>
          </div>
        ) : (
          <>
            <form onSubmit={handleSubmit} className="space-y-5">
              {error && (
                <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-600">
                  {error}
                </div>
              )}

              <p className="text-sm text-gray-600">
                Enter the email address associated with your account and we&apos;ll send you a link
                to reset your password.
              </p>

              <Input
                label="Email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
                required
              />

              <Button type="submit" className="w-full" loading={loading} disabled={loading}>
                {loading ? 'Sending...' : 'Send reset link'}
              </Button>
            </form>

            <p className="mt-8 text-center text-sm text-gray-500">
              Remember your password?{' '}
              <Link href="/login" className="text-black font-medium hover:underline">
                Sign in
              </Link>
            </p>
          </>
        )}
      </div>
    </div>
  );
}
