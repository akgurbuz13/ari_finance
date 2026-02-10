'use client';

import { useState } from 'react';
import Link from 'next/link';
import { Shield } from 'lucide-react';
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
    <div className="min-h-screen bg-ova-50 flex items-center justify-center px-4">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-10">
          <Link href="/" className="ova-logo text-4xl" aria-label="Ova home">
            ova
          </Link>
          <p className="mt-3 text-body-sm text-ova-500">Reset your password</p>
        </div>

        {submitted ? (
          <div className="space-y-6">
            <div className="p-4 bg-ova-green-light border border-ova-green/20 rounded-xl text-body-sm text-ova-green">
              If an account exists with this email, we&apos;ve sent a reset link. Please check your
              inbox and spam folder.
            </div>
            <div className="text-center">
              <Link href="/login" className="text-body-sm text-ova-blue font-medium hover:underline">
                Back to sign in
              </Link>
            </div>
          </div>
        ) : (
          <>
            <form onSubmit={handleSubmit} className="space-y-5">
              {error && (
                <div className="p-3 bg-ova-red-light border border-ova-red/20 rounded-xl text-body-sm text-ova-red">
                  {error}
                </div>
              )}

              <p className="text-body-sm text-ova-500">
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

              <Button type="submit" fullWidth loading={loading} disabled={loading}>
                {loading ? 'Sending...' : 'Send reset link'}
              </Button>
            </form>

            <p className="mt-8 text-center text-body-sm text-ova-500">
              Remember your password?{' '}
              <Link href="/login" className="text-ova-blue font-medium hover:underline">
                Sign in
              </Link>
            </p>
          </>
        )}

        {/* Trust signal */}
        <div className="mt-8 flex items-center justify-center gap-2 text-caption text-ova-400">
          <Shield size={14} strokeWidth={1.5} />
          <span>Secured with bank-grade encryption</span>
        </div>
      </div>
    </div>
  );
}
