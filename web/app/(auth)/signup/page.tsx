'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { motion, AnimatePresence } from 'framer-motion';
import { clsx } from 'clsx';
import { useAuth } from '../../../lib/hooks/useAuth';
import Input from '../../../components/ui/Input';
import Button from '../../../components/ui/Button';
import PasswordStrength from '../../../components/ui/PasswordStrength';
import { ShieldCheck } from 'lucide-react';

export default function SignupPage() {
  const router = useRouter();
  const { signup } = useAuth();
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [region, setRegion] = useState('TR');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      await signup(email, phone, password, region);
      router.push('/home');
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      setError(axiosErr.response?.data?.message || 'Signup failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-ova-50 flex items-center justify-center px-4">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-10">
          <Link href="/" className="ova-logo text-4xl" aria-label="ARI home">
            ARI
          </Link>
          <p className="mt-3 text-body-sm text-ova-500">Create your account</p>
        </div>

        {/* Card shell */}
        <div className="bg-white border border-ova-200 rounded-2xl shadow-card p-8 sm:p-10">
          {/* Form */}
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

            <Input
              label="Email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              required
            />

            <Input
              label="Phone"
              type="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="+90 5XX XXX XX XX"
              required
            />

            <div>
              <Input
                label="Password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Min 8 characters"
                minLength={8}
                required
              />
              {password && <PasswordStrength password={password} />}
            </div>

            {/* Region selector with styled radio buttons */}
            <div>
              <label className="block text-body-sm font-medium text-ova-700 mb-3">Region</label>
              <div className="grid grid-cols-2 gap-3">
                <button
                  type="button"
                  onClick={() => setRegion('TR')}
                  className={clsx(
                    'flex items-center gap-3 p-4 rounded-xl border-2 transition-all duration-fast text-left',
                    region === 'TR'
                      ? 'border-ova-navy bg-ova-50'
                      : 'border-ova-200 hover:border-ova-300'
                  )}
                >
                  <span className="text-2xl">{'\u{1F1F9}\u{1F1F7}'}</span>
                  <div>
                    <p className="text-body-sm font-medium text-ova-900">Turkey</p>
                    <p className="text-caption text-ova-500">TRY accounts</p>
                  </div>
                </button>
                <button
                  type="button"
                  onClick={() => setRegion('EU')}
                  className={clsx(
                    'flex items-center gap-3 p-4 rounded-xl border-2 transition-all duration-fast text-left',
                    region === 'EU'
                      ? 'border-ova-navy bg-ova-50'
                      : 'border-ova-200 hover:border-ova-300'
                  )}
                >
                  <span className="text-2xl">{'\u{1F1EA}\u{1F1FA}'}</span>
                  <div>
                    <p className="text-body-sm font-medium text-ova-900">European Union</p>
                    <p className="text-caption text-ova-500">EUR accounts</p>
                  </div>
                </button>
              </div>
            </div>

            <Button type="submit" fullWidth loading={loading}>
              Create account
            </Button>
          </form>

          {/* Divider + links */}
          <div className="border-t border-ova-200 mt-6 pt-6">
            <p className="text-center text-body-sm text-ova-500">
              Already have an account?{' '}
              <Link href="/login" className="text-ova-blue font-medium hover:underline">
                Sign in
              </Link>
            </p>
          </div>
        </div>

        {/* Trust signal */}
        <div className="mt-8 flex items-center justify-center gap-2 text-caption text-ova-400">
          <ShieldCheck size={14} strokeWidth={1.5} />
          <span>Encrypted end-to-end · Identity verified</span>
        </div>
      </div>
    </div>
  );
}
