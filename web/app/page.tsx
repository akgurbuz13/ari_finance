'use client';

import { useState, useEffect, useRef } from 'react';
import Link from 'next/link';
import { motion, useInView } from 'framer-motion';
import { clsx } from 'clsx';
import {
  ArrowRight, ArrowLeftRight, Car, Shield, Wallet,
  CheckCircle2, Menu, X, ChevronRight, Zap, Receipt,
  Lock, CreditCard, TrendingUp, Clock,
  Check, XCircle, Smartphone, UserCheck, Send,
} from 'lucide-react';
import FxCalculator from '../components/ui/FxCalculator';
import AnimatedCounter from '../components/ui/AnimatedCounter';

/* ────────────────────────────────────────────────────────────
   Animation helpers
   ──────────────────────────────────────────────────────────── */

const fadeUp = {
  hidden: { opacity: 0, y: 24 },
  visible: (i: number = 0) => ({
    opacity: 1, y: 0,
    transition: { duration: 0.6, delay: i * 0.1, ease: [0.25, 0.46, 0.45, 0.94] },
  }),
};

function AnimatedSection({ children, className }: { children: React.ReactNode; className?: string }) {
  const ref = useRef(null);
  const isInView = useInView(ref, { once: true, margin: '-80px' });
  return (
    <motion.div ref={ref} initial="hidden" animate={isInView ? 'visible' : 'hidden'}
      variants={{ visible: { transition: { staggerChildren: 0.08 } } }} className={className}>
      {children}
    </motion.div>
  );
}

/* ────────────────────────────────────────────────────────────
   Hero — floating product cards
   ──────────────────────────────────────────────────────────── */

function HeroVisual() {
  return (
    <div className="relative w-full h-[500px]">
      {/* Ambient glow */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[450px] h-[450px] rounded-full bg-gradient-radial from-ova-blue/[0.06] to-transparent blur-3xl" />

      {/* Card: Account Balance — top left */}
      <motion.div
        className="absolute top-4 left-4 w-[270px]"
        initial={{ opacity: 0, y: 30, rotate: -2 }}
        animate={{ opacity: 1, y: 0, rotate: -2 }}
        transition={{ duration: 0.7, delay: 0.3, ease: 'easeOut' }}
      >
        <motion.div
          animate={{ y: [-3, 5, -3] }}
          transition={{ duration: 6, repeat: Infinity, ease: 'easeInOut' }}
          className="rounded-2xl bg-white border border-ova-200/80 p-5 shadow-[0_8px_30px_rgba(0,0,0,0.06)]"
        >
          <div className="flex items-center gap-2.5 mb-4">
            <div className="w-8 h-8 rounded-lg bg-ova-navy flex items-center justify-center">
              <Wallet size={15} className="text-white" />
            </div>
            <span className="text-body-sm text-ova-500">TRY Account</span>
          </div>
          <p className="text-[1.75rem] font-semibold text-ova-900 tracking-tight leading-none" style={{ fontVariantNumeric: 'tabular-nums' }}>
            ₺24,350<span className="text-ova-300">.00</span>
          </p>
          <div className="mt-3 flex items-center gap-1.5">
            <TrendingUp size={13} className="text-ova-green" />
            <span className="text-caption text-ova-green font-medium">+₺2,100 this month</span>
          </div>
        </motion.div>
      </motion.div>

      {/* Card: Transfer — top right */}
      <motion.div
        className="absolute top-20 right-4 w-[240px]"
        initial={{ opacity: 0, y: 30, rotate: 3 }}
        animate={{ opacity: 1, y: 0, rotate: 3 }}
        transition={{ duration: 0.7, delay: 0.55, ease: 'easeOut' }}
      >
        <motion.div
          animate={{ y: [4, -4, 4] }}
          transition={{ duration: 5, repeat: Infinity, ease: 'easeInOut', delay: 1 }}
          className="rounded-2xl bg-white border border-ova-200/80 p-5 shadow-[0_8px_30px_rgba(0,0,0,0.06)]"
        >
          <div className="flex items-center gap-2 mb-3">
            <div className="w-6 h-6 rounded-full bg-ova-green-light flex items-center justify-center">
              <Check size={13} className="text-ova-green" />
            </div>
            <span className="text-caption text-ova-green font-medium">Transfer complete</span>
          </div>
          <p className="text-h3 font-semibold text-ova-900">€500.00</p>
          <p className="text-caption text-ova-500 mt-1">Istanbul → Berlin</p>
          <div className="mt-3 flex items-center gap-1.5">
            <Clock size={11} className="text-ova-400" />
            <span className="text-[10px] text-ova-400">Arrived in 47 seconds</span>
          </div>
        </motion.div>
      </motion.div>

      {/* Card: Vehicle Sale — bottom center */}
      <motion.div
        className="absolute bottom-4 left-[15%] w-[290px]"
        initial={{ opacity: 0, y: 30, rotate: 1 }}
        animate={{ opacity: 1, y: 0, rotate: 1 }}
        transition={{ duration: 0.7, delay: 0.8, ease: 'easeOut' }}
      >
        <motion.div
          animate={{ y: [-4, 3, -4] }}
          transition={{ duration: 7, repeat: Infinity, ease: 'easeInOut', delay: 0.5 }}
          className="rounded-2xl bg-white border border-ova-200/80 p-5 shadow-[0_8px_30px_rgba(0,0,0,0.06)]"
        >
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-lg bg-ova-blue-light flex items-center justify-center">
                <Car size={15} className="text-ova-blue" />
              </div>
              <div>
                <p className="text-body-sm font-medium text-ova-900 leading-tight">Honda Civic 2024</p>
                <p className="text-[10px] text-ova-400">34 ABC 456</p>
              </div>
            </div>
            <span className="text-[10px] bg-ova-green-light text-ova-green px-2 py-0.5 rounded-full font-medium">
              Protected
            </span>
          </div>
          <p className="text-h3 font-semibold text-ova-900">₺485,000</p>
          <div className="mt-3 w-full bg-ova-100 rounded-full h-1.5 overflow-hidden">
            <motion.div
              className="bg-ova-blue h-full rounded-full"
              initial={{ width: '0%' }}
              animate={{ width: '75%' }}
              transition={{ duration: 1.5, delay: 1.8, ease: 'easeOut' }}
            />
          </div>
          <div className="mt-1.5 flex justify-between">
            <span className="text-[10px] text-ova-400">Sale in progress</span>
            <span className="text-[10px] text-ova-blue font-medium">Both parties confirmed</span>
          </div>
        </motion.div>
      </motion.div>
    </div>
  );
}

/* ────────────────────────────────────────────────────────────
   Page
   ──────────────────────────────────────────────────────────── */

export default function LandingPage() {
  const [scrolled, setScrolled] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  useEffect(() => {
    const handleScroll = () => setScrolled(window.scrollY > 10);
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return (
    <div className="min-h-screen bg-white">

      {/* ─── NAV ─── */}
      <nav className={clsx(
        'fixed top-0 left-0 right-0 z-50 transition-all duration-200',
        scrolled
          ? 'bg-white/80 backdrop-blur-md border-b border-ova-200/50 shadow-[0_1px_3px_rgba(0,0,0,0.04)]'
          : 'bg-white border-b border-transparent',
      )}>
        <div className="mx-auto flex h-16 max-w-landing items-center justify-between px-6">
          <Link href="/" className="ova-logo text-2xl shrink-0" aria-label="ARI home">ARI</Link>

          {/* Desktop: nav + CTA grouped right */}
          <div className="hidden md:flex items-center">
            <div className="flex items-center gap-7">
              <a href="#products" className="text-body-sm font-medium text-ova-500 hover:text-ova-900 transition-colors">Features</a>
              <a href="#vehicles" className="text-body-sm font-medium text-ova-500 hover:text-ova-900 transition-colors">Vehicle Sales</a>
              <a href="#transfers" className="text-body-sm font-medium text-ova-500 hover:text-ova-900 transition-colors">Transfers</a>
              <a href="#trust" className="text-body-sm font-medium text-ova-500 hover:text-ova-900 transition-colors">Security</a>
            </div>
            <div className="flex items-center gap-3 ml-8 pl-8 border-l border-ova-200">
              <Link href="/login" className="text-body-sm font-medium text-ova-700 hover:text-ova-900 transition-colors">Sign in</Link>
              <Link href="/signup" className="inline-flex h-9 items-center rounded-lg bg-ova-navy px-4 text-body-sm font-medium text-white hover:bg-ova-navy-light transition-colors">
                Open an account
              </Link>
            </div>
          </div>

          <button className="md:hidden p-2 text-ova-700" onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            aria-label={mobileMenuOpen ? 'Close menu' : 'Open menu'}>
            {mobileMenuOpen ? <X size={22} /> : <Menu size={22} />}
          </button>
        </div>

        {mobileMenuOpen && (
          <div className="md:hidden border-t border-ova-200 bg-white px-6 py-4 space-y-3 animate-fade-in">
            <a href="#products" onClick={() => setMobileMenuOpen(false)} className="block text-body-sm font-medium text-ova-700 py-2">Features</a>
            <a href="#vehicles" onClick={() => setMobileMenuOpen(false)} className="block text-body-sm font-medium text-ova-700 py-2">Vehicle Sales</a>
            <a href="#transfers" onClick={() => setMobileMenuOpen(false)} className="block text-body-sm font-medium text-ova-700 py-2">Transfers</a>
            <a href="#trust" onClick={() => setMobileMenuOpen(false)} className="block text-body-sm font-medium text-ova-700 py-2">Security</a>
            <div className="border-t border-ova-200 pt-3 flex flex-col gap-3">
              <Link href="/login" className="text-body-sm font-medium text-ova-700">Sign in</Link>
              <Link href="/signup" className="inline-flex h-10 items-center justify-center rounded-lg bg-ova-navy px-5 text-body-sm font-medium text-white">Open an account</Link>
            </div>
          </div>
        )}
      </nav>

      {/* ─── HERO ─── */}
      <section className="relative pt-32 pb-20 overflow-hidden">
        <div className="absolute inset-0 pointer-events-none">
          <div className="absolute -top-32 right-0 w-[600px] h-[600px] rounded-full bg-gradient-radial from-ova-blue/[0.03] to-transparent" />
          <div className="absolute -bottom-48 -left-24 w-[500px] h-[500px] rounded-full bg-gradient-radial from-ova-navy/[0.02] to-transparent" />
        </div>

        <div className="relative mx-auto max-w-landing px-6">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 items-center">
            {/* Copy */}
            <div className="max-w-xl">
              <motion.h1 initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5, delay: 0.12 }}
                className="text-[2.75rem] md:text-[3.25rem] font-semibold text-ova-navy leading-[1.08] tracking-tight">
                All your finances.
                <br />
                <span className="text-ova-blue">One powerful platform.</span>
              </motion.h1>

              <motion.p initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5, delay: 0.22 }}
                className="mt-6 text-body-lg text-ova-500 max-w-md leading-relaxed">
                Manage your accounts, send money across borders in seconds, and sell your car safely — all from your phone.
              </motion.p>

              <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5, delay: 0.32 }}
                className="mt-8 flex flex-wrap items-center gap-3">
                <Link href="/signup"
                  className="inline-flex h-12 items-center gap-2 rounded-xl bg-ova-navy px-6 text-body font-medium text-white transition-all hover:bg-ova-navy-light hover:shadow-md active:scale-[0.98]">
                  Get started <ArrowRight size={18} />
                </Link>
                <a href="#products"
                  className="inline-flex h-12 items-center gap-2 rounded-xl border border-ova-300 px-6 text-body font-medium text-ova-700 transition-all hover:bg-ova-50 hover:border-ova-400">
                  Explore features
                </a>
              </motion.div>
            </div>

            {/* Floating UI cards */}
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}
              transition={{ duration: 0.6, delay: 0.2 }}
              className="hidden lg:block">
              <HeroVisual />
            </motion.div>
          </div>
        </div>
      </section>

      {/* ─── TRUST BAR ─── */}
      <section className="border-y border-ova-100 bg-ova-50/50 py-4">
        <div className="mx-auto flex max-w-landing flex-wrap items-center justify-center gap-x-8 gap-y-2 px-6 text-caption text-ova-400">
          {['Bank-Grade Encryption', 'Identity Verified', 'GDPR Compliant', 'Protected Transactions', 'Always Available'].map(label => (
            <span key={label} className="flex items-center gap-1.5">
              <Shield size={12} strokeWidth={1.5} className="text-ova-300" />{label}
            </span>
          ))}
        </div>
      </section>

      {/* ─── PRODUCTS ─── */}
      <section id="products" className="py-24 scroll-mt-20">
        <div className="mx-auto max-w-landing px-6">
          <AnimatedSection>
            <motion.div variants={fadeUp} className="text-center mb-16">
              <h2 className="text-h1 text-ova-navy">Everything you need in one place</h2>
              <p className="mt-3 text-body-lg text-ova-500 max-w-lg mx-auto">
                Accounts, transfers, and vehicle sales — designed to work together seamlessly.
              </p>
            </motion.div>
          </AnimatedSection>

          <AnimatedSection className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {[
              {
                icon: CreditCard, color: 'bg-ova-navy', badge: 'Accounts',
                title: 'Multi-Currency Accounts',
                desc: 'Hold TRY and EUR side by side. Track your balance, view transaction history, and manage everything from one dashboard.',
              },
              {
                icon: Send, color: 'bg-ova-blue', badge: 'Transfers',
                title: 'Instant Transfers',
                desc: 'Send money across borders in under 2 minutes. Real-time tracking, transparent fees, competitive exchange rates.',
              },
              {
                icon: Car, color: 'bg-ova-green', badge: 'Vehicles',
                title: 'Protected Vehicle Sales',
                desc: 'Buy or sell a car without the hassle. Payment is held securely until both parties agree, then everything transfers at once.',
              },
            ].map((product, i) => (
              <motion.div key={product.title} variants={fadeUp} custom={i}
                className="group rounded-2xl border border-ova-200 bg-white p-7 hover:shadow-[0_4px_20px_rgba(0,0,0,0.06)] hover:border-ova-300 transition-all duration-200 relative overflow-hidden">
                <div className="absolute top-0 right-0 w-32 h-32 bg-gradient-radial from-ova-blue/[0.03] to-transparent rounded-full -translate-y-1/2 translate-x-1/2 opacity-0 group-hover:opacity-100 transition-opacity" />
                <div className="relative">
                  <span className="text-[10px] font-semibold uppercase tracking-wider text-ova-400">{product.badge}</span>
                  <div className={clsx('flex h-11 w-11 items-center justify-center rounded-xl mt-3', product.color)}>
                    <product.icon size={20} strokeWidth={1.5} className="text-white" />
                  </div>
                  <h3 className="mt-5 text-h3 text-ova-900">{product.title}</h3>
                  <p className="mt-2 text-body-sm text-ova-500 leading-relaxed">{product.desc}</p>
                  <div className="mt-5 flex items-center gap-1.5 text-body-sm font-medium text-ova-blue opacity-0 group-hover:opacity-100 transition-opacity">
                    Learn more <ChevronRight size={15} />
                  </div>
                </div>
              </motion.div>
            ))}
          </AnimatedSection>
        </div>
      </section>

      {/* ─── VEHICLE SALES ─── */}
      <section id="vehicles" className="py-24 bg-ova-50 scroll-mt-20 relative">
        <div className="absolute top-0 left-0 w-full h-px bg-gradient-to-r from-transparent via-ova-200 to-transparent" />
        <div className="mx-auto max-w-landing px-6">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-16 items-start">
            {/* Steps */}
            <AnimatedSection>
              <motion.div variants={fadeUp}>
                <span className="text-body-sm font-medium text-ova-blue uppercase tracking-wider">Vehicle Sales</span>
                <h2 className="mt-3 text-h1 text-ova-navy">The safest way to sell your car.</h2>
                <p className="mt-4 text-body-lg text-ova-500 leading-relaxed max-w-md">
                  No more standing in line. No more hoping the buyer actually pays. ARI holds the payment securely until both sides agree.
                </p>
              </motion.div>

              <motion.div variants={fadeUp} className="mt-10 space-y-6">
                {[
                  { num: '1', title: 'List your car', desc: 'Enter your vehicle details. We create a verified digital record of your ownership.' },
                  { num: '2', title: 'Share a link with the buyer', desc: 'Set your price and share a secure link. The buyer reviews the details and joins the deal.' },
                  { num: '3', title: 'Both confirm. Done.', desc: 'Payment is held securely until you both agree. Then money and ownership transfer simultaneously. Neither side can be cheated.' },
                ].map((step, i) => (
                  <motion.div key={step.num} variants={fadeUp} custom={i} className="flex gap-4">
                    <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-ova-navy text-caption font-semibold text-white">
                      {step.num}
                    </div>
                    <div>
                      <h4 className="text-body font-medium text-ova-900">{step.title}</h4>
                      <p className="mt-1 text-body-sm text-ova-500">{step.desc}</p>
                    </div>
                  </motion.div>
                ))}
              </motion.div>
            </AnimatedSection>

            {/* Comparison */}
            <AnimatedSection>
              <motion.div variants={fadeUp} className="rounded-2xl border border-ova-200 bg-white overflow-hidden shadow-sm">
                <div className="grid grid-cols-2">
                  <div className="px-5 py-4 bg-ova-100/50 border-b border-ova-200">
                    <span className="text-body-sm font-semibold text-ova-500">The old way</span>
                  </div>
                  <div className="px-5 py-4 bg-ova-navy border-b border-ova-navy">
                    <span className="text-body-sm font-semibold text-white">With ARI</span>
                  </div>
                </div>
                {[
                  { old: 'Go to the notary in person', ari: 'From your phone, anytime' },
                  { old: 'Payment is NOT verified', ari: 'Payment held securely' },
                  { old: 'Costs 3.5–6% of car value', ari: 'Flat ₺50 fee' },
                  { old: 'Paper-based proof', ari: 'Permanent digital proof' },
                  { old: 'Fraud is common', ari: 'Fraud is impossible' },
                  { old: 'Business hours only', ari: 'Available 24/7' },
                ].map((row, i, arr) => (
                  <div key={i} className={clsx('grid grid-cols-2', i < arr.length - 1 && 'border-b border-ova-100')}>
                    <div className="px-5 py-3.5 flex items-start gap-2.5">
                      <XCircle size={15} className="text-ova-red/60 shrink-0 mt-0.5" />
                      <span className="text-body-sm text-ova-500">{row.old}</span>
                    </div>
                    <div className="px-5 py-3.5 flex items-start gap-2.5 bg-ova-50/50">
                      <CheckCircle2 size={15} className="text-ova-green shrink-0 mt-0.5" />
                      <span className="text-body-sm text-ova-700 font-medium">{row.ari}</span>
                    </div>
                  </div>
                ))}
              </motion.div>
            </AnimatedSection>
          </div>
        </div>
      </section>

      {/* ─── TRANSFERS ─── */}
      <section id="transfers" className="py-24 scroll-mt-20">
        <div className="mx-auto max-w-landing px-6">
          <div className="grid grid-cols-1 lg:grid-cols-[1.1fr_0.9fr] gap-16 items-start">
            <AnimatedSection>
              <motion.p variants={fadeUp} className="text-body-sm font-medium text-ova-blue uppercase tracking-wider">Transfers</motion.p>
              <motion.h2 variants={fadeUp} className="mt-3 text-h1 text-ova-navy">
                Move money across borders. Instantly.
              </motion.h2>
              <motion.p variants={fadeUp} className="mt-4 text-body-lg text-ova-500 max-w-md">
                Competitive exchange rates, transparent fees, and real-time tracking. Your money arrives in seconds, not days.
              </motion.p>
              <motion.div variants={fadeUp} className="mt-8 grid grid-cols-2 gap-6">
                <FeatureItem icon={<Zap size={18} />} title="Under 2 minutes" desc="Transfers settle in seconds, not days." />
                <FeatureItem icon={<Receipt size={18} />} title="0.25% flat fee" desc="No hidden charges, no tiers." />
                <FeatureItem icon={<ArrowLeftRight size={18} />} title="Live FX rates" desc="Market rates, locked for 30 seconds." />
                <FeatureItem icon={<Lock size={18} />} title="Secure by design" desc="Your money is protected at every step." />
              </motion.div>
            </AnimatedSection>
            <AnimatedSection>
              <motion.div variants={fadeUp}><FxCalculator /></motion.div>
            </AnimatedSection>
          </div>
        </div>
      </section>

      {/* ─── TRUST ─── */}
      <section id="trust" className="py-24 bg-ova-50 scroll-mt-20 relative">
        <div className="absolute top-0 left-0 w-full h-px bg-gradient-to-r from-transparent via-ova-200 to-transparent" />
        <div className="mx-auto max-w-landing px-6">
          <AnimatedSection>
            <motion.div variants={fadeUp} className="text-center mb-14">
              <h2 className="text-h1 text-ova-navy">Built for trust</h2>
              <p className="mt-3 text-body-lg text-ova-500 max-w-md mx-auto">
                Your money and your data are protected at every level.
              </p>
            </motion.div>
          </AnimatedSection>

          <AnimatedSection className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {[
              { icon: UserCheck, title: 'Verified Identity', desc: 'Every user goes through identity verification. Real people, real accountability.' },
              { icon: Shield, title: 'Protected Transactions', desc: 'Payments are held securely until all parties confirm. No one can take your money and disappear.' },
              { icon: Lock, title: 'Bank-Grade Security', desc: 'Two-factor authentication, encrypted data, and continuous monitoring to keep your account safe.' },
              { icon: Receipt, title: 'Full Transparency', desc: 'Every transaction has a permanent, verifiable record. No hidden fees, no surprises.' },
              { icon: Smartphone, title: 'Works Everywhere', desc: 'Use ARI from your phone, tablet, or computer. Your finances are always with you.' },
              { icon: Zap, title: 'Real-Time Settlement', desc: 'No waiting days for money to arrive. Transfers and vehicle sales settle in seconds.' },
            ].map((f, i) => (
              <motion.div key={f.title} variants={fadeUp} custom={i}
                className="rounded-2xl border border-ova-200 bg-white p-6 hover:shadow-sm transition-shadow">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-ova-100 text-ova-navy">
                  <f.icon size={20} strokeWidth={1.5} />
                </div>
                <h4 className="mt-4 text-h3 text-ova-900">{f.title}</h4>
                <p className="mt-2 text-body-sm text-ova-500 leading-relaxed">{f.desc}</p>
              </motion.div>
            ))}
          </AnimatedSection>
        </div>
      </section>

      {/* ─── STATS ─── */}
      <section className="py-16 bg-ova-navy relative overflow-hidden">
        <div className="absolute inset-0 opacity-[0.04]">
          <svg width="100%" height="100%"><defs>
            <pattern id="stats-grid" width="40" height="40" patternUnits="userSpaceOnUse">
              <path d="M 40 0 L 0 0 0 40" fill="none" stroke="white" strokeWidth="0.5" />
            </pattern>
          </defs><rect width="100%" height="100%" fill="url(#stats-grid)" /></svg>
        </div>
        <div className="relative mx-auto max-w-landing px-6">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-8 text-center">
            <div>
              <p className="text-display text-white" style={{ fontVariantNumeric: 'tabular-nums' }}><AnimatedCounter target={50} suffix=" TRY" /></p>
              <p className="mt-2 text-body-sm text-white/40">Flat vehicle sale fee</p>
            </div>
            <div>
              <p className="text-display text-white" style={{ fontVariantNumeric: 'tabular-nums' }}>&lt;<AnimatedCounter target={2} suffix=" min" /></p>
              <p className="mt-2 text-body-sm text-white/40">Transfer delivery</p>
            </div>
            <div>
              <p className="text-display text-white" style={{ fontVariantNumeric: 'tabular-nums' }}><AnimatedCounter target={0} suffix="%" /></p>
              <p className="mt-2 text-body-sm text-white/40">Fraud rate</p>
            </div>
            <div>
              <p className="text-display text-white" style={{ fontVariantNumeric: 'tabular-nums' }}>24/7</p>
              <p className="mt-2 text-body-sm text-white/40">Always available</p>
            </div>
          </div>
        </div>
      </section>

      {/* ─── CTA ─── */}
      <section className="py-24">
        <div className="mx-auto max-w-form px-6 text-center">
          <h2 className="text-h1 text-ova-navy text-balance">Ready to take control of your finances?</h2>
          <p className="mt-4 text-body-lg text-ova-500 max-w-md mx-auto">
            Open an account in minutes. No paperwork, no branch visits.
          </p>
          <div className="mt-8">
            <Link href="/signup"
              className="inline-flex h-12 items-center gap-2 rounded-xl bg-ova-navy px-8 text-body font-medium text-white transition-all hover:bg-ova-navy-light hover:shadow-md active:scale-[0.98]">
              Open an account <ArrowRight size={18} />
            </Link>
          </div>
        </div>
      </section>

      {/* ─── FOOTER ─── */}
      <footer className="border-t border-ova-200 py-12 bg-ova-50/50">
        <div className="mx-auto max-w-landing px-6">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-8">
            <div className="col-span-2 md:col-span-1">
              <p className="ova-logo text-2xl">ARI</p>
              <p className="mt-2 text-body-sm text-ova-500">The financial platform<br />built for the modern world.</p>
            </div>
            <div>
              <h4 className="text-caption text-ova-400 uppercase tracking-wide">Product</h4>
              <ul className="mt-3 space-y-2">
                <li><a href="#products" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors">Features</a></li>
                <li><a href="#vehicles" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors">Vehicle Sales</a></li>
                <li><a href="#transfers" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors">Transfers</a></li>
                <li><a href="#trust" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors">Security</a></li>
              </ul>
            </div>
            <div>
              <h4 className="text-caption text-ova-400 uppercase tracking-wide">Company</h4>
              <ul className="mt-3 space-y-2">
                <li><a href="#" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors">About</a></li>
                <li><a href="#" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors">Careers</a></li>
                <li><a href="#" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors">Contact</a></li>
              </ul>
            </div>
            <div>
              <h4 className="text-caption text-ova-400 uppercase tracking-wide">Legal</h4>
              <ul className="mt-3 space-y-2">
                <li><a href="#" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors">Terms</a></li>
                <li><a href="#" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors">Privacy</a></li>
                <li><a href="#" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors">Compliance</a></li>
              </ul>
            </div>
          </div>
          <div className="mt-10 border-t border-ova-200 pt-6">
            <p className="text-caption text-ova-400">
              ARI Financial Services. Your money is protected by bank-grade encryption and verified identity checks.
            </p>
            <p className="mt-2 text-caption text-ova-400">&copy; {new Date().getFullYear()} ARI Financial Services. All rights reserved.</p>
          </div>
        </div>
      </footer>
    </div>
  );
}

/* ────────────────────────────────────────────────────────────
   Helpers
   ──────────────────────────────────────────────────────────── */

function FeatureItem({ icon, title, desc }: { icon: React.ReactNode; title: string; desc: string }) {
  return (
    <div>
      <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-ova-100 text-ova-navy">{icon}</div>
      <h4 className="mt-3 text-body font-medium text-ova-900">{title}</h4>
      <p className="mt-1 text-body-sm text-ova-500">{desc}</p>
    </div>
  );
}
