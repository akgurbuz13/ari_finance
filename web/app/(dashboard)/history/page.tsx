'use client';

import { useState, useEffect } from 'react';
import api from '../../../lib/api/client';
import type { Account, Transaction } from '../../../lib/api/types';
import Card from '../../../components/ui/Card';

export default function HistoryPage() {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [selectedAccount, setSelectedAccount] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get<Account[]>('/accounts').then(({ data }) => {
      setAccounts(data);
      if (data.length > 0) {
        setSelectedAccount(data[0].id);
      }
      setLoading(false);
    });
  }, []);

  useEffect(() => {
    if (!selectedAccount) return;
    setLoading(true);
    api.get<Transaction[]>(`/transactions/account/${selectedAccount}?limit=50`)
      .then(({ data }) => setTransactions(data))
      .finally(() => setLoading(false));
  }, [selectedAccount]);

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-2xl font-bold text-black">Transaction History</h1>
        {accounts.length > 0 && (
          <select
            value={selectedAccount}
            onChange={(e) => setSelectedAccount(e.target.value)}
            className="px-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-black"
          >
            {accounts.map((a) => (
              <option key={a.id} value={a.id}>
                {a.currency} Account
              </option>
            ))}
          </select>
        )}
      </div>

      <Card>
        {loading ? (
          <div className="text-center py-8 text-gray-400">Loading...</div>
        ) : transactions.length === 0 ? (
          <div className="text-center py-8 text-gray-400">No transactions found</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-gray-100">
                  <th className="text-left py-3 px-2 text-xs font-medium text-gray-500 uppercase">Type</th>
                  <th className="text-left py-3 px-2 text-xs font-medium text-gray-500 uppercase">Status</th>
                  <th className="text-left py-3 px-2 text-xs font-medium text-gray-500 uppercase">Reference</th>
                  <th className="text-left py-3 px-2 text-xs font-medium text-gray-500 uppercase">Date</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {transactions.map((tx) => (
                  <tr key={tx.id} className="hover:bg-gray-50 transition-colors">
                    <td className="py-3 px-2 text-sm font-medium text-black capitalize">
                      {tx.type.replace('_', ' ')}
                    </td>
                    <td className="py-3 px-2">
                      <span className={`text-xs px-2 py-1 rounded-full ${
                        tx.status === 'completed'
                          ? 'bg-green-100 text-green-700'
                          : tx.status === 'failed'
                          ? 'bg-red-100 text-red-700'
                          : 'bg-yellow-100 text-yellow-700'
                      }`}>
                        {tx.status}
                      </span>
                    </td>
                    <td className="py-3 px-2 text-sm text-gray-500 font-mono text-xs">
                      {tx.referenceId || '—'}
                    </td>
                    <td className="py-3 px-2 text-sm text-gray-500">
                      {new Date(tx.createdAt).toLocaleDateString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
