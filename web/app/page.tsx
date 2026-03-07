'use client';

import { useState, useEffect, useRef } from 'react';
import Link from 'next/link';
import { motion, useInView } from 'framer-motion';
import { clsx } from 'clsx';
import {
  ArrowLeftRight, Car, Shield, Lock, Zap, Receipt,
  CheckCircle2, XCircle, Menu, X, ChevronRight,
  ArrowRight, FileText, Clock, Ban, Landmark,
} from 'lucide-react';
import FxCalculator from '../components/ui/FxCalculator';
import AnimatedCounter from '../components/ui/AnimatedCounter';

/* ────── animation helpers ────── */
const fadeUp = {
  hidden: { opacity: 0, y: 20 },
  visible: (i: number = 0) => ({
    opacity: 1,
    y: 0,
    transition: { duration: 0.5, delay: i * 0.1, ease: [0.25, 0.46, 0.45, 0.94] },
  }),
};

function AnimatedSection({ children, className }: { children: React.ReactNode; className?: string }) {
  const ref = useRef(null);
  const isInView = useInView(ref, { once: true, margin: '-60px' });
  return (
    <motion.div
      ref={ref}
      initial="hidden"
      animate={isInView ? 'visible' : 'hidden'}
      variants={{ visible: { transition: { staggerChildren: 0.08 } } }}
      className={className}
    >
      {children}
    </motion.div>
  );
}

/* ────── comparison data ────── */
const comparison = [
  { notary: 'Must go to notary in person', ari: 'Complete from your phone', icon: Landmark },
  { notary: 'Notary does NOT verify payment', ari: 'Smart contract guarantees payment', icon: Shield },
  { notary: 'Costs thousands of TL (3.5-6%)', ari: 'Flat 50 TRY fee', icon: Receipt },
  { notary: 'Paper-based proof', ari: 'Blockchain-verified permanent proof', icon: FileText },
  { notary: 'Fraud is common', ari: 'Fraud is structurally impossible', icon: Ban },
  { notary: 'Only during business hours', ari: 'Available 24/7', icon: Clock },
];

/* ────── escrow steps ────── */
const escrowSteps = [
  {
    step: '01',
    title: 'Register your vehicle',
    description: 'Enter your vehicle details. ARI mints a digital ownership token (NFT) on the Avalanche blockchain, linked to your car\'s VIN and plate number.',
  },
  {
    step: '02',
    title: 'Share the deal link',
    description: 'Set your price and share a secure link with the buyer. They review the deal details and join with one tap.',
  },
  {
    step: '03',
    title: 'Funds locked in escrow',
    description: 'The buyer\'s payment is locked in a smart contract. The money leaves the buyer but doesn\'t reach the seller yet — it\'s held in tamper-proof escrow.',
  },
  {
    step: '04',
    title: 'Both parties confirm',
    description: 'Seller and buyer each confirm the deal. Neither can complete or cancel unilaterally — both must agree.',
  },
  {
    step: '05',
    title: 'Atomic swap executes',
    description: 'Payment releases to the seller and ownership transfers to the buyer simultaneously. Neither can happen without the other. Done.',
  },
];

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
        'fixed top-0 left-0 right-0 z-50 transition-all duration-base',
        scrolled
          ? 'bg-[var(--glass-bg)] backdrop-blur-sm border-b border-[var(--glass-border)] shadow-sm'
          : 'bg-white border-b border-ova-200',
      )}>
        <div className="mx-auto flex h-16 max-w-landing items-center justify-between px-6">
          <Link href="/" className="ova-logo text-2xl" aria-label="ARI home">
            ARI
          </Link>

          <div className="hidden md:flex items-center gap-6">
            <a href="#vehicle-escrow" className="text-body-sm font-medium text-ova-700 hover:text-ova-900 transition-colors duration-fast">Vehicle Escrow</a>
            <a href="#transfers" className="text-body-sm font-medium text-ova-700 hover:text-ova-900 transition-colors duration-fast">Transfers</a>
            <a href="#how-it-works" className="text-body-sm font-medium text-ova-700 hover:text-ova-900 transition-colors duration-fast">How it works</a>
            <a href="#comparison" className="text-body-sm font-medium text-ova-700 hover:text-ova-900 transition-colors duration-fast">Why ARI</a>
          </div>

          <div className="hidden md:flex items-center gap-4">
            <Link href="/login" className="text-body-sm font-medium text-ova-700 transition-colors duration-fast hover:text-ova-900">
              Sign in
            </Link>
            <Link href="/signup" className="inline-flex h-10 items-center rounded-xl bg-ova-navy px-5 text-body-sm font-medium text-white transition-all duration-base hover:bg-ova-navy-light hover:shadow-sm">
              Open an account
            </Link>
          </div>

          <button
            className="md:hidden p-2 text-ova-700 hover:text-ova-900 transition-colors duration-fast"
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            aria-label={mobileMenuOpen ? 'Close menu' : 'Open menu'}
          >
            {mobileMenuOpen ? <X size={24} /> : <Menu size={24} />}
          </button>
        </div>

        {mobileMenuOpen && (
          <div className="md:hidden border-t border-ova-200 bg-white px-6 py-4 space-y-3 animate-fade-in">
            <a href="#vehicle-escrow" onClick={() => setMobileMenuOpen(false)} className="block text-body-sm font-medium text-ova-700 py-2">Vehicle Escrow</a>
            <a href="#transfers" onClick={() => setMobileMenuOpen(false)} className="block text-body-sm font-medium text-ova-700 py-2">Transfers</a>
            <a href="#how-it-works" onClick={() => setMobileMenuOpen(false)} className="block text-body-sm font-medium text-ova-700 py-2">How it works</a>
            <a href="#comparison" onClick={() => setMobileMenuOpen(false)} className="block text-body-sm font-medium text-ova-700 py-2">Why ARI</a>
            <div className="border-t border-ova-200 pt-3 flex flex-col gap-3">
              <Link href="/login" className="text-body-sm font-medium text-ova-700">Sign in</Link>
              <Link href="/signup" className="inline-flex h-10 items-center justify-center rounded-xl bg-ova-navy px-5 text-body-sm font-medium text-white">Open an account</Link>
            </div>
          </div>
        )}
      </nav>

      {/* ──── HERO ──── */}
      <section className="bg-white pt-36 pb-20 overflow-hidden">
        <div className="mx-auto max-w-landing px-6">
          <div className="max-w-3xl">
            <motion.p
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.4, delay: 0.05 }}
              className="text-body-sm font-medium text-ova-blue uppercase tracking-wider"
            >
              Powered by Avalanche blockchain
            </motion.p>
            <motion.h1
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, delay: 0.12 }}
              className="mt-4 text-display text-ova-navy text-balance leading-[1.05]"
            >
              Sell your car without the notary.
              <br />
              <span className="text-ova-blue">Send money without the bank.</span>
            </motion.h1>
            <motion.p
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, delay: 0.22 }}
              className="mt-6 text-body-lg text-ova-700 max-w-xl"
            >
              ARI replaces Turkey&apos;s broken notary system with blockchain-guaranteed escrow for vehicle sales, and makes cross-border transfers between Turkey and Europe instant and transparent.
            </motion.p>
            <motion.div
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, delay: 0.32 }}
              className="mt-8 flex flex-wrap items-center gap-4"
            >
              <Link
                href="/signup"
                className="inline-flex h-12 items-center gap-2 rounded-xl bg-ova-navy px-6 text-body font-medium text-white transition-all duration-base hover:bg-ova-navy-light hover:shadow-sm active:scale-[0.98]"
              >
                Get started <ArrowRight size={18} />
              </Link>
              <a
                href="#vehicle-escrow"
                className="inline-flex h-12 items-center gap-2 rounded-xl border border-ova-300 px-6 text-body font-medium text-ova-700 transition-all duration-base hover:bg-ova-50 hover:border-ova-400"
              >
                See how escrow works
              </a>
            </motion.div>
          </div>

          {/* Hero guarantee banner */}
          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.45 }}
            className="mt-16 rounded-2xl bg-ova-navy p-8 md:p-10"
          >
            <p className="text-caption text-white/50 uppercase tracking-wider mb-4">The ARI Guarantee</p>
            <p className="text-h2 md:text-h1 text-white leading-snug max-w-3xl">
              Ownership does not transfer until payment confirms.
              Payment does not release until ownership transfers.
              <span className="text-ova-blue"> Neither party can cheat.</span>
            </p>
          </motion.div>
        </div>
      </section>

      {/* ──── TRUST BAR ──── */}
      <section className="bg-ova-100 py-5">
        <div className="mx-auto flex max-w-landing flex-wrap items-center justify-center gap-x-8 gap-y-2 px-6 text-caption text-ova-500">
          {['BDDK Licensed', 'PSD2 Authorized', 'KVKK Compliant', 'GDPR Compliant', 'MASAK Compliant', 'Avalanche L1'].map(label => (
            <span key={label} className="flex items-center gap-1.5">
              <TrustShield />
              {label}
            </span>
          ))}
        </div>
      </section>

      {/* ──── TWO PILLARS ──── */}
      <section className="py-20 bg-white">
        <div className="mx-auto max-w-landing px-6">
          <AnimatedSection>
            <motion.div variants={fadeUp} className="text-center mb-16">
              <h2 className="text-h1 text-ova-navy">Two problems. One platform.</h2>
              <p className="mt-3 text-body-lg text-ova-500 max-w-lg mx-auto">
                ARI brings blockchain security to everyday financial transactions in Turkey and Europe.
              </p>
            </motion.div>
          </AnimatedSection>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* Pillar 1: Vehicle Escrow */}
            <AnimatedSection>
              <motion.div variants={fadeUp} custom={0}>
                <a href="#vehicle-escrow" className="group block h-full rounded-2xl border border-ova-200 bg-white p-8 shadow-card hover:shadow-card-hover transition-all duration-base">
                  <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-ova-navy">
                    <Car size={28} strokeWidth={1.5} className="text-white" />
                  </div>
                  <h3 className="mt-6 text-h2 text-ova-900">Secure Vehicle Sales</h3>
                  <p className="mt-3 text-body-sm text-ova-500 leading-relaxed">
                    Sell or buy a car without going to the notary. Smart contract escrow locks payment until both parties confirm. Ownership transfers atomically on-chain. No fraud. No stamps. No waiting.
                  </p>
                  <div className="mt-6 flex items-center gap-2 text-body-sm font-medium text-ova-blue group-hover:gap-3 transition-all duration-fast">
                    Learn more <ChevronRight size={16} />
                  </div>
                </a>
              </motion.div>
            </AnimatedSection>

            {/* Pillar 2: Cross-Border Payments */}
            <AnimatedSection>
              <motion.div variants={fadeUp} custom={1}>
                <a href="#transfers" className="group block h-full rounded-2xl border border-ova-200 bg-white p-8 shadow-card hover:shadow-card-hover transition-all duration-base">
                  <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-ova-navy">
                    <ArrowLeftRight size={28} strokeWidth={1.5} className="text-white" />
                  </div>
                  <h3 className="mt-6 text-h2 text-ova-900">Cross-Border Transfers</h3>
                  <p className="mt-3 text-body-sm text-ova-500 leading-relaxed">
                    Send money between Turkey and Europe in under 2 minutes. Live FX rates, 0.25% flat fee, real-time settlement tracking. No SWIFT fees, no hidden markups, no waiting days.
                  </p>
                  <div className="mt-6 flex items-center gap-2 text-body-sm font-medium text-ova-blue group-hover:gap-3 transition-all duration-fast">
                    Learn more <ChevronRight size={16} />
                  </div>
                </a>
              </motion.div>
            </AnimatedSection>
          </div>
        </div>
      </section>

      {/* ──── THE PROBLEM ──── */}
      <section id="vehicle-escrow" className="py-20 bg-ova-50 scroll-mt-20">
        <div className="mx-auto max-w-landing px-6">
          <AnimatedSection>
            <motion.div variants={fadeUp} className="max-w-2xl">
              <p className="text-body-sm font-medium text-ova-blue uppercase tracking-wider">The problem</p>
              <h2 className="mt-3 text-h1 text-ova-navy">
                Selling a car in Turkey is broken.
              </h2>
              <p className="mt-4 text-body-lg text-ova-700 leading-relaxed">
                In 2026, you can send millions of lira to another country with a single tap. But selling a car requires physically going to a notary, standing in line, and paying thousands of lira for a stamp that <strong>doesn&apos;t even verify the payment happened</strong>.
              </p>
            </motion.div>
          </AnimatedSection>

          <AnimatedSection className="mt-12 grid grid-cols-1 md:grid-cols-3 gap-6">
            <motion.div variants={fadeUp} custom={0} className="rounded-2xl border border-ova-200 bg-white p-6">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-red-50">
                <XCircle size={20} className="text-ova-red" />
              </div>
              <h4 className="mt-4 text-h3 text-ova-900">No payment verification</h4>
              <p className="mt-2 text-body-sm text-ova-500">The notary stamps ownership transfer without confirming money actually changed hands. Fake receipts and scams are common.</p>
            </motion.div>
            <motion.div variants={fadeUp} custom={1} className="rounded-2xl border border-ova-200 bg-white p-6">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-red-50">
                <XCircle size={20} className="text-ova-red" />
              </div>
              <h4 className="mt-4 text-h3 text-ova-900">Costs thousands</h4>
              <p className="mt-2 text-body-sm text-ova-500">Notary fees run 3.5-6% of vehicle value. For a 500,000 TL car, that&apos;s up to 30,000 TL for a rubber stamp.</p>
            </motion.div>
            <motion.div variants={fadeUp} custom={2} className="rounded-2xl border border-ova-200 bg-white p-6">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-red-50">
                <XCircle size={20} className="text-ova-red" />
              </div>
              <h4 className="mt-4 text-h3 text-ova-900">Physical, slow, limited</h4>
              <p className="mt-2 text-body-sm text-ova-500">Both parties must physically go to the same office, during business hours, and wait. In 2026. For a piece of paper.</p>
            </motion.div>
          </AnimatedSection>
        </div>
      </section>

      {/* ──── HOW ESCROW WORKS ──── */}
      <section id="how-it-works" className="py-20 bg-white scroll-mt-20">
        <div className="mx-auto max-w-landing px-6">
          <AnimatedSection>
            <motion.div variants={fadeUp} className="text-center mb-16">
              <p className="text-body-sm font-medium text-ova-blue uppercase tracking-wider">How it works</p>
              <h2 className="mt-3 text-h1 text-ova-navy">Sell a car in 5 steps. From your couch.</h2>
              <p className="mt-3 text-body-lg text-ova-500 max-w-lg mx-auto">
                Blockchain-guaranteed escrow replaces the notary. Every step is verified on-chain.
              </p>
            </motion.div>
          </AnimatedSection>

          <div className="max-w-2xl mx-auto">
            {escrowSteps.map((step, i) => (
              <AnimatedSection key={step.step}>
                <motion.div
                  variants={fadeUp}
                  custom={i}
                  className={clsx(
                    'flex gap-6 pb-10',
                    i < escrowSteps.length - 1 && 'border-l-2 border-ova-200 ml-5 pl-10',
                    i === escrowSteps.length - 1 && 'ml-5 pl-10',
                  )}
                >
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-ova-navy text-body-sm font-semibold text-white -ml-[3.25rem]">
                    {step.step}
                  </div>
                  <div className="-mt-1">
                    <h4 className="text-h3 text-ova-900">{step.title}</h4>
                    <p className="mt-2 text-body-sm text-ova-500 leading-relaxed">{step.description}</p>
                  </div>
                </motion.div>
              </AnimatedSection>
            ))}
          </div>
        </div>
      </section>

      {/* ──── COMPARISON TABLE ──── */}
      <section id="comparison" className="py-20 bg-ova-50 scroll-mt-20">
        <div className="mx-auto max-w-landing px-6">
          <AnimatedSection>
            <motion.div variants={fadeUp} className="text-center mb-12">
              <h2 className="text-h1 text-ova-navy">Notary vs ARI</h2>
              <p className="mt-3 text-body-lg text-ova-500 max-w-md mx-auto">
                See what changes when you replace stamps with smart contracts.
              </p>
            </motion.div>
          </AnimatedSection>

          <AnimatedSection className="max-w-3xl mx-auto">
            <motion.div variants={fadeUp} className="rounded-2xl border border-ova-200 bg-white overflow-hidden">
              {/* Header */}
              <div className="grid grid-cols-[1fr_1fr] border-b border-ova-200">
                <div className="px-6 py-4 bg-red-50">
                  <span className="text-body-sm font-semibold text-ova-red">Traditional Notary</span>
                </div>
                <div className="px-6 py-4 bg-ova-navy">
                  <span className="text-body-sm font-semibold text-white">ARI Smart Escrow</span>
                </div>
              </div>

              {/* Rows */}
              {comparison.map((row, i) => (
                <motion.div
                  key={i}
                  variants={fadeUp}
                  custom={i}
                  className={clsx(
                    'grid grid-cols-[1fr_1fr]',
                    i < comparison.length - 1 && 'border-b border-ova-100',
                  )}
                >
                  <div className="px-6 py-4 flex items-start gap-3">
                    <XCircle size={16} className="text-ova-red shrink-0 mt-0.5" />
                    <span className="text-body-sm text-ova-700">{row.notary}</span>
                  </div>
                  <div className="px-6 py-4 flex items-start gap-3 bg-ova-50">
                    <CheckCircle2 size={16} className="text-ova-green shrink-0 mt-0.5" />
                    <span className="text-body-sm text-ova-700 font-medium">{row.ari}</span>
                  </div>
                </motion.div>
              ))}
            </motion.div>
          </AnimatedSection>
        </div>
      </section>

      {/* ──── CROSS-BORDER TRANSFERS ──── */}
      <section id="transfers" className="py-20 bg-white scroll-mt-20">
        <div className="mx-auto max-w-landing px-6">
          <div className="grid grid-cols-1 lg:grid-cols-[1.1fr_0.9fr] gap-16 items-start">
            <AnimatedSection>
              <motion.p variants={fadeUp} className="text-body-sm font-medium text-ova-blue uppercase tracking-wider">
                Cross-Border Transfers
              </motion.p>
              <motion.h2 variants={fadeUp} className="mt-3 text-h1 text-ova-navy">
                Move money between Turkey and Europe. Instantly.
              </motion.h2>
              <motion.p variants={fadeUp} className="mt-4 text-body-lg text-ova-700 max-w-md">
                Live FX rates, transparent fees, and real-time settlement tracking powered by the Avalanche blockchain.
              </motion.p>

              <motion.div variants={fadeUp} className="mt-8 grid grid-cols-2 gap-6">
                <FeatureItem icon={<Zap size={20} />} title="Under 2 minutes" desc="Cross-border transfers settle in seconds, not days." />
                <FeatureItem icon={<Receipt size={20} />} title="0.25% flat fee" desc="No hidden markups, no tiers, no fine print." />
                <FeatureItem icon={<ArrowLeftRight size={20} />} title="Live FX rates" desc="Market rates locked for 30 seconds. No surprises." />
                <FeatureItem icon={<Lock size={20} />} title="Bank-grade security" desc="2FA, KYC, sanctions screening, encryption." />
              </motion.div>
            </AnimatedSection>

            <AnimatedSection>
              <motion.div variants={fadeUp}>
                <FxCalculator />
              </motion.div>
            </AnimatedSection>
          </div>
        </div>
      </section>

      {/* ──── FEATURES GRID ──── */}
      <section className="py-20 bg-ova-50">
        <div className="mx-auto max-w-landing px-6">
          <AnimatedSection>
            <motion.div variants={fadeUp} className="text-center mb-12">
              <h2 className="text-h1 text-ova-navy">Built for trust</h2>
              <p className="mt-3 text-body-lg text-ova-500 max-w-md mx-auto">
                Every feature is designed around one principle: you should never have to trust a stranger with your money.
              </p>
            </motion.div>
          </AnimatedSection>

          <AnimatedSection className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {[
              { icon: Shield, title: 'Smart Contract Escrow', desc: 'Funds locked in tamper-proof code. No person or institution controls the money during the transaction.' },
              { icon: Car, title: 'Vehicle NFT Ownership', desc: 'Your car is represented as a unique token on Avalanche. Transfer is verified on-chain, permanently recorded.' },
              { icon: Lock, title: 'KYC-Verified Parties', desc: 'Both buyer and seller must pass identity verification. Real people, real accountability.' },
              { icon: Zap, title: 'Atomic Swap', desc: 'Payment and ownership transfer happen simultaneously. If one fails, both fail. No partial states.' },
              { icon: FileText, title: 'On-Chain Audit Trail', desc: 'Every step recorded on blockchain. Permanent, tamper-proof receipts for any future dispute.' },
              { icon: ArrowLeftRight, title: 'Cross-Border Ready', desc: 'Same-currency and FX transfers between Turkey and Europe. Avalanche Teleporter bridge.' },
            ].map((feature, i) => (
              <motion.div
                key={feature.title}
                variants={fadeUp}
                custom={i}
                className="rounded-2xl border border-ova-200 bg-white p-6 shadow-card"
              >
                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-ova-100 text-ova-navy">
                  <feature.icon size={20} strokeWidth={1.5} />
                </div>
                <h4 className="mt-4 text-h3 text-ova-900">{feature.title}</h4>
                <p className="mt-2 text-body-sm text-ova-500 leading-relaxed">{feature.desc}</p>
              </motion.div>
            ))}
          </AnimatedSection>
        </div>
      </section>

      {/* ──── STATS ──── */}
      <section className="py-16 bg-ova-navy">
        <div className="mx-auto max-w-landing px-6">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-8 text-center">
            <div>
              <p className="text-display text-white amount">
                <AnimatedCounter target={50} suffix=" TRY" />
              </p>
              <p className="mt-2 text-body-sm text-white/50">Flat escrow fee</p>
            </div>
            <div>
              <p className="text-display text-white amount">
                <AnimatedCounter target={0} suffix="%" prefix="" />
              </p>
              <p className="mt-2 text-body-sm text-white/50">Fraud rate (by design)</p>
            </div>
            <div>
              <p className="text-display text-white amount">
                &lt;<AnimatedCounter target={2} suffix=" min" />
              </p>
              <p className="mt-2 text-body-sm text-white/50">Transfer delivery</p>
            </div>
            <div>
              <p className="text-display text-white amount">
                <AnimatedCounter target={183} suffix="" />
              </p>
              <p className="mt-2 text-body-sm text-white/50">Smart contract tests</p>
            </div>
          </div>
        </div>
      </section>

      {/* ──── FINAL CTA ──── */}
      <section className="py-20 bg-white">
        <div className="mx-auto max-w-form px-6 text-center">
          <h2 className="text-h1 text-ova-navy text-balance">
            Why should selling a car be harder than sending money abroad?
          </h2>
          <p className="mt-4 text-body-lg text-ova-500 max-w-md mx-auto">
            ARI makes it just as easy, just as safe, and dramatically cheaper.
          </p>
          <div className="mt-8 flex flex-wrap justify-center gap-4">
            <Link
              href="/signup"
              className="inline-flex h-12 items-center gap-2 rounded-xl bg-ova-navy px-8 text-body font-medium text-white transition-all duration-base hover:bg-ova-navy-light hover:shadow-sm active:scale-[0.98]"
            >
              Open an account <ArrowRight size={18} />
            </Link>
          </div>
        </div>
      </section>

      {/* ──── FOOTER ──── */}
      <footer className="border-t border-ova-200 py-12">
        <div className="mx-auto max-w-landing px-6">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-8">
            <div className="col-span-2 md:col-span-1">
              <p className="ova-logo text-2xl">ARI</p>
              <p className="mt-2 text-body-sm text-ova-500">
                Replacing notaries with<br />smart contracts.
              </p>
            </div>

            <div>
              <h4 className="text-caption text-ova-400 uppercase tracking-wide">Product</h4>
              <ul className="mt-3 space-y-2">
                <li><a href="#vehicle-escrow" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">Vehicle Escrow</a></li>
                <li><a href="#transfers" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">Transfers</a></li>
                <li><a href="#how-it-works" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">How it works</a></li>
                <li><a href="#comparison" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">Why ARI</a></li>
              </ul>
            </div>

            <div>
              <h4 className="text-caption text-ova-400 uppercase tracking-wide">Company</h4>
              <ul className="mt-3 space-y-2">
                <li><a href="#" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">About</a></li>
                <li><a href="#" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">Security</a></li>
                <li><a href="#" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">Contact</a></li>
              </ul>
            </div>

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
              ARI Financial Services, licensed by BDDK (Turkey) and authorized under PSD2 (European Union).
              KVKK and GDPR compliant. Built on the Avalanche blockchain. All transfers and escrow transactions are subject to regulatory compliance checks.
            </p>
            <p className="mt-2 text-caption text-ova-400">
              &copy; {new Date().getFullYear()} ARI Financial Services. All rights reserved.
            </p>
          </div>
        </div>
      </footer>
    </div>
  );
}

/* ──── HELPER COMPONENTS ──── */

function TrustShield() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="text-ova-400">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
    </svg>
  );
}

function FeatureItem({ icon, title, desc }: { icon: React.ReactNode; title: string; desc: string }) {
  return (
    <div>
      <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-ova-100 text-ova-navy">
        {icon}
      </div>
      <h4 className="mt-3 text-body font-medium text-ova-900">{title}</h4>
      <p className="mt-1 text-body-sm text-ova-500">{desc}</p>
    </div>
  );
}
