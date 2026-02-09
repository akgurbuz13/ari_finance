'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { ArrowUpRight, ArrowDownLeft, ArrowLeftRight } from 'lucide-react';
import api from '../../../lib/api/client';
import type { Account, Transaction } from '../../../lib/api/types';
import { useAuth } from '../../../lib/hooks/useAuth';
import Card from '../../../components/ui/Card';
import { BalanceCard } from '../../../components/ui/Card';
import StatusPill from '../../../components/ui/StatusPill';
import Skeleton, { SkeletonCard } from '../../../components/ui/Skeleton';

function formatCurrency(amount: string, currency: 'TRY' | 'EUR') {
  const num = parseFloat(amount);
  const symbol = currency === 'TRY' ? '₺' : '€';
  const locale = currency === 'TRY' ? 'tr-TR' : 'de-DE';
  return `${symbol}${num.toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString('en-GB', {
    month: 'short',
    day: 'numeric',
  });
}

function getGreeting() {
  const hour = new Date().getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 18) return 'Good afternoon';
  return 'Good evening';
}

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

function DashboardSkeleton() {
  return (
    <div className="max-w-dashboard mx-auto space-y-8">
      <div className="flex items-baseline justify-between">
        <Skeleton variant="text" className="w-48 h-8" />
        <Skeleton variant="text" className="w-32 h-5" />
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <SkeletonCard />
        <SkeletonCard />
      </div>
      <SkeletonCard />
      <SkeletonCard />
    </div>
  );
}

export default function HomePage() {
  const { user } = useAuth();
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(true);

  const today = new Date().toLocaleDateString('en-US', {
    month: 'long',
    day: 'numeric',
    year: 'numeric',
  });

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
    return <DashboardSkeleton />;
  }

  const displayName = user?.firstName || user?.email?.split('@')[0] || 'there';

  return (
    <div className="max-w-dashboard mx-auto space-y-8">
      {/* Personalized greeting */}
      <div className="flex items-baseline justify-between">
        <h1 className="text-h2 text-ova-900">
          {getGreeting()}, {displayName}
        </h1>
        <span className="text-body-sm text-ova-500">{today}</span>
      </div>

      {/* Balance cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {accounts.map((account) => (
          <BalanceCard
            key={account.id}
            currency={account.currency}
            amount={formatCurrency(account.balance, account.currency)}
          />
        ))}

        {accounts.length === 0 && (
          <Card className="col-span-2">
            <div className="text-center py-8">
              <p className="text-body text-ova-500 mb-4">No accounts yet</p>
              <Link href={"/accounts" as const}>
                <button className="inline-flex items-center justify-center font-medium rounded-xl transition-all duration-base bg-ova-navy text-white hover:bg-ova-navy-light hover:shadow-sm px-6 h-12 text-body-sm">
                  Create your first account
                </button>
              </Link>
            </div>
          </Card>
        )}
      </div>

      {/* Quick actions */}
      <Card>
        <div className="flex items-center justify-center gap-8">
          <Link
            href={"/transfer" as const}
            className="flex flex-col items-center gap-2 px-6 py-4 rounded-xl hover:bg-ova-100 transition-colors duration-fast"
          >
            <div className="flex h-10 w-10 items-center justify-center rounded-full bg-ova-navy text-white">
              <ArrowUpRight size={20} strokeWidth={1.5} />
            </div>
            <span className="text-body-sm font-medium text-ova-700">Send</span>
          </Link>
          <Link
            href={"/transfer" as const}
            className="flex flex-col items-center gap-2 px-6 py-4 rounded-xl hover:bg-ova-100 transition-colors duration-fast"
          >
            <div className="flex h-10 w-10 items-center justify-center rounded-full bg-ova-navy text-white">
              <ArrowDownLeft size={20} strokeWidth={1.5} />
            </div>
            <span className="text-body-sm font-medium text-ova-700">Request</span>
          </Link>
          <Link
            href={"/transfer" as const}
            className="flex flex-col items-center gap-2 px-6 py-4 rounded-xl hover:bg-ova-100 transition-colors duration-fast"
          >
            <div className="flex h-10 w-10 items-center justify-center rounded-full bg-ova-navy text-white">
              <ArrowLeftRight size={20} strokeWidth={1.5} />
            </div>
            <span className="text-body-sm font-medium text-ova-700">Convert</span>
          </Link>
        </div>
      </Card>

      {/* Recent activity */}
      <Card header="Recent Activity">
        {transactions.length > 0 ? (
          <div className="divide-y divide-ova-200">
            {transactions.map((tx) => {
              const Icon = txTypeIcons[tx.type] || ArrowUpRight;
              return (
                <div key={tx.id} className="flex items-center justify-between py-3">
                  <div className="flex items-center gap-3">
                    <div className="flex h-8 w-8 items-center justify-center rounded-full bg-ova-100">
                      <Icon size={16} strokeWidth={1.5} className="text-ova-500" />
                    </div>
                    <div>
                      <p className="text-body-sm font-medium text-ova-900 capitalize">
                        {tx.type.replace(/_/g, ' ')}
                      </p>
                      <p className="text-caption text-ova-400">
                        {formatDate(tx.createdAt)}
                      </p>
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
              );
            })}
          </div>
        ) : (
          <p className="text-body-sm text-ova-400 text-center py-6">No transactions yet</p>
        )}
        {transactions.length > 0 && (
          <Link
            href={"/history" as const}
            className="block mt-4 text-center text-body-sm text-ova-blue hover:underline"
          >
            View all transactions &rarr;
          </Link>
        )}
      </Card>

      {/* Account health */}
      <Card>
        <h3 className="text-body-sm font-medium text-ova-700 mb-4">Account Health</h3>
        <div className="flex flex-wrap gap-6">
          <div className="flex items-center gap-2">
            <StatusPill variant={user?.status === 'active' ? 'success' : 'warning'}>
              {user?.status === 'active' ? 'KYC Verified' : 'KYC Pending'}
            </StatusPill>
          </div>
          <div className="flex items-center gap-2">
            <StatusPill variant={user?.totpEnabled ? 'success' : 'warning'}>
              {user?.totpEnabled ? '2FA Enabled' : '2FA Disabled'}
            </StatusPill>
          </div>
        </div>
      </Card>
    </div>
  );
}
