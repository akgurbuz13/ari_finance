'use client';

import { useState, useEffect } from 'react';
import { Copy, Check, Wallet, Plus, ArrowRight } from 'lucide-react';
import Link from 'next/link';
import { clsx } from 'clsx';
import api from '../../../lib/api/client';
import type { Account } from '../../../lib/api/types';
import Card from '../../../components/ui/Card';
import Button from '../../../components/ui/Button';
import StatusPill from '../../../components/ui/StatusPill';
import Skeleton, { SkeletonCard } from '../../../components/ui/Skeleton';

function formatCurrency(amount: string, currency: 'TRY' | 'EUR') {
  const num = parseFloat(amount);
  const symbol = currency === 'TRY' ? '\u20BA' : '\u20AC';
  const locale = currency === 'TRY' ? 'tr-TR' : 'de-DE';
  return `${symbol}${num.toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function maskIban(accountId: string) {
  const prefix = 'TR';
  const shortId = accountId.replace(/-/g, '').slice(0, 16).toUpperCase();
  const masked = `${prefix}** **** **** ${shortId.slice(-4)}`;
  return masked;
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard API may not be available
    }
  };

  return (
    <button
      onClick={handleCopy}
      className={clsx(
        'inline-flex items-center justify-center w-8 h-8 rounded-lg transition-all duration-base cursor-pointer',
        copied
          ? 'bg-ari-green/10 text-ari-green'
          : 'hover:bg-ari-100 text-ari-400 hover:text-ari-700'
      )}
      title="Copy account ID"
    >
      {copied ? <Check size={14} strokeWidth={2.5} /> : <Copy size={14} strokeWidth={2} />}
    </button>
  );
}

function AccountCard({ account }: { account: Account }) {
  const iban = maskIban(account.id);
  const flag = account.currency === 'TRY' ? '\u{1F1F9}\u{1F1F7}' : '\u{1F1EA}\u{1F1FA}';
  const regionLabel = account.region === 'TR' ? 'Turkey' : 'Europe';

  return (
    <div className="bg-white border border-ari-200/60 rounded-2xl p-6 hover:border-ari-300 hover:shadow-card-hover transition-all duration-base group">
      {/* Top: Currency name + flag + region badge + status */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2.5">
          <span className="text-xl leading-none">{flag}</span>
          <span className="text-body-sm font-semibold text-ari-900">{account.currency}</span>
          <StatusPill variant="neutral">
            {regionLabel}
          </StatusPill>
        </div>
        <StatusPill
          variant={
            account.status === 'active' ? 'success' :
            account.status === 'frozen' ? 'warning' : 'error'
          }
          dot
        >
          {account.status}
        </StatusPill>
      </div>

      {/* Middle: Large balance */}
      <div className="mt-5 mb-5">
        <p className="text-h1 font-display text-ari-900 tracking-tight leading-none">
          {formatCurrency(account.balance, account.currency)}
        </p>
        <span className="text-caption text-ari-400 mt-1.5 block">Available balance</span>
      </div>

      {/* Bottom: IBAN + details */}
      <div className="pt-4 border-t border-ari-100 space-y-3">
        {/* IBAN row */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-caption text-ari-400 uppercase tracking-wide font-medium">IBAN</span>
            <span className="font-mono text-body-sm text-ari-700">{iban}</span>
          </div>
          <CopyButton text={account.id} />
        </div>

        {/* Created date */}
        <div className="flex items-center gap-2">
          <span className="text-caption text-ari-400 uppercase tracking-wide font-medium">Opened</span>
          <span className="text-caption text-ari-500">
            {new Date(account.createdAt).toLocaleDateString('en-GB', {
              day: 'numeric',
              month: 'short',
              year: 'numeric',
            })}
          </span>
        </div>

        {/* View transactions link */}
        <Link
          href="/history"
          className="inline-flex items-center gap-1.5 text-body-sm font-medium text-ari-700 hover:text-ari-900 transition-colors mt-1 group/link"
        >
          View transactions
          <ArrowRight size={14} strokeWidth={1.5} className="transition-transform group-hover/link:translate-x-0.5" />
        </Link>
      </div>
    </div>
  );
}

function CreateAccountCard({ onCreate }: { onCreate: (currency: string) => void }) {
  return (
    <button
      onClick={() => onCreate('TRY')}
      className="border-2 border-dashed border-ari-200 rounded-2xl p-6 flex flex-col items-center justify-center hover:border-ari-300 transition-colors cursor-pointer min-h-[220px] w-full group"
    >
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-ari-100 group-hover:bg-ari-200 transition-colors">
        <Plus size={24} strokeWidth={1.5} className="text-ari-400 group-hover:text-ari-600 transition-colors" />
      </div>
      <p className="text-body-sm font-medium text-ari-500 mt-3 group-hover:text-ari-700 transition-colors">
        Create new account
      </p>
      <p className="text-caption text-ari-400 mt-1">
        TRY or EUR
      </p>
    </button>
  );
}

function AccountsSkeleton() {
  return (
    <div className="max-w-dashboard mx-auto space-y-8">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Skeleton variant="text" className="w-32 h-8" />
          <Skeleton variant="rectangular" className="w-8 h-6 rounded-full" />
        </div>
        <div className="flex gap-2">
          <Skeleton variant="rectangular" className="w-32 h-10" />
          <Skeleton variant="rectangular" className="w-32 h-10" />
        </div>
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <SkeletonCard />
        <SkeletonCard />
      </div>
    </div>
  );
}

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
    return <AccountsSkeleton />;
  }

  return (
    <div className="max-w-dashboard mx-auto space-y-8">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="text-h2 font-display text-ari-900">Accounts</h1>
          {accounts.length > 0 && (
            <span className="inline-flex items-center justify-center min-w-[24px] h-6 px-2 rounded-full bg-ari-100 text-caption font-semibold text-ari-700">
              {accounts.length}
            </span>
          )}
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => createAccount('TRY')} disabled={creating}>
            <Plus size={16} strokeWidth={2} className="mr-1 inline" />
            TRY Account
          </Button>
          <Button variant="secondary" onClick={() => createAccount('EUR')} disabled={creating}>
            <Plus size={16} strokeWidth={2} className="mr-1 inline" />
            EUR Account
          </Button>
        </div>
      </div>

      {/* Account list */}
      {accounts.length === 0 ? (
        <Card padding="generous">
          <div className="text-center py-8 space-y-4">
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-ari-100 mx-auto">
              <Wallet size={32} strokeWidth={1.5} className="text-ari-400" />
            </div>
            <h2 className="text-h3 font-display text-ari-900">No accounts yet</h2>
            <p className="text-body-sm text-ari-400 max-w-sm mx-auto">
              Create a TRY or EUR account to start sending and receiving money between Turkey and Europe.
            </p>
            <div className="flex justify-center gap-3 pt-2">
              <Button variant="secondary" onClick={() => createAccount('TRY')} disabled={creating}>
                {'\u{1F1F9}\u{1F1F7}'} Create TRY Account
              </Button>
              <Button variant="secondary" onClick={() => createAccount('EUR')} disabled={creating}>
                {'\u{1F1EA}\u{1F1FA}'} Create EUR Account
              </Button>
            </div>
          </div>
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {accounts.map((account) => (
            <AccountCard key={account.id} account={account} />
          ))}
          {accounts.length < 2 && (
            <CreateAccountCard onCreate={createAccount} />
          )}
        </div>
      )}
    </div>
  );
}
