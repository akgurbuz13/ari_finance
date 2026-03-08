'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import Link from 'next/link';
import { ArrowRight, ArrowUpRight, Globe, CheckCircle2, ArrowRightLeft } from 'lucide-react';
import { motion } from 'framer-motion';
import api from '../../../lib/api/client';
import type { Account, FxQuote } from '../../../lib/api/types';
import Input from '../../../components/ui/Input';
import Button from '../../../components/ui/Button';
import Card from '../../../components/ui/Card';
import Skeleton, { SkeletonCard } from '../../../components/ui/Skeleton';
import AvalancheBadge from '../../../components/ui/AvalancheBadge';
import TransferProgress from '../../../components/ui/TransferProgress';
import type { TransferStep } from '../../../components/ui/TransferProgress';

type Tab = 'domestic' | 'crossBorder';

function formatAmount(amount: string | number, currency: string) {
  const num = typeof amount === 'string' ? parseFloat(amount) : amount;
  const symbol = currency === 'TRY' ? '\u20BA' : '\u20AC';
  const locale = currency === 'TRY' ? 'tr-TR' : 'de-DE';
  return `${symbol}${num.toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function CountdownRing({ seconds, total }: { seconds: number; total: number }) {
  const radius = 18;
  const circumference = 2 * Math.PI * radius;
  const progress = total > 0 ? seconds / total : 0;
  const offset = circumference * (1 - progress);
  const color = seconds > 10 ? 'text-ari-green' : seconds > 5 ? 'text-ari-amber' : 'text-ari-red';

  return (
    <div className="relative flex items-center justify-center">
      <svg width="48" height="48" viewBox="0 0 48 48" className="-rotate-90">
        <circle
          cx="24" cy="24" r={radius}
          fill="none"
          stroke="currentColor"
          strokeWidth="2.5"
          className="text-ari-100"
        />
        <circle
          cx="24" cy="24" r={radius}
          fill="none"
          stroke="currentColor"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          className={`${color} transition-all duration-1000 ease-linear`}
        />
      </svg>
      <span className={`absolute text-caption font-mono font-semibold ${color}`}>{seconds}s</span>
    </div>
  );
}

function ChainVisualization({ sourceCurrency, targetCurrency, sameCurrency }: { sourceCurrency: string; targetCurrency: string; sameCurrency: boolean }) {
  const sourceLabel = sourceCurrency === 'TRY' ? 'Turkey L1' : 'Europe L1';
  const destLabel = sameCurrency
    ? (sourceCurrency === 'TRY' ? 'Europe L1' : 'Turkey L1')
    : (targetCurrency === 'TRY' ? 'Turkey L1' : 'Europe L1');

  return (
    <div className="flex items-center justify-between bg-ari-navy rounded-xl px-5 py-4">
      <div className="text-center">
        <p className="text-micro font-medium text-white/50 uppercase tracking-wider">Source</p>
        <p className="text-body-sm font-semibold text-white mt-0.5">{sourceLabel}</p>
        <p className="text-micro text-white/40 font-mono mt-0.5">{sourceCurrency}</p>
      </div>
      <div className="flex items-center gap-1.5 px-4">
        {[0, 1, 2].map((i) => (
          <motion.div
            key={i}
            className="w-1.5 h-1.5 rounded-full bg-white/40"
            animate={{ opacity: [0.3, 1, 0.3] }}
            transition={{ duration: 1.5, repeat: Infinity, delay: i * 0.3 }}
          />
        ))}
        <ArrowRight size={14} className="text-white/60 mx-1" />
        {[0, 1, 2].map((i) => (
          <motion.div
            key={i + 3}
            className="w-1.5 h-1.5 rounded-full bg-white/40"
            animate={{ opacity: [0.3, 1, 0.3] }}
            transition={{ duration: 1.5, repeat: Infinity, delay: (i + 3) * 0.3 }}
          />
        ))}
      </div>
      <div className="text-center">
        <p className="text-micro font-medium text-white/50 uppercase tracking-wider">Destination</p>
        <p className="text-body-sm font-semibold text-white mt-0.5">{destLabel}</p>
        <p className="text-micro text-white/40 font-mono mt-0.5">{targetCurrency}</p>
      </div>
    </div>
  );
}

export default function TransferPage() {
  const [tab, setTab] = useState<Tab>('domestic');
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loadingAccounts, setLoadingAccounts] = useState(true);

  // Domestic state
  const [senderAccountId, setSenderAccountId] = useState('');
  const [receiverAccountId, setReceiverAccountId] = useState('');
  const [amount, setAmount] = useState('');
  const [currency, setCurrency] = useState('TRY');
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState('');
  const [error, setError] = useState('');

  // Cross-border state
  const [cbSourceAccount, setCbSourceAccount] = useState('');
  const [cbReceiverAccount, setCbReceiverAccount] = useState('');
  const [cbAmount, setCbAmount] = useState('');
  const [cbQuote, setCbQuote] = useState<FxQuote | null>(null);
  const [cbQuoteCountdown, setCbQuoteCountdown] = useState(0);
  const [cbQuoteTotal, setCbQuoteTotal] = useState(0);
  const [cbStep, setCbStep] = useState<'form' | 'quote' | 'progress' | 'success'>('form');
  const [cbLoading, setCbLoading] = useState(false);
  const [cbError, setCbError] = useState('');
  const [cbPaymentId, setCbPaymentId] = useState('');
  const [progressSteps, setProgressSteps] = useState<TransferStep[]>([]);
  const countdownRef = useRef<NodeJS.Timeout | null>(null);
  const progressRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    async function loadAccounts() {
      try {
        const res = await api.get<Account[]>('/accounts');
        setAccounts(res.data);
        if (res.data.length > 0) {
          setSenderAccountId(res.data[0].id);
          setCbSourceAccount(res.data[0].id);
        }
      } catch {
        // handled by interceptor
      } finally {
        setLoadingAccounts(false);
      }
    }
    loadAccounts();
  }, []);

  // Countdown timer for FX quote
  useEffect(() => {
    if (cbQuoteCountdown <= 0 && countdownRef.current) {
      clearInterval(countdownRef.current);
      countdownRef.current = null;
      if (cbStep === 'quote') {
        setCbQuote(null);
        setCbStep('form');
        setCbError('Quote expired. Please request a new quote.');
      }
    }
  }, [cbQuoteCountdown, cbStep]);

  // Cleanup intervals on unmount
  useEffect(() => {
    return () => {
      if (countdownRef.current) clearInterval(countdownRef.current);
      if (progressRef.current) clearInterval(progressRef.current);
    };
  }, []);

  const getSourceAccount = useCallback(() => {
    return accounts.find(a => a.id === cbSourceAccount);
  }, [accounts, cbSourceAccount]);

  const [cbSameCurrency, setCbSameCurrency] = useState(false);

  const getTargetCurrency = useCallback(() => {
    const source = getSourceAccount();
    if (!source) return 'EUR';
    if (cbSameCurrency) return source.currency;
    return source.currency === 'TRY' ? 'EUR' : 'TRY';
  }, [getSourceAccount, cbSameCurrency]);

  const handleDomestic = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setLoading(true);

    try {
      await api.post('/payments/domestic', {
        senderAccountId,
        receiverAccountId,
        amount: parseFloat(amount),
        currency,
      }, {
        headers: { 'Idempotency-Key': crypto.randomUUID() },
      });
      setSuccess('Transfer completed successfully');
      setAmount('');
      setReceiverAccountId('');
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      setError(axiosErr.response?.data?.message || 'Transfer failed');
    } finally {
      setLoading(false);
    }
  };

  const handleGetQuote = async (e: React.FormEvent) => {
    e.preventDefault();
    setCbError('');
    setCbLoading(true);

    const sourceAccount = getSourceAccount();
    if (!sourceAccount) {
      setCbError('Please select a source account');
      setCbLoading(false);
      return;
    }

    try {
      const res = await api.post<FxQuote>('/fx/quotes', {
        sourceCurrency: sourceAccount.currency,
        targetCurrency: getTargetCurrency(),
        sourceAmount: parseFloat(cbAmount),
      });

      setCbQuote(res.data);
      setCbStep('quote');

      // Start countdown based on expiresAt
      const expiresAt = new Date(res.data.expiresAt).getTime();
      const now = Date.now();
      const secondsLeft = Math.max(0, Math.floor((expiresAt - now) / 1000));
      setCbQuoteCountdown(secondsLeft);
      setCbQuoteTotal(secondsLeft);

      if (countdownRef.current) clearInterval(countdownRef.current);
      countdownRef.current = setInterval(() => {
        setCbQuoteCountdown(prev => prev - 1);
      }, 1000);
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      setCbError(axiosErr.response?.data?.message || 'Failed to get FX quote');
    } finally {
      setCbLoading(false);
    }
  };

  const handleSameCurrencyTransfer = async (e: React.FormEvent) => {
    e.preventDefault();
    setCbError('');
    setCbLoading(true);

    try {
      const res = await api.post('/payments/cross-border', {
        senderAccountId: cbSourceAccount,
        receiverAccountId: cbReceiverAccount,
        amount: parseFloat(cbAmount),
      }, {
        headers: { 'Idempotency-Key': crypto.randomUUID() },
      });

      setCbPaymentId(res.data.id || '');
      simulateSameCcyProgress();
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      setCbError(axiosErr.response?.data?.message || 'Same-currency cross-border transfer failed');
    } finally {
      setCbLoading(false);
    }
  };

  const simulateSameCcyProgress = () => {
    const now = () => new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' });

    const initialSteps: TransferStep[] = [
      { label: 'Payment initiated', status: 'active', timestamp: now() },
      { label: 'Compliance check', status: 'pending' },
      { label: 'Blockchain settlement', status: 'pending', details: [] },
      { label: 'Delivered', status: 'pending' },
    ];
    setProgressSteps(initialSteps);
    setCbStep('progress');

    let stepIndex = 0;
    progressRef.current = setInterval(() => {
      stepIndex++;
      setProgressSteps(prev => {
        const updated = prev.map((s, i) => {
          if (i < stepIndex) return { ...s, status: 'completed' as const, timestamp: s.timestamp || now() };
          if (i === stepIndex) {
            const step = { ...s, status: 'active' as const, timestamp: now() };
            if (i === 2) {
              step.details = [
                { label: 'Burn on source chain', value: 'Processing...' },
                { label: 'Teleporter relay', value: 'Pending' },
                { label: 'Mint on dest chain', value: 'Pending' },
              ];
            }
            return step;
          }
          return s;
        });
        return updated;
      });

      if (stepIndex >= initialSteps.length - 1) {
        if (progressRef.current) clearInterval(progressRef.current);
        setTimeout(() => {
          setProgressSteps(prev => prev.map(s => ({
            ...s,
            status: 'completed' as const,
            timestamp: s.timestamp || now(),
            details: s.label === 'Blockchain settlement' ? [
              { label: 'Burn on source chain', value: '0x1a2b...3c4d' },
              { label: 'Teleporter relay', value: 'Confirmed' },
              { label: 'Mint on dest chain', value: 'Confirmed' },
            ] : s.details,
          })));
          setCbStep('success');
        }, 2000);
      }
    }, 2000);
  };

  const simulateProgress = () => {
    const now = () => new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' });

    const initialSteps: TransferStep[] = [
      { label: 'Payment initiated', status: 'active', timestamp: now() },
      { label: 'Compliance check', status: 'pending' },
      { label: 'FX conversion', status: 'pending' },
      { label: 'Settlement', status: 'pending', details: [] },
      { label: 'Delivered', status: 'pending' },
    ];
    setProgressSteps(initialSteps);
    setCbStep('progress');

    let stepIndex = 0;
    progressRef.current = setInterval(() => {
      stepIndex++;
      setProgressSteps(prev => {
        const updated = prev.map((s, i) => {
          if (i < stepIndex) return { ...s, status: 'completed' as const, timestamp: s.timestamp || now() };
          if (i === stepIndex) {
            const step = { ...s, status: 'active' as const, timestamp: now() };
            if (i === 3) {
              step.details = [
                { label: 'Burn tx', value: '0x1a2b...3c4d' },
                { label: 'Bridge relay', value: 'In transit' },
                { label: 'Mint tx', value: 'Pending' },
              ];
            }
            return step;
          }
          return s;
        });
        return updated;
      });

      if (stepIndex >= initialSteps.length - 1) {
        if (progressRef.current) clearInterval(progressRef.current);
        // Mark all complete after a short delay
        setTimeout(() => {
          setProgressSteps(prev => prev.map(s => ({
            ...s,
            status: 'completed' as const,
            timestamp: s.timestamp || now(),
            details: s.label === 'Settlement' ? [
              { label: 'Burn tx', value: '0x1a2b...3c4d' },
              { label: 'Bridge relay', value: 'Confirmed' },
              { label: 'Mint tx', value: '0x5e6f...7a8b' },
            ] : s.details,
          })));
          setCbStep('success');
        }, 2000);
      }
    }, 2000);
  };

  const handleConfirmCrossBorder = async () => {
    if (!cbQuote) return;
    setCbError('');
    setCbLoading(true);

    try {
      const res = await api.post('/payments/cross-border', {
        senderAccountId: cbSourceAccount,
        receiverAccountId: cbReceiverAccount,
        amount: parseFloat(cbAmount),
        currency: getSourceAccount()?.currency,
        fxQuoteId: cbQuote.quoteId,
      }, {
        headers: { 'Idempotency-Key': crypto.randomUUID() },
      });

      if (countdownRef.current) {
        clearInterval(countdownRef.current);
        countdownRef.current = null;
      }

      setCbPaymentId(res.data.id || res.data.paymentOrderId || '');
      simulateProgress();
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      setCbError(axiosErr.response?.data?.message || 'Cross-border transfer failed');
    } finally {
      setCbLoading(false);
    }
  };

  const resetCrossBorder = () => {
    setCbQuote(null);
    setCbStep('form');
    setCbAmount('');
    setCbReceiverAccount('');
    setCbError('');
    setCbPaymentId('');
    setCbSameCurrency(false);
    setProgressSteps([]);
    if (countdownRef.current) {
      clearInterval(countdownRef.current);
      countdownRef.current = null;
    }
    if (progressRef.current) {
      clearInterval(progressRef.current);
      progressRef.current = null;
    }
  };

  if (loadingAccounts) {
    return (
      <div className="max-w-2xl mx-auto space-y-6">
        <Skeleton variant="text" className="w-32 h-8" />
        <SkeletonCard />
      </div>
    );
  }

  const sourceAccount = getSourceAccount();
  const selectClassName = "w-full h-11 px-4 bg-ari-50 border border-ari-200 rounded-xl text-ari-900 transition-all duration-base focus:outline-none focus:bg-white focus:border-ari-900 focus:ring-1 focus:ring-ari-900/10 appearance-none cursor-pointer text-body-sm";

  return (
    <div className="max-w-2xl mx-auto space-y-8">
      {/* Page header */}
      <div>
        <h1 className="text-h2 font-display text-ari-900">Transfer</h1>
        <p className="text-body-sm text-ari-500 mt-1">Send money domestically or across borders</p>
      </div>

      {/* Pill-style tab toggle */}
      <div className="inline-flex bg-ari-100 rounded-xl p-1 gap-1">
        {(['domestic', 'crossBorder'] as Tab[]).map((t) => (
          <button
            key={t}
            onClick={() => { setTab(t); setSuccess(''); setError(''); setCbError(''); }}
            className={`inline-flex items-center gap-1.5 px-5 py-2.5 rounded-lg text-body-sm font-medium transition-all duration-base cursor-pointer ${
              tab === t
                ? 'bg-white text-ari-900 shadow-sm'
                : 'text-ari-500 hover:text-ari-700'
            }`}
          >
            {t === 'domestic' ? (
              <><ArrowUpRight size={15} strokeWidth={1.5} /> Domestic</>
            ) : (
              <><Globe size={15} strokeWidth={1.5} /> Cross-Border</>
            )}
          </button>
        ))}
      </div>

      {/* Domestic success/error banners */}
      {success && (
        <motion.div
          initial={{ opacity: 0, y: -8 }}
          animate={{ opacity: 1, y: 0 }}
          role="alert"
          className="p-4 bg-ari-green/5 border border-ari-green/20 rounded-2xl text-body-sm text-ari-green flex items-center gap-2"
        >
          <CheckCircle2 size={16} strokeWidth={1.5} />
          {success}
        </motion.div>
      )}
      {error && (
        <motion.div
          initial={{ opacity: 0, y: -8 }}
          animate={{ opacity: 1, y: 0 }}
          role="alert"
          className="p-4 bg-ari-red/5 border border-ari-red/20 rounded-2xl text-body-sm text-ari-red"
        >
          {error}
        </motion.div>
      )}

      {/* ===== DOMESTIC TAB ===== */}
      {tab === 'domestic' && (
        <Card>
          <form onSubmit={handleDomestic} className="space-y-5">
            <div>
              <label className="micro-label mb-3 block">From Account</label>
              <select
                value={senderAccountId}
                onChange={(e) => setSenderAccountId(e.target.value)}
                className={selectClassName}
                required
              >
                <option value="">Select account</option>
                {accounts.map(a => (
                  <option key={a.id} value={a.id}>
                    {a.currency === 'TRY' ? '\u{1F1F9}\u{1F1F7}' : '\u{1F1EA}\u{1F1FA}'} {a.currency} — {formatAmount(a.balance, a.currency)}
                  </option>
                ))}
              </select>
            </div>
            <Input
              label="To Account ID"
              value={receiverAccountId}
              onChange={(e) => setReceiverAccountId(e.target.value)}
              placeholder="Recipient account ID"
              required
            />
            <Input
              label="Amount"
              type="number"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0.00"
              min="0.01"
              step="0.01"
              required
            />
            <div>
              <label className="micro-label mb-3 block">Currency</label>
              <select
                value={currency}
                onChange={(e) => setCurrency(e.target.value)}
                className={selectClassName}
              >
                <option value="TRY">TRY ({'\u20BA'})</option>
                <option value="EUR">EUR ({'\u20AC'})</option>
              </select>
            </div>
            <Button type="submit" fullWidth loading={loading} size="lg">
              Send Money
            </Button>
          </form>
        </Card>
      )}

      {/* ===== CROSS-BORDER: STEP 1 - FORM ===== */}
      {tab === 'crossBorder' && cbStep === 'form' && (
        <Card>
          {cbError && (
            <motion.div
              initial={{ opacity: 0, y: -8 }}
              animate={{ opacity: 1, y: 0 }}
              role="alert"
              className="mb-5 p-4 bg-ari-red/5 border border-ari-red/20 rounded-xl text-body-sm text-ari-red"
            >
              {cbError}
            </motion.div>
          )}
          <form onSubmit={cbSameCurrency ? handleSameCurrencyTransfer : handleGetQuote} className="space-y-5">
            <div>
              <label className="micro-label mb-3 block">From Account</label>
              <select
                value={cbSourceAccount}
                onChange={(e) => setCbSourceAccount(e.target.value)}
                className={selectClassName}
                required
              >
                <option value="">Select source account</option>
                {accounts.filter(a => a.status === 'active').map(a => (
                  <option key={a.id} value={a.id}>
                    {a.currency === 'TRY' ? '\u{1F1F9}\u{1F1F7}' : '\u{1F1EA}\u{1F1FA}'} {a.currency} — {formatAmount(a.balance, a.currency)}
                  </option>
                ))}
              </select>
            </div>

            {cbSourceAccount && (
              <div className="space-y-4">
                {/* Same-currency toggle card */}
                <button
                  type="button"
                  onClick={() => setCbSameCurrency(!cbSameCurrency)}
                  className={`flex items-center gap-3 w-full p-3.5 rounded-xl border transition-all duration-base text-left cursor-pointer ${
                    cbSameCurrency
                      ? 'border-ari-900 bg-ari-50'
                      : 'border-ari-200 hover:border-ari-300'
                  }`}
                >
                  <div className={`flex h-5 w-5 items-center justify-center rounded-md border-2 transition-all ${
                    cbSameCurrency
                      ? 'bg-ari-navy border-ari-navy'
                      : 'border-ari-300'
                  }`}>
                    {cbSameCurrency && (
                      <svg width="10" height="8" viewBox="0 0 10 8" fill="none">
                        <path d="M1 4L3.5 6.5L9 1" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                      </svg>
                    )}
                  </div>
                  <div className="flex-1">
                    <p className="text-body-sm font-medium text-ari-900">Same currency transfer</p>
                    <p className="text-caption text-ari-500">Send to a different region without FX conversion</p>
                  </div>
                  <ArrowRightLeft size={16} className="text-ari-400" />
                </button>

                {/* Target currency display */}
                <div className="flex items-center justify-between p-3.5 bg-ari-50 rounded-xl">
                  <span className="text-caption text-ari-500 uppercase tracking-wide font-medium">Target currency</span>
                  <div className="flex items-center gap-2">
                    <span className="text-body-sm font-semibold text-ari-900">
                      {getTargetCurrency()} ({getTargetCurrency() === 'TRY' ? '\u20BA' : '\u20AC'})
                    </span>
                    {cbSameCurrency && (
                      <span className="text-micro text-ari-400 bg-ari-100 px-2 py-0.5 rounded-full">different region</span>
                    )}
                  </div>
                </div>
              </div>
            )}

            <Input
              label="Recipient Account ID"
              value={cbReceiverAccount}
              onChange={(e) => setCbReceiverAccount(e.target.value)}
              placeholder="Recipient account ID"
              required
            />

            {/* Display-size amount input */}
            <div>
              <label className="micro-label mb-3 block">
                You send ({sourceAccount?.currency || 'TRY'})
              </label>
              <div className="relative">
                <span className="absolute left-4 top-1/2 -translate-y-1/2 text-h3 text-ari-400 font-display">
                  {sourceAccount?.currency === 'EUR' ? '\u20AC' : '\u20BA'}
                </span>
                <input
                  type="text"
                  inputMode="decimal"
                  value={cbAmount}
                  onChange={(e) => setCbAmount(e.target.value.replace(/[^0-9.]/g, ''))}
                  placeholder="0.00"
                  className="w-full h-16 pl-10 pr-4 bg-ari-50 border border-ari-200 rounded-xl text-h2 text-ari-900 font-display transition-all duration-base focus:outline-none focus:bg-white focus:border-ari-900 focus:ring-1 focus:ring-ari-900/10"
                  required
                />
              </div>
              {sourceAccount && (
                <p className="mt-2 text-caption text-ari-400">
                  Available: {formatAmount(sourceAccount.balance, sourceAccount.currency)}
                </p>
              )}
              {cbAmount && sourceAccount && parseFloat(cbAmount) > parseFloat(sourceAccount.balance) && (
                <p className="mt-1 text-caption text-ari-red">Insufficient balance</p>
              )}
            </div>

            <Button type="submit" fullWidth loading={cbLoading} size="lg">
              {cbSameCurrency ? 'Send' : 'Get quote'} <ArrowRight size={16} strokeWidth={1.5} className="ml-1" />
            </Button>
          </form>
        </Card>
      )}

      {/* ===== CROSS-BORDER: STEP 2 - QUOTE REVIEW ===== */}
      {tab === 'crossBorder' && cbStep === 'quote' && cbQuote && (
        <Card>
          {cbError && (
            <motion.div
              initial={{ opacity: 0, y: -8 }}
              animate={{ opacity: 1, y: 0 }}
              role="alert"
              className="mb-5 p-4 bg-ari-red/5 border border-ari-red/20 rounded-xl text-body-sm text-ari-red"
            >
              {cbError}
            </motion.div>
          )}

          <div className="space-y-6">
            {/* Quote header with countdown ring */}
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-h3 font-display text-ari-900">FX Quote</h3>
                <p className="text-caption text-ari-500 mt-0.5">Review and confirm your transfer</p>
              </div>
              <CountdownRing seconds={cbQuoteCountdown} total={cbQuoteTotal} />
            </div>

            {/* Quote amounts — large typography */}
            <div className="bg-ari-50 rounded-2xl p-6">
              <div className="grid grid-cols-[1fr,auto,1fr] items-center gap-4">
                <div>
                  <p className="micro-label">You send</p>
                  <p className="text-h2 font-display text-ari-900 mt-2 tracking-tight">
                    {formatAmount(cbQuote.sourceAmount, cbQuote.sourceCurrency)}
                  </p>
                  <p className="text-caption text-ari-400 mt-1">{cbQuote.sourceCurrency}</p>
                </div>
                <div className="flex items-center justify-center w-10 h-10 rounded-full bg-white border border-ari-200">
                  <ArrowRight size={16} className="text-ari-500" />
                </div>
                <div className="text-right">
                  <p className="micro-label">They receive</p>
                  <p className="text-h2 font-display text-ari-900 mt-2 tracking-tight">
                    {formatAmount(cbQuote.targetAmount, cbQuote.targetCurrency)}
                  </p>
                  <p className="text-caption text-ari-400 mt-1">{cbQuote.targetCurrency}</p>
                </div>
              </div>
            </div>

            {/* Quote details table */}
            <div className="space-y-0 divide-y divide-ari-100">
              <div className="flex justify-between py-3">
                <span className="text-body-sm text-ari-500">Exchange rate</span>
                <span className="text-body-sm text-ari-900 font-medium font-mono">
                  1 {cbQuote.sourceCurrency} = {parseFloat(cbQuote.customerRate).toFixed(6)} {cbQuote.targetCurrency}
                </span>
              </div>
              <div className="flex justify-between py-3">
                <span className="text-body-sm text-ari-500">Spread</span>
                <span className="text-body-sm text-ari-900 font-medium">{cbQuote.spread}%</span>
              </div>
              <div className="flex justify-between py-3">
                <span className="text-body-sm text-ari-500">Estimated delivery</span>
                <span className="text-body-sm text-ari-900 font-medium">~2 minutes</span>
              </div>
              <div className="flex justify-between py-3">
                <span className="text-body-sm text-ari-700 font-medium">Total cost</span>
                <span className="text-body-sm text-ari-900 font-semibold">
                  {formatAmount(
                    parseFloat(cbQuote.sourceAmount) * (1 + parseFloat(cbQuote.spread) / 100),
                    cbQuote.sourceCurrency
                  )}
                </span>
              </div>
            </div>

            {/* Trust signal */}
            <p className="text-caption text-ari-400 text-center">
              Secured by bank-grade encryption
            </p>

            {/* Actions */}
            <div className="flex gap-3">
              <Button
                variant="secondary"
                size="lg"
                className="flex-1"
                onClick={() => {
                  setCbStep('form');
                  setCbQuote(null);
                  if (countdownRef.current) {
                    clearInterval(countdownRef.current);
                    countdownRef.current = null;
                  }
                }}
              >
                Cancel
              </Button>
              <Button
                size="lg"
                className="flex-1"
                loading={cbLoading}
                onClick={handleConfirmCrossBorder}
                disabled={cbQuoteCountdown <= 0}
              >
                Confirm &amp; send
              </Button>
            </div>
          </div>
        </Card>
      )}

      {/* ===== CROSS-BORDER: STEP 3 - PROGRESS ===== */}
      {tab === 'crossBorder' && cbStep === 'progress' && (
        <div className="space-y-4">
          {/* Chain visualization */}
          <ChainVisualization
            sourceCurrency={sourceAccount?.currency || 'TRY'}
            targetCurrency={getTargetCurrency()}
            sameCurrency={cbSameCurrency}
          />

          <Card>
            <div className="space-y-6">
              {cbQuote && (
                <div className="text-center pb-4 border-b border-ari-100">
                  <p className="text-body-sm text-ari-500">
                    Sending {formatAmount(cbQuote.sourceAmount, cbQuote.sourceCurrency)} &rarr; {formatAmount(cbQuote.targetAmount, cbQuote.targetCurrency)}
                  </p>
                </div>
              )}
              <TransferProgress steps={progressSteps} />
              <div className="flex items-center justify-center gap-2 pt-2">
                <AvalancheBadge label="Powered by Avalanche Teleporter" size="md" />
              </div>
            </div>
          </Card>
        </div>
      )}

      {/* ===== CROSS-BORDER: STEP 4 - SUCCESS ===== */}
      {tab === 'crossBorder' && cbStep === 'success' && (
        <Card>
          <div className="text-center py-10 space-y-5">
            <motion.div
              initial={{ scale: 0, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              transition={{ type: 'spring', stiffness: 200, damping: 15, delay: 0.1 }}
              className="w-20 h-20 bg-ari-green/10 rounded-full flex items-center justify-center mx-auto"
            >
              <motion.div
                initial={{ scale: 0 }}
                animate={{ scale: 1 }}
                transition={{ delay: 0.3, type: 'spring', stiffness: 300 }}
              >
                <CheckCircle2 size={40} strokeWidth={1.5} className="text-ari-green" />
              </motion.div>
            </motion.div>

            <div>
              <h3 className="text-h1 font-display text-ari-900 tracking-tight">Transfer complete</h3>
              <p className="text-body-sm text-ari-500 mt-2">
                {cbQuote
                  ? `${formatAmount(cbQuote.targetAmount, cbQuote.targetCurrency)} delivered`
                  : cbAmount
                    ? `${formatAmount(cbAmount, getSourceAccount()?.currency || 'TRY')} delivered`
                    : 'Your transfer has been completed.'}
              </p>
            </div>

            {cbPaymentId && (
              <div className="inline-flex items-center gap-2 bg-ari-50 px-4 py-2 rounded-xl">
                <span className="text-caption text-ari-500">Payment ID</span>
                <span className="text-caption text-ari-900 font-mono font-medium">{cbPaymentId}</span>
              </div>
            )}

            <div className="pt-1">
              <AvalancheBadge label="Settled on Avalanche" size="md" />
            </div>

            <p className="text-caption text-ari-400">
              {new Date().toLocaleDateString('en-GB', { month: 'short', day: 'numeric', year: 'numeric' })},{' '}
              {new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })}
            </p>

            {/* Settlement details (collapsed by default) */}
            {progressSteps.length > 0 && (
              <div className="mt-6 text-left border-t border-ari-100 pt-6">
                <TransferProgress steps={progressSteps} />
              </div>
            )}

            <div className="flex gap-3 pt-4">
              <Button variant="secondary" size="lg" className="flex-1" onClick={resetCrossBorder}>
                Make another transfer
              </Button>
              <Link
                href={"/history" as const}
                className="flex-1 inline-flex items-center justify-center rounded-xl px-4 h-12 text-body-sm font-medium text-ari-700 border border-ari-200 hover:bg-ari-50 hover:border-ari-300 transition-all duration-base focus:outline-none focus:ring-2 focus:ring-ari-900/10 focus:ring-offset-2"
              >
                View in history
              </Link>
            </div>
          </div>
        </Card>
      )}
    </div>
  );
}
