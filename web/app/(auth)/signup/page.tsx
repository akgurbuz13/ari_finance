'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useAuth } from '../../../lib/hooks/useAuth';
import Input from '../../../components/ui/Input';
import Button from '../../../components/ui/Button';
import { Shield } from 'lucide-react';

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
          <Link href="/" className="ova-logo text-4xl" aria-label="Ova home">
            ova
          </Link>
          <p className="mt-3 text-body-sm text-ova-500">Create your account</p>
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
            label="Phone"
            type="tel"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            placeholder="+90 5XX XXX XX XX"
            required
          />

          <Input
            label="Password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Min 8 characters"
            minLength={8}
            required
          />

          {/* Region selector with flags */}
          <div>
            <label className="block text-body-sm font-medium text-ova-700 mb-3">
              Region
            </label>
            <select
              value={region}
              onChange={(e) => setRegion(e.target.value)}
              className="w-full h-12 px-4 bg-white border border-ova-300 rounded-xl text-ova-900 transition-all duration-base focus:outline-none focus:border-ova-blue focus:ring-2 focus:ring-ova-blue/20 appearance-none cursor-pointer"
            >
              <option value="TR">{'\u{1F1F9}\u{1F1F7}'} Turkey</option>
              <option value="EU">{'\u{1F1EA}\u{1F1FA}'} European Union</option>
            </select>
          </div>

          <Button type="submit" fullWidth loading={loading}>
            Create account
          </Button>
        </form>

        {/* Links */}
        <p className="mt-8 text-center text-body-sm text-ova-500">
          Already have an account?{' '}
          <Link href="/login" className="text-ova-blue font-medium hover:underline">
            Sign in
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
