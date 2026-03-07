'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import Link from 'next/link';
import { ArrowRight, ArrowUpRight, Globe, CheckCircle2 } from 'lucide-react';
import { motion } from 'framer-motion';
import api from '../../../lib/api/client';
import type { Account, FxQuote } from '../../../lib/api/types';
import Input from '../../../components/ui/Input';
import Button from '../../../components/ui/Button';
import Card from '../../../components/ui/Card';
import Skeleton, { SkeletonCard } from '../../../components/ui/Skeleton';
import TransferProgress from '../../../components/ui/TransferProgress';
import type { TransferStep } from '../../../components/ui/TransferProgress';

type Tab = 'domestic' | 'crossBorder';

function formatAmount(amount: string | number, currency: string) {
  const num = typeof amount === 'string' ? parseFloat(amount) : amount;
  const symbol = currency === 'TRY' ? '₺' : '€';
  const locale = currency === 'TRY' ? 'tr-TR' : 'de-DE';
  return `${symbol}${num.toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
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
      <div className="max-w-form mx-auto space-y-6">
        <Skeleton variant="text" className="w-32 h-8" />
        <SkeletonCard />
      </div>
    );
  }

  const sourceAccount = getSourceAccount();

  return (
    <div className="max-w-form mx-auto space-y-6">
      <h1 className="text-h2 text-ova-900">Transfer</h1>

      {/* Tabs */}
      <div className="flex border-b border-ova-200">
        {(['domestic', 'crossBorder'] as Tab[]).map((t) => (
          <button
            key={t}
            onClick={() => { setTab(t); setSuccess(''); setError(''); setCbError(''); }}
            className={`px-6 py-3 text-body-sm font-medium border-b-2 transition-colors duration-fast ${
              tab === t
                ? 'border-ova-navy text-ova-900'
                : 'border-transparent text-ova-500 hover:text-ova-700'
            }`}
          >
            {t === 'domestic' ? <><ArrowUpRight size={16} className="mr-1.5 inline" /> Domestic</> : <><Globe size={16} className="mr-1.5 inline" /> Cross-Border</>}
          </button>
        ))}
      </div>

      {/* Domestic success/error banners */}
      {success && (
        <div role="alert" className="p-3 bg-ova-green-light border border-ova-green/20 rounded-xl text-body-sm text-ova-green">
          {success}
        </div>
      )}
      {error && (
        <div role="alert" className="p-3 bg-ova-red-light border border-ova-red/20 rounded-xl text-body-sm text-ova-red">
          {error}
        </div>
      )}

      {/* ===== DOMESTIC TAB ===== */}
      {tab === 'domestic' && (
        <Card>
          <form onSubmit={handleDomestic} className="space-y-5">
            <div>
              <label className="block text-body-sm font-medium text-ova-700 mb-3">From Account</label>
              <select
                value={senderAccountId}
                onChange={(e) => setSenderAccountId(e.target.value)}
                className="w-full h-12 px-4 bg-white border border-ova-300 rounded-xl text-ova-900 transition-all duration-base focus:outline-none focus:border-ova-blue focus:ring-2 focus:ring-ova-blue/20 appearance-none cursor-pointer"
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
              <label className="block text-body-sm font-medium text-ova-700 mb-3">Currency</label>
              <select
                value={currency}
                onChange={(e) => setCurrency(e.target.value)}
                className="w-full h-12 px-4 bg-white border border-ova-300 rounded-xl text-ova-900 transition-all duration-base focus:outline-none focus:border-ova-blue focus:ring-2 focus:ring-ova-blue/20 appearance-none cursor-pointer"
              >
                <option value="TRY">TRY ({'\u20BA'})</option>
                <option value="EUR">EUR ({'\u20AC'})</option>
              </select>
            </div>
            <Button type="submit" fullWidth loading={loading}>
              Send Money
            </Button>
          </form>
        </Card>
      )}

      {/* ===== CROSS-BORDER: STEP 1 - FORM ===== */}
      {tab === 'crossBorder' && cbStep === 'form' && (
        <Card>
          {cbError && (
            <div role="alert" className="mb-5 p-3 bg-ova-red-light border border-ova-red/20 rounded-xl text-body-sm text-ova-red">
              {cbError}
            </div>
          )}
          <form onSubmit={cbSameCurrency ? handleSameCurrencyTransfer : handleGetQuote} className="space-y-5">
            <div>
              <label className="block text-body-sm font-medium text-ova-700 mb-3">From Account</label>
              <select
                value={cbSourceAccount}
                onChange={(e) => setCbSourceAccount(e.target.value)}
                className="w-full h-12 px-4 bg-white border border-ova-300 rounded-xl text-ova-900 transition-all duration-base focus:outline-none focus:border-ova-blue focus:ring-2 focus:ring-ova-blue/20 appearance-none cursor-pointer"
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
              <div className="space-y-3">
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={cbSameCurrency}
                    onChange={(e) => setCbSameCurrency(e.target.checked)}
                    className="w-4 h-4 rounded border-ova-300 text-ova-blue focus:ring-ova-blue"
                  />
                  <span className="text-body-sm text-ova-700">Same currency (different region)</span>
                </label>
                <div className="p-3 bg-ova-100 border border-ova-200 rounded-xl">
                  <p className="text-caption text-ova-500 uppercase tracking-wide">Target currency</p>
                  <p className="text-body-sm font-semibold text-ova-900">
                    {getTargetCurrency()} ({getTargetCurrency() === 'TRY' ? '₺' : '€'})
                    {cbSameCurrency && <span className="text-caption text-ova-400 ml-2">(different region)</span>}
                  </p>
                </div>
              </div>
            )}

            <Input
              label="Recipient Account ID"
              value={cbReceiverAccount}
              onChange={(e) => setCbReceiverAccount(e.target.value)}
              placeholder="Recipient account ID (different currency)"
              required
            />

            {/* Display-size amount input */}
            <div>
              <label className="block text-body-sm font-medium text-ova-700 mb-3">
                You send ({sourceAccount?.currency || 'TRY'})
              </label>
              <div className="relative">
                <span className="absolute left-4 top-1/2 -translate-y-1/2 text-h3 text-ova-500 amount">
                  {sourceAccount?.currency === 'EUR' ? '€' : '₺'}
                </span>
                <input
                  type="text"
                  inputMode="decimal"
                  value={cbAmount}
                  onChange={(e) => setCbAmount(e.target.value.replace(/[^0-9.]/g, ''))}
                  placeholder="0.00"
                  className="w-full h-16 pl-10 pr-4 bg-white border border-ova-300 rounded-xl text-h2 text-ova-900 amount transition-all duration-base focus:outline-none focus:border-ova-blue focus:ring-2 focus:ring-ova-blue/20"
                  required
                />
              </div>
              {sourceAccount && (
                <p className="mt-2 text-caption text-ova-400">
                  Available: {formatAmount(sourceAccount.balance, sourceAccount.currency)}
                </p>
              )}
              {cbAmount && sourceAccount && parseFloat(cbAmount) > parseFloat(sourceAccount.balance) && (
                <p className="mt-1 text-caption text-ova-red">Insufficient balance</p>
              )}
            </div>

            <Button type="submit" fullWidth loading={cbLoading}>
              {cbSameCurrency ? 'Send' : 'Get quote'} <ArrowRight size={16} strokeWidth={1.5} className="ml-1" />
            </Button>
          </form>
        </Card>
      )}

      {/* ===== CROSS-BORDER: STEP 2 - QUOTE REVIEW ===== */}
      {tab === 'crossBorder' && cbStep === 'quote' && cbQuote && (
        <Card>
          {cbError && (
            <div role="alert" className="mb-5 p-3 bg-ova-red-light border border-ova-red/20 rounded-xl text-body-sm text-ova-red">
              {cbError}
            </div>
          )}

          <div className="space-y-6">
            {/* Quote timer */}
            <div className="flex items-center justify-between">
              <h3 className="text-h3 text-ova-900">FX Quote</h3>
              <div className={`text-body-sm font-mono px-3 py-1 rounded-full ${
                cbQuoteCountdown > 10 ? 'bg-ova-green-light text-ova-green' :
                cbQuoteCountdown > 5 ? 'bg-ova-amber-light text-ova-amber' :
                'bg-ova-red-light text-ova-red'
              }`}>
                {cbQuoteCountdown}s remaining
              </div>
            </div>

            {/* Quote amounts */}
            <div className="bg-ova-50 rounded-xl p-5">
              <div className="flex justify-between items-center">
                <div>
                  <p className="text-caption text-ova-500 uppercase tracking-wide">You send</p>
                  <p className="text-h2 text-ova-900 amount mt-1">
                    {formatAmount(cbQuote.sourceAmount, cbQuote.sourceCurrency)}
                  </p>
                </div>
                <div className="text-h2 text-ova-300">&rarr;</div>
                <div className="text-right">
                  <p className="text-caption text-ova-500 uppercase tracking-wide">They receive</p>
                  <p className="text-h2 text-ova-900 amount mt-1">
                    {formatAmount(cbQuote.targetAmount, cbQuote.targetCurrency)}
                  </p>
                </div>
              </div>
            </div>

            {/* Quote details */}
            <div className="space-y-3 border-t border-ova-200 pt-4">
              <div className="flex justify-between text-body-sm">
                <span className="text-ova-500">Exchange rate</span>
                <span className="text-ova-900 font-medium">
                  1 {cbQuote.sourceCurrency} = {parseFloat(cbQuote.customerRate).toFixed(6)} {cbQuote.targetCurrency}
                </span>
              </div>
              <div className="flex justify-between text-body-sm">
                <span className="text-ova-500">Spread</span>
                <span className="text-ova-900 font-medium">{cbQuote.spread}%</span>
              </div>
              <div className="flex justify-between text-body-sm">
                <span className="text-ova-500">Arrives in</span>
                <span className="text-ova-900 font-medium">~2 minutes</span>
              </div>
              <div className="flex justify-between text-body-sm font-medium border-t border-ova-200 pt-3 mt-3">
                <span className="text-ova-700">Total cost</span>
                <span className="text-ova-900">
                  {formatAmount(
                    parseFloat(cbQuote.sourceAmount) * (1 + parseFloat(cbQuote.spread) / 100),
                    cbQuote.sourceCurrency
                  )}
                </span>
              </div>
            </div>

            {/* Trust signal */}
            <p className="text-caption text-ova-400 text-center">
              Your transfer is protected by BDDK and PSD2 regulations
            </p>

            {/* Actions */}
            <div className="flex gap-3">
              <Button
                variant="secondary"
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
        <Card>
          <div className="space-y-6">
            <div className="text-center">
              <p className="text-body-sm text-ova-500">
                Sending {cbQuote ? formatAmount(cbQuote.sourceAmount, cbQuote.sourceCurrency) : ''} &rarr; {cbQuote ? formatAmount(cbQuote.targetAmount, cbQuote.targetCurrency) : ''}
              </p>
            </div>
            <TransferProgress steps={progressSteps} />
          </div>
        </Card>
      )}

      {/* ===== CROSS-BORDER: STEP 4 - SUCCESS ===== */}
      {tab === 'crossBorder' && cbStep === 'success' && (
        <Card>
          <div className="text-center py-8 space-y-4">
            <motion.div
              initial={{ scale: 0, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              transition={{ type: 'spring', stiffness: 200, damping: 15, delay: 0.1 }}
              className="w-16 h-16 bg-ova-green-light rounded-full flex items-center justify-center mx-auto"
            >
              <CheckCircle2 size={32} strokeWidth={1.5} className="text-ova-green" />
            </motion.div>
            <h3 className="text-h2 text-ova-900">Transfer complete</h3>
            <p className="text-body-sm text-ova-500">
              {cbQuote
                ? `${formatAmount(cbQuote.targetAmount, cbQuote.targetCurrency)} delivered`
                : cbAmount
                  ? `${formatAmount(cbAmount, getSourceAccount()?.currency || 'TRY')} delivered`
                  : 'Your transfer has been completed.'}
            </p>
            {cbPaymentId && (
              <p className="text-caption text-ova-400 font-mono">
                Payment ID: {cbPaymentId}
              </p>
            )}
            <p className="text-caption text-ova-400">
              {new Date().toLocaleDateString('en-GB', { month: 'short', day: 'numeric', year: 'numeric' })},{' '}
              {new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })}
            </p>

            {/* Settlement details (collapsed by default) */}
            {progressSteps.length > 0 && (
              <div className="mt-6 text-left">
                <TransferProgress steps={progressSteps} />
              </div>
            )}

            <div className="flex gap-3 pt-4">
              <Button variant="secondary" className="flex-1" onClick={resetCrossBorder}>
                Make another transfer
              </Button>
              <Link
                href={"/history" as const}
                className="flex-1 inline-flex items-center justify-center rounded-xl px-4 h-12 text-body-sm font-medium text-ova-700 hover:bg-ova-100 transition-all duration-base focus:outline-none focus:ring-2 focus:ring-ova-blue focus:ring-offset-2"
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
