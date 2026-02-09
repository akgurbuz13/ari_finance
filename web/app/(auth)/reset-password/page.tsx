'use client';

import { useState, Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { Shield } from 'lucide-react';
import api from '../../../lib/api/client';
import Input from '../../../components/ui/Input';
import Button from '../../../components/ui/Button';
import Skeleton from '../../../components/ui/Skeleton';

function ResetPasswordForm() {
  const searchParams = useSearchParams();
  const token = searchParams.get('token');

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (newPassword.length < 8) {
      setError('Password must be at least 8 characters long.');
      return;
    }

    if (newPassword !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    if (!token) {
      setError('Invalid or missing reset token. Please request a new reset link.');
      return;
    }

    setLoading(true);

    try {
      await api.post('/auth/reset-password', { token, newPassword });
      setSuccess(true);
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      setError(
        axiosErr.response?.data?.message ||
          'Failed to reset password. The link may have expired. Please request a new one.'
      );
    } finally {
      setLoading(false);
    }
  };

  if (!token) {
    return (
      <div className="min-h-screen bg-ova-50 flex items-center justify-center px-4">
        <div className="w-full max-w-md">
          {/* Logo */}
          <div className="text-center mb-10">
            <Link href="/" className="ova-logo text-4xl" aria-label="Ova home">
              ova
            </Link>
          </div>
          <div className="p-4 bg-ova-red-light border border-ova-red/20 rounded-xl text-body-sm text-ova-red mb-6">
            Invalid or missing reset token. Please request a new password reset link.
          </div>
          <div className="text-center">
            <Link href="/forgot-password" className="text-body-sm text-ova-blue font-medium hover:underline">
              Request a new reset link
            </Link>
          </div>

          {/* Trust signal */}
          <div className="mt-8 flex items-center justify-center gap-2 text-caption text-ova-400">
            <Shield size={14} strokeWidth={1.5} />
            <span>Secured with bank-grade encryption</span>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-ova-50 flex items-center justify-center px-4">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-10">
          <Link href="/" className="ova-logo text-4xl" aria-label="Ova home">
            ova
          </Link>
          <p className="mt-3 text-body-sm text-ova-500">Set a new password</p>
        </div>

        {success ? (
          <div className="space-y-6">
            <div className="p-4 bg-ova-green-light border border-ova-green/20 rounded-xl text-body-sm text-ova-green">
              Your password has been reset successfully. You can now sign in with your new password.
            </div>
            <div className="text-center">
              <Link href="/login" className="text-body-sm text-ova-blue font-medium hover:underline">
                Go to sign in
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

              <Input
                label="New Password"
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder="Enter your new password"
                required
              />

              <Input
                label="Confirm Password"
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="Confirm your new password"
                required
              />

              <Button type="submit" fullWidth loading={loading} disabled={loading}>
                {loading ? 'Resetting...' : 'Reset password'}
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

export default function ResetPasswordPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen bg-ova-50 flex items-center justify-center px-4">
          <div className="w-full max-w-md space-y-6">
            <div className="text-center">
              <Skeleton variant="text" className="w-16 h-10 mx-auto" />
              <Skeleton variant="text" className="w-48 h-5 mx-auto mt-3" />
            </div>
            <div className="space-y-5">
              <Skeleton variant="rectangular" className="w-full h-12 rounded-xl" />
              <Skeleton variant="rectangular" className="w-full h-12 rounded-xl" />
              <Skeleton variant="rectangular" className="w-full h-12 rounded-xl" />
            </div>
          </div>
        </div>
      }
    >
      <ResetPasswordForm />
    </Suspense>
  );
}
