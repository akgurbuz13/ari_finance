'use client';

import { useState } from 'react';
import api from '../../../lib/api/client';
import Input from '../../../components/ui/Input';
import Button from '../../../components/ui/Button';
import Card from '../../../components/ui/Card';

type Tab = 'domestic' | 'crossBorder';

export default function TransferPage() {
  const [tab, setTab] = useState<Tab>('domestic');
  const [senderAccountId, setSenderAccountId] = useState('');
  const [receiverAccountId, setReceiverAccountId] = useState('');
  const [amount, setAmount] = useState('');
  const [currency, setCurrency] = useState('TRY');
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState('');
  const [error, setError] = useState('');

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

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-black">Transfer</h1>

      {/* Tabs */}
      <div className="flex border-b border-gray-200">
        {(['domestic', 'crossBorder'] as Tab[]).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
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
            <Input
              label="From Account ID"
              value={senderAccountId}
              onChange={(e) => setSenderAccountId(e.target.value)}
              placeholder="Your account ID"
              required
            />
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
            <Button type="submit" className="w-full" disabled={loading}>
              {loading ? 'Processing...' : 'Send Money'}
            </Button>
          </form>
        </Card>
      )}

      {tab === 'crossBorder' && (
        <Card>
          <div className="text-center py-8">
            <p className="text-gray-500 mb-2">Cross-border transfers require an FX quote.</p>
            <p className="text-sm text-gray-400">
              First get a quote at the FX rates page, then use the quote ID to initiate the transfer.
            </p>
          </div>
        </Card>
      )}
    </div>
  );
}
