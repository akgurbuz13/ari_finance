'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import api from '../../../lib/api/client';
import type { Account, FxQuote } from '../../../lib/api/types';
import Input from '../../../components/ui/Input';
import Button from '../../../components/ui/Button';
import Card from '../../../components/ui/Card';

type Tab = 'domestic' | 'crossBorder';

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
  const [cbStep, setCbStep] = useState<'form' | 'quote' | 'success'>('form');
  const [cbLoading, setCbLoading] = useState(false);
  const [cbError, setCbError] = useState('');
  const [cbPaymentId, setCbPaymentId] = useState('');
  const countdownRef = useRef<NodeJS.Timeout | null>(null);

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

  // Cleanup interval on unmount
  useEffect(() => {
    return () => {
      if (countdownRef.current) clearInterval(countdownRef.current);
    };
  }, []);

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

  const getSourceAccount = useCallback(() => {
    return accounts.find(a => a.id === cbSourceAccount);
  }, [accounts, cbSourceAccount]);

  const getTargetCurrency = useCallback(() => {
    const source = getSourceAccount();
    if (!source) return 'EUR';
    return source.currency === 'TRY' ? 'EUR' : 'TRY';
  }, [getSourceAccount]);

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
      setCbStep('success');
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
    if (countdownRef.current) {
      clearInterval(countdownRef.current);
      countdownRef.current = null;
    }
  };

  const currencySymbol = (c: string) => c === 'TRY' ? '₺' : '€';

  if (loadingAccounts) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-400">Loading...</div>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-black">Transfer</h1>

      {/* Tabs */}
      <div className="flex border-b border-gray-200">
        {(['domestic', 'crossBorder'] as Tab[]).map((t) => (
          <button
            key={t}
            onClick={() => { setTab(t); setSuccess(''); setError(''); }}
            className={`px-6 py-3 text-sm font-medium border-b-2 transition-colors ${
              tab === t
                ? 'border-black text-black'
                : 'border-transparent text-gray-400 hover:text-gray-600'
            }`}
          >
            {t === 'domestic' ? 'Domestic' : 'Cross-Border'}
          </button>
        ))}
      </div>

      {success && (
        <div className="p-3 bg-green-50 border border-green-200 rounded-lg text-sm text-green-700">
          {success}
        </div>
      )}
      {error && (
        <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-600">
          {error}
        </div>
      )}

      {tab === 'domestic' && (
        <Card>
          <form onSubmit={handleDomestic} className="space-y-5">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">From Account</label>
              <select
                value={senderAccountId}
                onChange={(e) => setSenderAccountId(e.target.value)}
                className="w-full px-4 py-3 border border-gray-300 rounded-lg text-black focus:outline-none focus:ring-2 focus:ring-black"
                required
              >
                <option value="">Select account</option>
                {accounts.map(a => (
                  <option key={a.id} value={a.id}>
                    {a.currency} — {currencySymbol(a.currency)}{parseFloat(a.balance).toLocaleString('en-US', { minimumFractionDigits: 2 })}
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
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Currency</label>
              <select
                value={currency}
                onChange={(e) => setCurrency(e.target.value)}
                className="w-full px-4 py-3 border border-gray-300 rounded-lg text-black focus:outline-none focus:ring-2 focus:ring-black"
              >
                <option value="TRY">TRY (₺)</option>
                <option value="EUR">EUR (€)</option>
              </select>
            </div>
            <Button type="submit" className="w-full" loading={loading}>
              {loading ? 'Processing...' : 'Send Money'}
            </Button>
          </form>
        </Card>
      )}

      {tab === 'crossBorder' && cbStep === 'form' && (
        <Card>
          {cbError && (
            <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-600">
              {cbError}
            </div>
          )}
          <form onSubmit={handleGetQuote} className="space-y-5">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">From Account</label>
              <select
                value={cbSourceAccount}
                onChange={(e) => setCbSourceAccount(e.target.value)}
                className="w-full px-4 py-3 border border-gray-300 rounded-lg text-black focus:outline-none focus:ring-2 focus:ring-black"
                required
              >
                <option value="">Select source account</option>
                {accounts.filter(a => a.status === 'active').map(a => (
                  <option key={a.id} value={a.id}>
                    {a.currency} — {currencySymbol(a.currency)}{parseFloat(a.balance).toLocaleString('en-US', { minimumFractionDigits: 2 })}
                  </option>
                ))}
              </select>
            </div>

            {cbSourceAccount && (
              <div className="p-3 bg-gray-50 border border-gray-200 rounded-lg">
                <p className="text-xs text-gray-500">Target currency</p>
                <p className="text-sm font-semibold text-black">
                  {getTargetCurrency()} ({currencySymbol(getTargetCurrency())})
                </p>
              </div>
            )}

            <Input
              label="Recipient Account ID"
              value={cbReceiverAccount}
              onChange={(e) => setCbReceiverAccount(e.target.value)}
              placeholder="Recipient account ID (different currency)"
              required
            />

            <Input
              label={`Amount (${getSourceAccount()?.currency || 'source currency'})`}
              type="number"
              value={cbAmount}
              onChange={(e) => setCbAmount(e.target.value)}
              placeholder="0.00"
              min="0.01"
              step="0.01"
              required
            />

            <Button type="submit" className="w-full" loading={cbLoading}>
              Get FX Quote
            </Button>
          </form>
        </Card>
      )}

      {tab === 'crossBorder' && cbStep === 'quote' && cbQuote && (
        <Card>
          {cbError && (
            <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-600">
              {cbError}
            </div>
          )}

          <div className="space-y-6">
            {/* Quote timer */}
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-semibold text-black">FX Quote</h3>
              <div className={`text-sm font-mono px-3 py-1 rounded-full ${
                cbQuoteCountdown > 10 ? 'bg-green-100 text-green-700' :
                cbQuoteCountdown > 5 ? 'bg-yellow-100 text-yellow-700' :
                'bg-red-100 text-red-700'
              }`}>
                {cbQuoteCountdown}s remaining
              </div>
            </div>

            {/* Quote details */}
            <div className="bg-gray-50 rounded-xl p-5 space-y-4">
              <div className="flex justify-between items-center">
                <div>
                  <p className="text-xs text-gray-500 uppercase tracking-wide">You send</p>
                  <p className="text-xl font-bold text-black">
                    {currencySymbol(cbQuote.sourceCurrency)}
                    {parseFloat(cbQuote.sourceAmount).toLocaleString('en-US', { minimumFractionDigits: 2 })}
                  </p>
                </div>
                <div className="text-2xl text-gray-300">→</div>
                <div className="text-right">
                  <p className="text-xs text-gray-500 uppercase tracking-wide">They receive</p>
                  <p className="text-xl font-bold text-black">
                    {currencySymbol(cbQuote.targetCurrency)}
                    {parseFloat(cbQuote.targetAmount).toLocaleString('en-US', { minimumFractionDigits: 2 })}
                  </p>
                </div>
              </div>

              <div className="border-t border-gray-200 pt-3 space-y-2">
                <div className="flex justify-between text-sm">
                  <span className="text-gray-500">Exchange rate</span>
                  <span className="text-black font-medium">
                    1 {cbQuote.sourceCurrency} = {parseFloat(cbQuote.customerRate).toFixed(6)} {cbQuote.targetCurrency}
                  </span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-gray-500">Spread</span>
                  <span className="text-black font-medium">{cbQuote.spread}%</span>
                </div>
              </div>
            </div>

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
                Confirm & Send
              </Button>
            </div>
          </div>
        </Card>
      )}

      {tab === 'crossBorder' && cbStep === 'success' && (
        <Card>
          <div className="text-center py-8 space-y-4">
            <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto">
              <svg className="w-8 h-8 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            </div>
            <h3 className="text-lg font-semibold text-black">Transfer initiated</h3>
            <p className="text-sm text-gray-500">
              Your cross-border transfer has been submitted and is being processed.
            </p>
            {cbPaymentId && (
              <p className="text-xs text-gray-400 font-mono">
                Payment ID: {cbPaymentId}
              </p>
            )}
            <Button variant="secondary" onClick={resetCrossBorder}>
              Make another transfer
            </Button>
          </div>
        </Card>
      )}
    </div>
  );
}
