'use client';

import { useState, useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { ArrowLeft, Car, Shield, Lock } from 'lucide-react';
import api from '../../../../../lib/api/client';
import type { VehicleEscrow } from '../../../../../lib/api/types';
import Card from '../../../../../components/ui/Card';
import Button from '../../../../../components/ui/Button';
import Input from '../../../../../components/ui/Input';
import AvalancheBadge from '../../../../../components/ui/AvalancheBadge';

export default function JoinEscrowPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const codeParam = searchParams.get('code') || '';

  const [code, setCode] = useState(codeParam);
  const [escrow, setEscrow] = useState<VehicleEscrow | null>(null);
  const [loading, setLoading] = useState(false);
  const [joining, setJoining] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (codeParam) {
      lookupEscrow(codeParam);
    }
  }, [codeParam]);

  const lookupEscrow = async (shareCode: string) => {
    setError('');
    setLoading(true);
    try {
      const res = await api.get(`/vehicles/escrow/code/${shareCode}`);
      setEscrow(res.data);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Escrow not found');
      setEscrow(null);
    } finally {
      setLoading(false);
    }
  };

  const handleJoin = async () => {
    setError('');
    setJoining(true);
    try {
      const res = await api.post(`/vehicles/escrow/join/${escrow!.shareCode}`);
      router.push(`/vehicles/escrow/${res.data.id}`);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to join escrow');
      setJoining(false);
    }
  };

  return (
    <div className="max-w-2xl mx-auto">
      {/* Back link */}
      <Link
        href="/vehicles"
        className="inline-flex items-center gap-2 text-body-sm text-ari-500 hover:text-ari-900 transition-colors mb-8"
      >
        <ArrowLeft size={16} />
        Back
      </Link>

      {/* Header */}
      <div className="mb-8">
        <h1 className="text-h2 font-display text-ari-900 mb-2">Join Vehicle Escrow</h1>
        <p className="text-body-sm text-ari-500">
          Enter the share code from the seller to review and join the deal
        </p>
      </div>

      {/* Lookup Form */}
      {!escrow && (
        <Card>
          <form
            onSubmit={e => {
              e.preventDefault();
              lookupEscrow(code);
            }}
            className="space-y-5"
          >
            <Input
              label="Share Code"
              value={code}
              onChange={e => setCode(e.target.value.toUpperCase())}
              placeholder="ABCD1234"
              maxLength={8}
              required
            />
            {error && (
              <div className="p-3 bg-ari-red-light rounded-xl border border-ari-red/10">
                <p className="text-body-sm text-ari-red">{error}</p>
              </div>
            )}
            <Button type="submit" loading={loading} fullWidth size="lg">
              Look Up Escrow
            </Button>
          </form>
        </Card>
      )}

      {/* Escrow Preview */}
      {escrow && (
        <div className="space-y-4">
          {/* Deal Card */}
          <Card>
            <div className="space-y-6">
              {/* Vehicle Header */}
              <div className="flex items-center gap-4">
                <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-ari-50 border border-ari-200/60 shrink-0">
                  <Car size={22} className="text-ari-navy" />
                </div>
                <div className="flex-1 min-w-0">
                  <h3 className="text-body font-semibold text-ari-900">Vehicle Escrow Deal</h3>
                  <span className="text-caption text-ari-400 font-mono">Code: {escrow.shareCode}</span>
                </div>
                <AvalancheBadge size="sm" />
              </div>

              {/* Price Grid */}
              <div className="grid grid-cols-2 gap-4 p-4 bg-ari-50 border border-ari-200/60 rounded-xl">
                <div>
                  <span className="micro-label block mb-1">Sale Price</span>
                  <span className="text-h3 font-display text-ari-900">
                    {parseFloat(escrow.saleAmount).toLocaleString('tr-TR')}
                  </span>
                  <span className="text-caption text-ari-500 ml-1">{escrow.currency}</span>
                </div>
                <div>
                  <span className="micro-label block mb-1">Platform Fee</span>
                  <span className="text-body font-semibold text-ari-900">
                    {parseFloat(escrow.feeAmount).toLocaleString('tr-TR')}
                  </span>
                  <span className="text-caption text-ari-500 ml-1">{escrow.currency}</span>
                </div>
              </div>

              {/* Security notice */}
              <div className="flex items-start gap-3 p-4 bg-ari-50 border border-ari-200/60 rounded-xl">
                <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-white border border-ari-200/60 shrink-0">
                  <Lock size={16} className="text-ari-navy" />
                </div>
                <div>
                  <p className="text-body-sm font-medium text-ari-900">Blockchain-Secured Escrow</p>
                  <p className="text-caption text-ari-500 mt-0.5 leading-relaxed">
                    Your payment is locked in a smart contract. The vehicle NFT transfers to you only after both parties confirm. Neither party can cheat.
                  </p>
                </div>
              </div>

              {/* Error */}
              {error && (
                <div className="p-3 bg-ari-red-light rounded-xl border border-ari-red/10">
                  <p className="text-body-sm text-ari-red">{error}</p>
                </div>
              )}

              {/* Join button */}
              <Button fullWidth size="lg" loading={joining} onClick={handleJoin}>
                <Shield size={16} className="mr-1.5" />
                Join & Review Deal
              </Button>
            </div>
          </Card>
        </div>
      )}
    </div>
  );
}
