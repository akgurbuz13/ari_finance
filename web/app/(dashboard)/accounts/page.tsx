'use client';

import { useState, useEffect } from 'react';
import api from '../../../lib/api/client';
import type { Account } from '../../../lib/api/types';
import Card from '../../../components/ui/Card';
import Button from '../../../components/ui/Button';

export default function AccountsPage() {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);

  const loadAccounts = async () => {
    try {
      const { data } = await api.get<Account[]>('/accounts');
      setAccounts(data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadAccounts(); }, []);

  const createAccount = async (currency: string) => {
    setCreating(true);
    try {
      await api.post('/accounts', { currency });
      await loadAccounts();
    } finally {
      setCreating(false);
    }
  };

  if (loading) {
    return <div className="flex items-center justify-center h-64 text-gray-400">Loading...</div>;
  }

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-2xl font-bold text-black">Accounts</h1>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => createAccount('TRY')} disabled={creating}>
            + TRY Account
          </Button>
          <Button variant="secondary" onClick={() => createAccount('EUR')} disabled={creating}>
            + EUR Account
          </Button>
        </div>
      </div>

      {accounts.length === 0 ? (
        <Card>
          <p className="text-center text-gray-500 py-8">
            No accounts yet. Create one to get started.
          </p>
        </Card>
      ) : (
        <div className="space-y-4">
          {accounts.map((account) => (
            <Card key={account.id}>
              <div className="flex justify-between items-center">
                <div>
                  <p className="text-sm text-gray-500 uppercase tracking-wide">
                    {account.currency} {account.accountType.replace('_', ' ')}
                  </p>
                  <p className="text-2xl font-bold text-black mt-1">
                    {account.currency === 'TRY' ? '₺' : '€'}
                    {parseFloat(account.balance).toLocaleString('en-US', {
                      minimumFractionDigits: 2,
                    })}
                  </p>
                </div>
                <div className="text-right">
                  <span className={`text-xs px-2 py-1 rounded-full ${
                    account.status === 'active'
                      ? 'bg-green-100 text-green-700'
                      : 'bg-red-100 text-red-700'
                  }`}>
                    {account.status}
                  </span>
                  <p className="text-xs text-gray-400 mt-2 font-mono">{account.id}</p>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
