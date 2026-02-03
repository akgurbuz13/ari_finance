'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import api from '../../../lib/api/client';
import type { Account, Transaction } from '../../../lib/api/types';
import Card from '../../../components/ui/Card';
import Button from '../../../components/ui/Button';

export default function HomePage() {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        const [acctRes] = await Promise.all([
          api.get<Account[]>('/accounts'),
        ]);
        setAccounts(acctRes.data);

        if (acctRes.data.length > 0) {
          const txRes = await api.get<Transaction[]>(
            `/transactions/account/${acctRes.data[0].id}?limit=5`
          );
          setTransactions(txRes.data);
        }
      } catch {
        // handled by interceptor
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-400">Loading...</div>
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-black">Welcome back</h1>
        <p className="text-gray-500 mt-1">Your account overview</p>
      </div>

      {/* Balance Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {accounts.map((account) => (
          <div
            key={account.id}
            className="bg-black text-white rounded-2xl p-6 flex flex-col justify-between min-h-[160px]"
          >
            <div className="flex justify-between items-start">
              <span className="text-sm text-gray-400 uppercase tracking-wide">
                {account.currency} Wallet
              </span>
              <span className={`text-xs px-2 py-1 rounded-full ${
                account.status === 'active' ? 'bg-green-900 text-green-300' : 'bg-red-900 text-red-300'
              }`}>
                {account.status}
              </span>
            </div>
            <div>
              <p className="text-3xl font-bold tracking-tight">
                {account.currency === 'TRY' ? '₺' : '€'}
                {parseFloat(account.balance).toLocaleString('en-US', {
                  minimumFractionDigits: 2,
                  maximumFractionDigits: 2,
                })}
              </p>
            </div>
          </div>
        ))}

        {accounts.length === 0 && (
          <Card className="col-span-2">
            <div className="text-center py-8">
              <p className="text-gray-500 mb-4">No accounts yet</p>
              <Link href="/accounts">
                <Button>Create your first account</Button>
              </Link>
            </div>
          </Card>
        )}
      </div>

      {/* Quick Actions */}
      <div className="flex gap-4">
        <Link href="/transfer">
          <Button>Send Money</Button>
        </Link>
        <Link href="/transfer">
          <Button variant="secondary">Convert Currency</Button>
        </Link>
      </div>

      {/* Recent Transactions */}
      <Card header="Recent Activity">
        {transactions.length > 0 ? (
          <div className="divide-y divide-gray-100">
            {transactions.map((tx) => (
              <div key={tx.id} className="py-3 flex justify-between items-center">
                <div>
                  <p className="text-sm font-medium text-black capitalize">
                    {tx.type.replace('_', ' ')}
                  </p>
                  <p className="text-xs text-gray-400">{new Date(tx.createdAt).toLocaleDateString()}</p>
                </div>
                <span className={`text-sm font-semibold ${
                  tx.status === 'completed' ? 'text-black' : 'text-gray-400'
                }`}>
                  {tx.status}
                </span>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-sm text-gray-400 text-center py-4">No transactions yet</p>
        )}
        {transactions.length > 0 && (
          <Link href="/history" className="block mt-4 text-center text-sm text-gray-500 hover:text-black">
            View all transactions →
          </Link>
        )}
      </Card>
    </div>
  );
}
