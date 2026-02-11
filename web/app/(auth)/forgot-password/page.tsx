'use client';

import { useState } from 'react';
import Link from 'next/link';
import { motion, AnimatePresence } from 'framer-motion';
import { ShieldCheck, Mail } from 'lucide-react';
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

        {/* Card shell */}
        <div className="bg-white border border-ova-200 rounded-2xl shadow-card p-8 sm:p-10">
          <AnimatePresence mode="wait">
            {submitted ? (
              <motion.div
                key="success"
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.3 }}
                className="space-y-6"
              >
                <div className="text-center">
                  <div className="flex h-14 w-14 items-center justify-center rounded-full bg-ova-green-light mx-auto mb-4">
                    <Mail size={28} strokeWidth={1.5} className="text-ova-green" />
                  </div>
                  <h2 className="text-h3 text-ova-900">Check your email</h2>
                  <p className="mt-2 text-body-sm text-ova-500">
                    If an account exists with this email, we&apos;ve sent a reset link. Please check your
                    inbox and spam folder.
                  </p>
                </div>
                <div className="text-center">
                  <Link href="/login" className="text-body-sm text-ova-blue font-medium hover:underline">
                    Back to sign in
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
                        className="p-3 bg-ova-red-light border border-ova-red/20 rounded-xl text-body-sm text-ova-red"
                        role="alert"
                      >
                        {error}
                      </motion.div>
                    )}
                  </AnimatePresence>

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

                <div className="border-t border-ova-200 mt-6 pt-6">
                  <p className="text-center text-body-sm text-ova-500">
                    Remember your password?{' '}
                    <Link href="/login" className="text-ova-blue font-medium hover:underline">
                      Sign in
                    </Link>
                  </p>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* Trust signal */}
        <div className="mt-8 flex items-center justify-center gap-2 text-caption text-ova-400">
          <ShieldCheck size={14} strokeWidth={1.5} />
          <span>BDDK regulated · Encrypted end-to-end</span>
        </div>
      </div>
    </div>
  );
}
