'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import {
  ArrowUpRight,
  ArrowDownLeft,
  ArrowLeftRight,
  Globe,
  Car,
  Clock,
  Eye,
  EyeOff,
  ChevronRight,
} from 'lucide-react';
import api from '../../../lib/api/client';
import type { Account, Transaction, Vehicle } from '../../../lib/api/types';
import { useAuth } from '../../../lib/hooks/useAuth';
import Card from '../../../components/ui/Card';
import { BalanceCard } from '../../../components/ui/Card';
import Button from '../../../components/ui/Button';
import StatusPill from '../../../components/ui/StatusPill';
import Skeleton, { SkeletonCard } from '../../../components/ui/Skeleton';
import GradientHero from '../../../components/ui/GradientHero';
import AvalancheBadge from '../../../components/ui/AvalancheBadge';

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

function getTotalBalance(accounts: Account[]): string {
  // Sum all balances — for display purposes only (mixed currencies)
  const total = accounts.reduce((sum, acc) => sum + parseFloat(acc.balance), 0);
  // If all accounts are same currency, format with that currency
  const currencies = Array.from(new Set(accounts.map((a) => a.currency)));
  if (currencies.length === 1) {
    return formatCurrency(total.toString(), currencies[0]);
  }
  // Mixed currencies: show with primary currency symbol
  const tryTotal = accounts
    .filter((a) => a.currency === 'TRY')
    .reduce((sum, a) => sum + parseFloat(a.balance), 0);
  const eurTotal = accounts
    .filter((a) => a.currency === 'EUR')
    .reduce((sum, a) => sum + parseFloat(a.balance), 0);
  if (tryTotal > 0 && eurTotal > 0) {
    return `₺${tryTotal.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }
  if (eurTotal > 0) return formatCurrency(eurTotal.toString(), 'EUR');
  return formatCurrency(tryTotal.toString(), 'TRY');
}

function getSecondaryBalance(accounts: Account[]): string | null {
  const currencies = Array.from(new Set(accounts.map((a) => a.currency)));
  if (currencies.length <= 1) return null;
  const eurTotal = accounts
    .filter((a) => a.currency === 'EUR')
    .reduce((sum, a) => sum + parseFloat(a.balance), 0);
  if (eurTotal > 0) {
    return `+ €${eurTotal.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }
  return null;
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

const txTypeLabels: Record<string, string> = {
  deposit: 'Deposit',
  withdrawal: 'Withdrawal',
  p2p_transfer: 'Transfer',
  fx_conversion: 'FX Conversion',
  cross_border: 'Cross-Border',
  mint: 'Mint',
  burn: 'Burn',
  fee: 'Fee',
};

function DashboardSkeleton() {
  return (
    <div className="max-w-dashboard mx-auto">
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Main skeleton */}
        <div className="lg:col-span-2 space-y-6">
          <Skeleton variant="text" className="w-48 h-5" />
          <Skeleton variant="rectangular" className="w-full h-48" />
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <SkeletonCard />
            <SkeletonCard />
          </div>
        </div>
        {/* Sidebar skeleton */}
        <div className="space-y-6">
          <SkeletonCard />
          <SkeletonCard className="h-64" />
          <SkeletonCard />
        </div>
      </div>
    </div>
  );
}

export default function HomePage() {
  const { user } = useAuth();
  const router = useRouter();
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [loading, setLoading] = useState(true);
  const [balanceVisible, setBalanceVisible] = useState(true);

  const today = new Date().toLocaleDateString('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
  });

  useEffect(() => {
    async function load() {
      try {
        const [acctRes, vehicleRes] = await Promise.all([
          api.get<Account[]>('/accounts'),
          api.get<Vehicle[]>('/vehicles').catch(() => ({ data: [] as Vehicle[] })),
        ]);
        setAccounts(acctRes.data);
        setVehicles(vehicleRes.data);

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
  const maskedBalance = '****';
  const secondaryBalance = getSecondaryBalance(accounts);

  return (
    <div className="max-w-dashboard mx-auto">
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* ── Main Area ── */}
        <div className="lg:col-span-2 space-y-6">
          {/* Greeting + Date */}
          <div className="animate-fade-in">
            <p className="text-body-sm text-ari-400">
              {today}
            </p>
            <h1 className="text-h2 font-display text-ari-900 mt-1">
              {getGreeting()}, {displayName}
            </h1>
          </div>

          {/* Net Worth Hero Card */}
          <div className="animate-fade-in-up">
            {accounts.length > 0 ? (
              <GradientHero>
                <div className="flex items-start justify-between">
                  <div>
                    <span className="micro-label !text-white/50">TOTAL BALANCE</span>
                    <div className="mt-3">
                      <p className="amount-hero text-white">
                        {balanceVisible ? getTotalBalance(accounts) : maskedBalance}
                      </p>
                      {secondaryBalance && balanceVisible && (
                        <p className="text-body text-white/40 mt-2 font-display">
                          {secondaryBalance}
                        </p>
                      )}
                    </div>
                  </div>
                  <button
                    onClick={() => setBalanceVisible(!balanceVisible)}
                    className="p-2 rounded-xl hover:bg-white/10 transition-colors duration-fast text-white/50 hover:text-white/80"
                    aria-label={balanceVisible ? 'Hide balance' : 'Show balance'}
                  >
                    {balanceVisible ? <Eye size={18} /> : <EyeOff size={18} />}
                  </button>
                </div>
              </GradientHero>
            ) : (
              <GradientHero>
                <div className="text-center py-4">
                  <h2 className="text-h2 font-display text-white">Welcome to ARI</h2>
                  <p className="text-body text-white/50 mt-2 max-w-md mx-auto">
                    Create your first account to start sending money between Turkey and Europe.
                  </p>
                  <div className="mt-6 flex justify-center gap-3">
                    <Button variant="secondary" size="sm" onClick={() => router.push('/accounts')}>
                      Create TRY Account
                    </Button>
                    <Button variant="secondary" size="sm" onClick={() => router.push('/accounts')}>
                      Create EUR Account
                    </Button>
                  </div>
                </div>
              </GradientHero>
            )}
          </div>

          {/* Account Cards Grid */}
          {accounts.length > 0 && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {accounts.map((account, i) => (
                <div
                  key={account.id}
                  className={`animate-fade-in-up stagger-${i + 1}`}
                >
                  <BalanceCard
                    currency={`${account.currency === 'TRY' ? '\u{1F1F9}\u{1F1F7}' : '\u{1F1EA}\u{1F1FA}'} ${account.currency}`}
                    amount={balanceVisible ? formatCurrency(account.balance, account.currency) : '****'}
                    subtitle={`${account.region} region`}
                  />
                </div>
              ))}
            </div>
          )}

          {/* Vehicles Section */}
          <div className="animate-fade-in-up stagger-4">
            <div className="flex items-center justify-between mb-4">
              <span className="micro-label">MY VEHICLES</span>
              {vehicles.length > 0 && (
                <Link
                  href="/vehicles"
                  className="text-caption text-ari-500 hover:text-ari-700 transition-colors duration-fast flex items-center gap-0.5"
                >
                  View all
                  <ChevronRight size={14} />
                </Link>
              )}
            </div>

            {vehicles.length > 0 ? (
              <div className="flex gap-4 overflow-x-auto scrollbar-none pb-1">
                {vehicles.map((vehicle) => (
                  <Card
                    key={vehicle.id}
                    padding="compact"
                    hover
                    className="min-w-[220px] flex-shrink-0"
                  >
                    <div className="flex flex-col gap-2">
                      <div className="flex items-start justify-between">
                        <div>
                          <p className="text-body-sm font-semibold text-ari-900">
                            {vehicle.make} {vehicle.model}
                          </p>
                          <p className="text-caption text-ari-400">{vehicle.year}</p>
                        </div>
                        {vehicle.tokenId !== null && (
                          <span className="text-micro bg-ari-navy text-white px-2 py-0.5 rounded-full">
                            #{vehicle.tokenId}
                          </span>
                        )}
                      </div>
                      <div className="flex items-center justify-between mt-1">
                        <StatusPill
                          variant={
                            vehicle.status === 'MINTED' ? 'success' :
                            vehicle.status === 'IN_ESCROW' ? 'warning' :
                            vehicle.status === 'TRANSFERRED' ? 'info' :
                            'neutral'
                          }
                          dot
                        >
                          {vehicle.status.toLowerCase()}
                        </StatusPill>
                        {vehicle.mintTxHash && <AvalancheBadge size="sm" />}
                      </div>
                    </div>
                  </Card>
                ))}
              </div>
            ) : (
              <Card padding="generous" className="border-dashed">
                <div className="text-center">
                  <div className="flex justify-center mb-3">
                    <div className="w-10 h-10 rounded-full bg-ari-100 flex items-center justify-center">
                      <Car size={18} className="text-ari-400" />
                    </div>
                  </div>
                  <p className="text-body-sm font-medium text-ari-700">
                    Register your first vehicle as an on-chain NFT
                  </p>
                  <p className="text-caption text-ari-400 mt-1">
                    Tokenize vehicle ownership on Avalanche for secure peer-to-peer sales
                  </p>
                  <div className="mt-4">
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => router.push('/vehicles')}
                    >
                      <Car size={14} />
                      Register Vehicle
                    </Button>
                  </div>
                </div>
              </Card>
            )}
          </div>
        </div>

        {/* ── Sidebar ── */}
        <div className="space-y-6">
          {/* Quick Actions */}
          <div className="animate-fade-in-up stagger-2">
            <span className="micro-label block mb-3">QUICK ACTIONS</span>
            <div className="grid grid-cols-2 gap-3">
              <Link
                href="/transfer"
                className="flex flex-col items-center gap-2.5 p-4 rounded-2xl bg-ari-navy text-white hover:bg-ari-navy-light transition-colors duration-fast group"
              >
                <ArrowUpRight size={20} strokeWidth={1.5} className="group-hover:translate-x-0.5 group-hover:-translate-y-0.5 transition-transform duration-fast" />
                <span className="text-caption font-medium">Send Money</span>
              </Link>
              <Link
                href="/transfer"
                className="flex flex-col items-center gap-2.5 p-4 rounded-2xl bg-ari-navy text-white hover:bg-ari-navy-light transition-colors duration-fast group"
              >
                <Globe size={20} strokeWidth={1.5} className="group-hover:rotate-12 transition-transform duration-fast" />
                <span className="text-caption font-medium">Cross-Border</span>
              </Link>
              <Link
                href="/vehicles"
                className="flex flex-col items-center gap-2.5 p-4 rounded-2xl bg-white border border-ari-200/60 text-ari-700 hover:border-ari-300 hover:bg-ari-50 transition-all duration-fast"
              >
                <Car size={20} strokeWidth={1.5} />
                <span className="text-caption font-medium">Register Vehicle</span>
              </Link>
              <Link
                href="/history"
                className="flex flex-col items-center gap-2.5 p-4 rounded-2xl bg-white border border-ari-200/60 text-ari-700 hover:border-ari-300 hover:bg-ari-50 transition-all duration-fast"
              >
                <Clock size={20} strokeWidth={1.5} />
                <span className="text-caption font-medium">History</span>
              </Link>
            </div>
          </div>

          {/* Recent Activity */}
          <div className="animate-fade-in-up stagger-3">
            <Card header="Recent Activity">
              {transactions.length > 0 ? (
                <div className="divide-y divide-ari-100">
                  {transactions.map((tx) => {
                    const Icon = txTypeIcons[tx.type] || ArrowUpRight;
                    const label = txTypeLabels[tx.type] || tx.type.replace(/_/g, ' ');
                    return (
                      <div key={tx.id} className="flex items-center justify-between py-3 first:pt-0 last:pb-0">
                        <div className="flex items-center gap-3 min-w-0">
                          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-ari-100 shrink-0">
                            <Icon size={14} strokeWidth={1.5} className="text-ari-500" />
                          </div>
                          <div className="min-w-0">
                            <p className="text-body-sm font-medium text-ari-900 truncate">
                              {label}
                            </p>
                            <p className="text-micro text-ari-400">
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
                          dot
                        >
                          {tx.status}
                        </StatusPill>
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className="text-center py-6">
                  <p className="text-body-sm text-ari-400">No transactions yet</p>
                  <p className="text-caption text-ari-300 mt-1">
                    Your activity will appear here
                  </p>
                </div>
              )}
              {transactions.length > 0 && (
                <Link
                  href="/history"
                  className="flex items-center justify-center gap-1 mt-4 pt-4 border-t border-ari-100 text-caption font-medium text-ari-500 hover:text-ari-700 transition-colors duration-fast"
                >
                  View all transactions
                  <ChevronRight size={14} />
                </Link>
              )}
            </Card>
          </div>

          {/* Account Health */}
          <div className="animate-fade-in-up stagger-5">
            <Card padding="compact">
              <span className="micro-label block mb-3 px-2 pt-1">ACCOUNT HEALTH</span>
              <div className="space-y-2">
                {user?.status !== 'active' ? (
                  <Link
                    href="/kyc"
                    className="flex items-center justify-between p-2.5 rounded-xl hover:bg-ari-50 transition-colors duration-fast"
                  >
                    <StatusPill variant="warning" dot>KYC Pending</StatusPill>
                    <span className="text-micro text-ari-amber">Verify &rarr;</span>
                  </Link>
                ) : (
                  <div className="flex items-center justify-between p-2.5">
                    <StatusPill variant="success" dot>KYC Verified</StatusPill>
                  </div>
                )}
                {!user?.totpEnabled ? (
                  <Link
                    href="/settings"
                    className="flex items-center justify-between p-2.5 rounded-xl hover:bg-ari-50 transition-colors duration-fast"
                  >
                    <StatusPill variant="warning" dot>2FA Disabled</StatusPill>
                    <span className="text-micro text-ari-amber">Enable &rarr;</span>
                  </Link>
                ) : (
                  <div className="flex items-center justify-between p-2.5">
                    <StatusPill variant="success" dot>2FA Enabled</StatusPill>
                  </div>
                )}
              </div>
            </Card>
          </div>
        </div>
      </div>
    </div>
  );
}
