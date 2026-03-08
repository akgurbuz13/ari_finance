'use client';

import { useState, Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { motion, AnimatePresence } from 'framer-motion';
import { ShieldCheck, CheckCircle2 } from 'lucide-react';
import api from '../../../lib/api/client';
import Input from '../../../components/ui/Input';
import Button from '../../../components/ui/Button';
import Skeleton from '../../../components/ui/Skeleton';
import PasswordStrength from '../../../components/ui/PasswordStrength';

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
      <div className="min-h-screen bg-ari-50 flex items-center justify-center px-4">
        <div className="w-full max-w-md">
          {/* Logo */}
          <div className="text-center mb-10">
            <Link href="/" className="ari-logo text-4xl" aria-label="ARI home">
              ARI
            </Link>
          </div>

          {/* Card shell */}
          <div className="bg-white border border-ari-200 rounded-2xl shadow-card p-8 sm:p-10">
            <div className="p-4 bg-ari-red-light border border-ari-red/20 rounded-xl text-body-sm text-ari-red mb-6" role="alert">
              Invalid or missing reset token. Please request a new password reset link.
            </div>
            <div className="text-center">
              <Link href="/forgot-password" className="text-body-sm text-ari-blue font-medium hover:underline">
                Request a new reset link
              </Link>
            </div>
          </div>

          {/* Trust signal */}
          <div className="mt-8 flex items-center justify-center gap-2 text-caption text-ari-400">
            <ShieldCheck size={14} strokeWidth={1.5} />
            <span>Encrypted end-to-end · Identity verified</span>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-ari-50 flex items-center justify-center px-4">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-10">
          <Link href="/" className="ari-logo text-4xl" aria-label="ARI home">
            ARI
          </Link>
          <p className="mt-3 text-body-sm text-ari-500">Set a new password</p>
        </div>

        {/* Card shell */}
        <div className="bg-white border border-ari-200 rounded-2xl shadow-card p-8 sm:p-10">
          <AnimatePresence mode="wait">
            {success ? (
              <motion.div
                key="success"
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.3 }}
                className="space-y-6"
              >
                <div className="text-center">
                  <motion.div
                    initial={{ scale: 0, opacity: 0 }}
                    animate={{ scale: 1, opacity: 1 }}
                    transition={{ type: 'spring', stiffness: 200, damping: 15, delay: 0.1 }}
                    className="flex h-14 w-14 items-center justify-center rounded-full bg-ari-green-light mx-auto mb-4"
                  >
                    <CheckCircle2 size={28} strokeWidth={1.5} className="text-ari-green" />
                  </motion.div>
                  <h2 className="text-h3 text-ari-900">Password reset successful</h2>
                  <p className="mt-2 text-body-sm text-ari-500">
                    Your password has been reset successfully. You can now sign in with your new password.
                  </p>
                </div>
                <div className="text-center">
                  <Link href="/login" className="text-body-sm text-ari-blue font-medium hover:underline">
                    Go to sign in
                  </Link>
                </div>
              </motion.div>
            ) : (
              <motion.div
                key="form"
                initial={{ opacity: 1 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.2 }}
              >
                <form onSubmit={handleSubmit} className="space-y-5">
                  <AnimatePresence>
                    {error && (
                      <motion.div
                        initial={{ opacity: 0, y: -8 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: -8 }}
                        className="p-3 bg-ari-red-light border border-ari-red/20 rounded-xl text-body-sm text-ari-red"
                        role="alert"
                      >
                        {error}
                      </motion.div>
                    )}
                  </AnimatePresence>

                  <div>
                    <Input
                      label="New Password"
                      type="password"
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                      placeholder="Enter your new password"
                      required
                    />
                    <p className="text-caption text-ari-400 mt-1">Must be at least 8 characters</p>
                    {newPassword && <PasswordStrength password={newPassword} />}
                  </div>

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

                <div className="border-t border-ari-200 mt-6 pt-6">
                  <p className="text-center text-body-sm text-ari-500">
                    Remember your password?{' '}
                    <Link href="/login" className="text-ari-blue font-medium hover:underline">
                      Sign in
                    </Link>
                  </p>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* Trust signal */}
        <div className="mt-8 flex items-center justify-center gap-2 text-caption text-ari-400">
          <ShieldCheck size={14} strokeWidth={1.5} />
          <span>Encrypted end-to-end · Identity verified</span>
        </div>
      </div>
    </div>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen bg-ari-50 flex items-center justify-center px-4">
          <div className="w-full max-w-md space-y-6">
            <div className="text-center">
              <Skeleton variant="text" className="w-16 h-10 mx-auto" />
              <Skeleton variant="text" className="w-48 h-5 mx-auto mt-3" />
            </div>
            <div className="bg-white border border-ari-200 rounded-2xl shadow-card p-8 sm:p-10 space-y-5">
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
