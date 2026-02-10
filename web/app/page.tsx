'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { motion } from 'framer-motion';
import { clsx } from 'clsx';
import { UserPlus, ShieldCheck, Zap, TrendingUp, Receipt, Lock, Menu, X } from 'lucide-react';
import FxCalculator from '../components/ui/FxCalculator';
import AnimatedCounter from '../components/ui/AnimatedCounter';

export default function LandingPage() {
  const [scrolled, setScrolled] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  useEffect(() => {
    const handleScroll = () => setScrolled(window.scrollY > 10);
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return (
    <div className="min-h-screen bg-ova-white">
      {/* ──── NAV ──── */}
      <nav className={clsx(
        "fixed top-0 left-0 right-0 z-50 transition-all duration-base",
        scrolled
          ? "bg-[var(--glass-bg)] backdrop-blur-sm border-b border-[var(--glass-border)] shadow-sm"
          : "bg-white border-b border-ova-200"
      )}>
        <div className="mx-auto flex h-16 max-w-landing items-center justify-between px-6">
          <Link href="/" className="ova-logo text-2xl" aria-label="Ova home">
            ova
          </Link>

          {/* Center nav links (desktop) */}
          <div className="hidden md:flex items-center gap-6">
            <a href="#features" className="text-body-sm font-medium text-ova-700 hover:text-ova-900 transition-colors duration-fast">Features</a>
            <a href="#how-it-works" className="text-body-sm font-medium text-ova-700 hover:text-ova-900 transition-colors duration-fast">How it works</a>
            <a href="#fees" className="text-body-sm font-medium text-ova-700 hover:text-ova-900 transition-colors duration-fast">Fees</a>
          </div>

          {/* Right side (desktop) */}
          <div className="hidden md:flex items-center gap-4">
            <Link
              href="/login"
              className="text-body-sm font-medium text-ova-700 transition-colors duration-fast hover:text-ova-900"
            >
              Sign in
            </Link>
            <Link
              href="/signup"
              className="inline-flex h-10 items-center rounded-xl bg-ova-navy px-5 text-body-sm font-medium text-white transition-all duration-base hover:bg-ova-navy-light hover:shadow-sm"
            >
              Open an account
            </Link>
          </div>

          {/* Mobile hamburger */}
          <button
            className="md:hidden p-2 text-ova-700 hover:text-ova-900 transition-colors duration-fast"
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            aria-label={mobileMenuOpen ? 'Close menu' : 'Open menu'}
          >
            {mobileMenuOpen ? <X size={24} /> : <Menu size={24} />}
          </button>
        </div>

        {/* Mobile menu panel */}
        {mobileMenuOpen && (
          <div className="md:hidden border-t border-ova-200 bg-white px-6 py-4 space-y-3 animate-fade-in">
            <a href="#features" onClick={() => setMobileMenuOpen(false)} className="block text-body-sm font-medium text-ova-700 py-2">Features</a>
            <a href="#how-it-works" onClick={() => setMobileMenuOpen(false)} className="block text-body-sm font-medium text-ova-700 py-2">How it works</a>
            <a href="#fees" onClick={() => setMobileMenuOpen(false)} className="block text-body-sm font-medium text-ova-700 py-2">Fees</a>
            <div className="border-t border-ova-200 pt-3 flex flex-col gap-3">
              <Link href="/login" className="text-body-sm font-medium text-ova-700">Sign in</Link>
              <Link href="/signup" className="inline-flex h-10 items-center justify-center rounded-xl bg-ova-navy px-5 text-body-sm font-medium text-white">Open an account</Link>
            </div>
          </div>
        )}
      </nav>

      {/* ──── HERO ──── */}
      <section className="bg-white pt-40 pb-20">
        <div className="mx-auto grid max-w-landing items-start gap-16 px-6 lg:grid-cols-[1.1fr_0.9fr]">
          {/* Left column */}
          <div className="max-w-xl">
            <motion.h1
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, delay: 0.1 }}
              className="text-display text-ova-navy text-balance"
            >
              Cross-border transfers between Turkey and Europe.
            </motion.h1>
            <motion.p
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, delay: 0.2 }}
              className="mt-2 text-h2 text-ova-navy font-semibold"
            >
              Instant. Transparent. Secure.
            </motion.p>
            <motion.p
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, delay: 0.3 }}
              className="mt-6 text-body-lg text-ova-700 max-w-md"
            >
              Move money between TRY and EUR with live rates, transparent fees, and real-time settlement tracking.
            </motion.p>
            <motion.div
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, delay: 0.4 }}
              className="mt-8 flex flex-wrap items-center gap-4"
            >
              <Link
                href="/signup"
                className="inline-flex h-12 items-center rounded-xl bg-ova-navy px-6 text-body font-medium text-white transition-all duration-base hover:bg-ova-navy-light hover:shadow-sm active:scale-[0.98]"
              >
                Open an account
              </Link>
              <Link
                href="/login"
                className="text-body-sm font-medium text-ova-blue hover:underline"
              >
                Already have an account? Sign in
              </Link>
            </motion.div>
          </div>

          {/* Right column: FX Calculator */}
          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.3 }}
          >
            <FxCalculator />
          </motion.div>
        </div>
      </section>

      {/* ──── TRUST BAR ──── */}
      <section className="bg-ova-100 py-6">
        <div className="mx-auto flex max-w-landing flex-wrap items-center justify-center gap-8 px-6 text-caption text-ova-500">
          <span className="flex items-center gap-2">
            <TrustShield />
            BDDK Licensed
          </span>
          <span className="flex items-center gap-2">
            <TrustShield />
            PSD2 Authorized
          </span>
          <span className="flex items-center gap-2">
            <TrustShield />
            KVKK Compliant
          </span>
          <span className="flex items-center gap-2">
            <TrustShield />
            GDPR Compliant
          </span>
          <span className="flex items-center gap-2">
            <TrustShield />
            MASAK Compliant
          </span>
          <span className="flex items-center gap-2">
            <TrustShield />
            AML Compliant
          </span>
        </div>
      </section>

      {/* ──── HOW IT WORKS ──── */}
      <section id="how-it-works" className="py-20 bg-white">
        <div className="mx-auto max-w-landing px-6">
          <h2 className="text-h1 text-ova-navy text-center">How Ova works</h2>
          <p className="mt-3 text-body-lg text-ova-500 text-center max-w-md mx-auto">
            Three steps to move money between Turkey and Europe
          </p>

          <div className="mt-16 grid grid-cols-1 md:grid-cols-3 gap-8">
            {/* Step 1 */}
            <div className="text-center">
              <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-ova-100">
                <UserPlus size={28} strokeWidth={1.5} className="text-ova-navy" />
              </div>
              <h3 className="mt-5 text-h3 text-ova-900">Create your account</h3>
              <p className="mt-2 text-body-sm text-ova-500">
                Sign up in under 2 minutes with your email and phone number.
              </p>
            </div>

            {/* Step 2 */}
            <div className="text-center">
              <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-ova-100">
                <ShieldCheck size={28} strokeWidth={1.5} className="text-ova-navy" />
              </div>
              <h3 className="mt-5 text-h3 text-ova-900">Verify your identity</h3>
              <p className="mt-2 text-body-sm text-ova-500">
                Quick KYC verification unlocks transfers up to EUR 50,000.
              </p>
            </div>

            {/* Step 3 */}
            <div className="text-center">
              <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-ova-100">
                <Zap size={28} strokeWidth={1.5} className="text-ova-navy" />
              </div>
              <h3 className="mt-5 text-h3 text-ova-900">Send money instantly</h3>
              <p className="mt-2 text-body-sm text-ova-500">
                Live FX rates, transparent fees, and real-time settlement tracking.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* ──── FEATURES ──── */}
      <section id="features" className="py-20">
        <div className="mx-auto max-w-landing px-6">
          <h2 className="text-h1 text-ova-navy text-center">Why choose Ova</h2>
          <div className="mt-12 grid grid-cols-1 md:grid-cols-2 gap-6">
            <FeatureCard
              icon={<TrendingUp size={24} strokeWidth={1.5} />}
              title="Real-time FX rates"
              description="Live market rates with no hidden markups. Rate locks for 30 seconds so you always know what you'll pay."
            />
            <FeatureCard
              icon={<Receipt size={24} strokeWidth={1.5} />}
              title="Transparent fees"
              description="0.25% flat fee on every transfer. No surprises, no tiers, no fine print."
            />
            <FeatureCard
              icon={<Zap size={24} strokeWidth={1.5} />}
              title="Instant settlement"
              description="Cross-border transfers settle in under 2 minutes with real-time tracking at every step."
            />
            <FeatureCard
              icon={<Lock size={24} strokeWidth={1.5} />}
              title="Bank-grade security"
              description="Two-factor authentication, KYC verification, sanctions screening, and end-to-end encryption."
            />
          </div>
        </div>
      </section>

      {/* ──── FX RATE BANNER ──── */}
      <section id="fees" className="py-16 bg-ova-navy">
        <div className="mx-auto max-w-landing px-6 text-center">
          <p className="text-caption text-white/60 uppercase tracking-wide">Live exchange rate</p>
          <p className="mt-3 text-display text-white amount">
            1 EUR = ₺{(1 / 0.0271).toFixed(2)} TRY
          </p>
          <p className="mt-2 text-body-sm text-white/50">
            Updated continuously · 0.25% flat fee on all transfers
          </p>
          <Link
            href="/signup"
            className="mt-6 inline-flex h-12 items-center rounded-xl bg-white px-6 text-body-sm font-medium text-ova-navy transition-all duration-base hover:bg-ova-50 hover:shadow-sm active:scale-[0.98]"
          >
            Start sending money
          </Link>
        </div>
      </section>

      {/* ──── STATS ──── */}
      <section className="py-20 bg-ova-100">
        <div className="mx-auto max-w-landing px-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8 text-center">
            <div>
              <p className="text-display text-ova-navy amount">
                <AnimatedCounter target={50000} suffix="+" />
              </p>
              <p className="mt-2 text-body-sm text-ova-500">Transfers completed</p>
            </div>
            <div>
              <p className="text-display text-ova-navy amount">
                €<AnimatedCounter target={100} suffix="M+" />
              </p>
              <p className="mt-2 text-body-sm text-ova-500">Volume processed</p>
            </div>
            <div>
              <p className="text-display text-ova-navy amount">
                <AnimatedCounter target={2} suffix=" min" />
              </p>
              <p className="mt-2 text-body-sm text-ova-500">Average delivery time</p>
            </div>
          </div>
        </div>
      </section>

      {/* ──── FINAL CTA ──── */}
      <section className="py-20 bg-white">
        <div className="mx-auto max-w-form px-6 text-center">
          <h2 className="text-h1 text-ova-navy">
            Ready to move money without borders?
          </h2>
          <div className="mt-8">
            <Link
              href="/signup"
              className="inline-flex h-12 items-center rounded-xl bg-ova-navy px-8 text-body font-medium text-white transition-all duration-base hover:bg-ova-navy-light hover:shadow-sm active:scale-[0.98]"
            >
              Open an account
            </Link>
          </div>
        </div>
      </section>

      {/* ──── FOOTER ──── */}
      <footer className="border-t border-ova-200 py-12">
        <div className="mx-auto max-w-landing px-6">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-8">
            {/* Brand column */}
            <div className="col-span-2 md:col-span-1">
              <p className="ova-logo text-2xl">ova</p>
              <p className="mt-2 text-body-sm text-ova-500">
                Designed for trust.<br />Built for movement.
              </p>
            </div>

            {/* Product */}
            <div>
              <h4 className="text-caption text-ova-400 uppercase tracking-wide">Product</h4>
              <ul className="mt-3 space-y-2">
                <li><a href="#features" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">Transfers</a></li>
                <li><a href="#fees" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">Fees & Rates</a></li>
                <li><a href="#how-it-works" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">How it works</a></li>
              </ul>
            </div>

            {/* Company */}
            <div>
              <h4 className="text-caption text-ova-400 uppercase tracking-wide">Company</h4>
              <ul className="mt-3 space-y-2">
                <li><a href="#" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">About</a></li>
                <li><a href="#" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">Security</a></li>
                <li><a href="#" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">Contact</a></li>
              </ul>
            </div>

            {/* Legal */}
            <div>
              <h4 className="text-caption text-ova-400 uppercase tracking-wide">Legal</h4>
              <ul className="mt-3 space-y-2">
                <li><a href="#" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">Terms</a></li>
                <li><a href="#" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">Privacy</a></li>
                <li><a href="#" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">Compliance</a></li>
              </ul>
            </div>
          </div>

          <div className="mt-10 border-t border-ova-200 pt-6">
            <p className="text-caption text-ova-400">
              Ova Financial Services, licensed by BDDK (Turkey) and authorized under PSD2 (European Union).
              KVKK and GDPR compliant. All transfers are subject to regulatory compliance checks.
            </p>
            <p className="mt-2 text-caption text-ova-400">
              © {new Date().getFullYear()} Ova Financial Services. All rights reserved.
            </p>
          </div>
        </div>
      </footer>
    </div>
  );
}

/* ──── INLINE HELPER COMPONENTS ──── */

function TrustShield() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="text-ova-400">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
    </svg>
  );
}

function FeatureCard({ icon, title, description }: { icon: React.ReactNode; title: string; description: string }) {
  return (
    <div className="bg-white border border-ova-200 rounded-2xl p-8 shadow-card hover:shadow-card-hover transition-shadow duration-base">
      <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-ova-100 text-ova-navy">
        {icon}
      </div>
      <h3 className="mt-5 text-h3 text-ova-900">{title}</h3>
      <p className="mt-2 text-body-sm text-ova-500 leading-relaxed">{description}</p>
    </div>
  );
}
