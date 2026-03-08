'use client';

import { useState, useEffect } from 'react';
import { ArrowUpRight, ArrowDownLeft, ArrowLeftRight, Clock } from 'lucide-react';
import { clsx } from 'clsx';
import api from '../../../lib/api/client';
import type { Account, Transaction } from '../../../lib/api/types';
import Card from '../../../components/ui/Card';
import StatusPill from '../../../components/ui/StatusPill';
import Skeleton, { SkeletonCard } from '../../../components/ui/Skeleton';

const txTypeIcons: Record<string, typeof ArrowUpRight> = {
  deposit: ArrowDownLeft,
  withdrawal: ArrowUpRight,
  p2p_transfer: ArrowUpRight,
  fx_conversion: ArrowLeftRight,
  cross_border: ArrowLeftRight,
  mint: ArrowDownLeft,
  burn: ArrowUpRight,
  fee: ArrowUpRight,
};

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString('en-GB', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

function HistorySkeleton() {
  return (
    <div className="max-w-dashboard mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <Skeleton variant="text" className="w-48 h-8" />
        <Skeleton variant="rectangular" className="w-40 h-12" />
      </div>
      <SkeletonCard />
      <div className="space-y-3">
        {[1, 2, 3, 4, 5].map((i) => (
          <div key={i} className="flex items-center gap-3">
            <Skeleton variant="circular" className="w-8 h-8" />
            <div className="flex-1 space-y-1">
              <Skeleton variant="text" className="w-32 h-4" />
              <Skeleton variant="text" className="w-20 h-3" />
            </div>
            <Skeleton variant="rectangular" className="w-20 h-6 rounded-full" />
          </div>
        ))}
      </div>
    </div>
  );
}

export default function HistoryPage() {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [selectedAccount, setSelectedAccount] = useState('');
  const [loading, setLoading] = useState(true);
  const [loadingTx, setLoadingTx] = useState(false);
  const [limit, setLimit] = useState(50);
  const [hasMore, setHasMore] = useState(false);
  const [typeFilter, setTypeFilter] = useState('all');
  const [expandedTx, setExpandedTx] = useState<string | null>(null);

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
    setLoadingTx(true);
    api.get<Transaction[]>(`/transactions/account/${selectedAccount}?limit=${limit}`)
      .then(({ data }) => {
        setTransactions(data);
        setHasMore(data.length >= limit);
      })
      .finally(() => setLoadingTx(false));
  }, [selectedAccount, limit]);

  const loadMore = () => {
    setLimit((prev) => prev + 50);
  };

  if (loading) {
    return <HistorySkeleton />;
  }

  return (
    <div className="max-w-dashboard mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-h2 font-display text-ova-900">Transaction History</h1>
        {accounts.length > 0 && (
          <div className="inline-flex bg-ova-100 rounded-xl p-1 gap-1">
            {accounts.map((a) => (
              <button
                key={a.id}
                onClick={() => { setSelectedAccount(a.id); setLimit(50); }}
                className={clsx(
                  'px-4 py-2 rounded-lg text-body-sm font-medium transition-all duration-fast cursor-pointer',
                  selectedAccount === a.id
                    ? 'bg-white text-ova-900 shadow-sm'
                    : 'text-ova-500 hover:text-ova-700'
                )}
              >
                {a.currency === 'TRY' ? '🇹🇷' : '🇪🇺'} {a.currency}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Type filter chips */}
      {transactions.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {['all', 'p2p_transfer', 'cross_border', 'fx_conversion', 'deposit', 'fee'].map((type) => (
            <button
              key={type}
              onClick={() => setTypeFilter(type)}
              className={clsx(
                'px-3 py-1.5 rounded-full text-caption font-medium transition-colors duration-fast cursor-pointer',
                typeFilter === type
                  ? 'bg-ova-navy text-white'
                  : 'bg-ova-100 text-ova-500 hover:bg-ova-200'
              )}
            >
              {type === 'all' ? 'All' : type.replace(/_/g, ' ')}
            </button>
          ))}
        </div>
      )}

      {/* Transaction list */}
      <Card>
        {loadingTx ? (
          <div className="space-y-4">
            {[1, 2, 3, 4, 5].map((i) => (
              <div key={i} className="flex items-center gap-3">
                <Skeleton variant="circular" className="w-8 h-8" />
                <div className="flex-1 space-y-1">
                  <Skeleton variant="text" className="w-32 h-4" />
                  <Skeleton variant="text" className="w-20 h-3" />
                </div>
                <Skeleton variant="rectangular" className="w-20 h-6 rounded-full" />
              </div>
            ))}
          </div>
        ) : transactions.length === 0 ? (
          <div className="text-center py-8">
            <div className="flex h-14 w-14 items-center justify-center rounded-full bg-ova-100 mx-auto mb-4">
              <Clock size={28} strokeWidth={1.5} className="text-ova-400" />
            </div>
            <p className="text-body-sm text-ova-400">No transactions found</p>
          </div>
        ) : (
          <>
            <div className="divide-y divide-ova-200">
              {(typeFilter === 'all' ? transactions : transactions.filter(tx => tx.type === typeFilter)).map((tx) => {
                const Icon = txTypeIcons[tx.type] || ArrowUpRight;
                return (
                  <div
                    key={tx.id}
                    className="cursor-pointer hover:bg-ova-50 transition-colors duration-fast -mx-6 px-6"
                    onClick={() => setExpandedTx(expandedTx === tx.id ? null : tx.id)}
                  >
                    <div className="flex items-center justify-between py-3">
                      <div className="flex items-center gap-3">
                        <div className="flex h-8 w-8 items-center justify-center rounded-full bg-ova-100">
                          <Icon size={16} strokeWidth={1.5} className="text-ova-500" />
                        </div>
                        <div>
                          <p className="text-body-sm font-medium text-ova-900 capitalize">
                            {tx.type.replace(/_/g, ' ')}
                          </p>
                          <div className="flex items-center gap-2">
                            <span className="text-caption text-ova-400">
                              {formatDate(tx.createdAt)}
                            </span>
                            {tx.referenceId && (
                              <>
                                <span className="text-caption text-ova-300">&middot;</span>
                                <span className="font-mono text-caption text-ova-400">
                                  {tx.referenceId}
                                </span>
                              </>
                            )}
                          </div>
                        </div>
                      </div>
                      <StatusPill
                        variant={
                          tx.status === 'completed' ? 'success' :
                          tx.status === 'pending' ? 'warning' :
                          tx.status === 'failed' ? 'error' : 'neutral'
                        }
                      >
                        {tx.status}
                      </StatusPill>
                    </div>
                    {expandedTx === tx.id && (
                      <div className="mt-2 pb-3 pl-11 space-y-1">
                        <p className="text-caption text-ova-400">
                          Transaction ID: <span className="font-mono text-ova-500">{tx.id}</span>
                        </p>
                        {tx.referenceId && (
                          <p className="text-caption text-ova-400">
                            Reference: <span className="font-mono text-ova-500">{tx.referenceId}</span>
                          </p>
                        )}
                        <p className="text-caption text-ova-400">
                          Date: {new Date(tx.createdAt).toLocaleString('en-GB')}
                        </p>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
            {hasMore && (
              <button
                onClick={loadMore}
                className="block w-full mt-4 text-center text-body-sm text-ova-blue hover:underline cursor-pointer py-2"
              >
                Load more transactions
              </button>
            )}
          </>
        )}
      </Card>
    </div>
  );
}
