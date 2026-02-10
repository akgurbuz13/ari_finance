'use client';

import { useState, useEffect } from 'react';
import { Copy, Check, Wallet, ChevronRight, Plus, ArrowRight } from 'lucide-react';
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
  const symbol = currency === 'TRY' ? '₺' : '€';
  const locale = currency === 'TRY' ? 'tr-TR' : 'de-DE';
  return `${symbol}${num.toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function maskIban(accountId: string) {
  // Generate a display IBAN from the account ID
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
      className="inline-flex items-center gap-1 text-caption text-ova-blue hover:underline cursor-pointer"
    >
      {copied ? (
        <>
          <Check size={12} strokeWidth={2} />
          <span>Copied</span>
        </>
      ) : (
        <>
          <Copy size={12} strokeWidth={2} />
          <span>Copy</span>
        </>
      )}
    </button>
  );
}

function AccountCard({ account }: { account: Account }) {
  const [expanded, setExpanded] = useState(false);
  const iban = maskIban(account.id);

  return (
    <div className="bg-white border border-ova-200 border-l-4 border-l-ova-navy rounded-2xl p-6 shadow-card">
      <div className="flex items-start justify-between">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <span className="text-caption uppercase text-ova-500 tracking-wide">
              {account.currency} {account.accountType.replace(/_/g, ' ')}
            </span>
            <StatusPill
              variant={
                account.status === 'active' ? 'success' :
                account.status === 'frozen' ? 'warning' : 'error'
              }
            >
              {account.status}
            </StatusPill>
          </div>
          <p className="amount-display text-ova-navy">
            {formatCurrency(account.balance, account.currency)}
          </p>
          <span className="text-body-sm text-ova-500 block">Available balance</span>
        </div>
        <div className="flex h-10 w-10 items-center justify-center rounded-full bg-ova-100">
          <Wallet size={20} strokeWidth={1.5} className="text-ova-500" />
        </div>
      </div>

      {/* IBAN section */}
      <div className="mt-4 pt-4 border-t border-ova-100">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="text-caption text-ova-400">IBAN</span>
            <span className="font-mono text-body-sm text-ova-700">{iban}</span>
          </div>
          <CopyButton text={account.id} />
        </div>
      </div>

      {/* Expandable details */}
      <div className="mt-3">
        <button
          onClick={() => setExpanded(!expanded)}
          className="inline-flex items-center gap-1 text-caption text-ova-blue cursor-pointer hover:underline"
        >
          <ChevronRight
            size={12}
            strokeWidth={2}
            className={clsx('transition-transform duration-fast', expanded && 'rotate-90')}
          />
          {expanded ? 'Hide details' : 'Account details'}
        </button>
        {expanded && (
          <div className="mt-3 space-y-2 pl-1">
            <div className="flex items-center gap-2">
              <span className="text-caption text-ova-400">Account ID:</span>
              <span className="font-mono text-caption text-ova-500">{account.id}</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-caption text-ova-400">Type:</span>
              <span className="text-caption text-ova-500 capitalize">{account.accountType.replace(/_/g, ' ')}</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-caption text-ova-400">Opened:</span>
              <span className="text-caption text-ova-500">
                {new Date(account.createdAt).toLocaleDateString('en-GB', {
                  day: 'numeric',
                  month: 'short',
                  year: 'numeric',
                })}
              </span>
            </div>
          </div>
        )}
        <Link
          href="/history"
          className="inline-flex items-center gap-1 text-caption text-ova-blue hover:underline mt-3"
        >
          View transactions <ArrowRight size={12} />
        </Link>
      </div>
    </div>
  );
}

function AccountsSkeleton() {
  return (
    <div className="max-w-dashboard mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <Skeleton variant="text" className="w-32 h-8" />
        <div className="flex gap-2">
          <Skeleton variant="rectangular" className="w-32 h-10" />
          <Skeleton variant="rectangular" className="w-32 h-10" />
        </div>
      </div>
      <SkeletonCard />
      <SkeletonCard />
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
    <div className="max-w-dashboard mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-h2 text-ova-900">Accounts</h1>
          {accounts.length > 0 && (
            <p className="text-body-sm text-ova-500 mt-1">{accounts.length} account{accounts.length !== 1 ? 's' : ''}</p>
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
        <Card>
          <div className="text-center py-12 space-y-4">
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-ova-100 mx-auto">
              <Wallet size={32} strokeWidth={1.5} className="text-ova-400" />
            </div>
            <h2 className="text-h3 text-ova-900">No accounts yet</h2>
            <p className="text-body-sm text-ova-400 max-w-sm mx-auto">
              Create a TRY or EUR account to start sending and receiving money between Turkey and Europe.
            </p>
            <div className="flex justify-center gap-3 pt-2">
              <Button variant="secondary" onClick={() => createAccount('TRY')}>
                {'\u{1F1F9}\u{1F1F7}'} Create TRY Account
              </Button>
              <Button variant="secondary" onClick={() => createAccount('EUR')}>
                {'\u{1F1EA}\u{1F1FA}'} Create EUR Account
              </Button>
            </div>
          </div>
        </Card>
      ) : (
        <div className="space-y-4">
          {accounts.map((account) => (
            <AccountCard key={account.id} account={account} />
          ))}
        </div>
      )}
    </div>
  );
}
