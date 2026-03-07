'use client';

import { useState, useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { ArrowLeft, Car, Shield } from 'lucide-react';
import api from '../../../../../lib/api/client';
import type { VehicleEscrow } from '../../../../../lib/api/types';
import Card from '../../../../../components/ui/Card';
import Button from '../../../../../components/ui/Button';
import Input from '../../../../../components/ui/Input';

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
      <Link href="/vehicles" className="inline-flex items-center gap-2 text-body-sm text-ova-500 hover:text-ova-700 mb-6">
        <ArrowLeft size={16} /> Back
      </Link>

      <h1 className="text-h2 text-ova-900 mb-2">Join Vehicle Escrow</h1>
      <p className="text-body-sm text-ova-500 mb-8">Enter the share code from the seller to review and join the deal</p>

      {!escrow && (
        <Card>
          <form onSubmit={(e) => { e.preventDefault(); lookupEscrow(code); }} className="space-y-5">
            <Input label="Share Code" value={code} onChange={e => setCode(e.target.value.toUpperCase())}
              placeholder="ABCD1234" maxLength={8} required />
            {error && <p className="text-body-sm text-ova-red">{error}</p>}
            <Button type="submit" loading={loading} fullWidth>
              Look Up Escrow
            </Button>
          </form>
        </Card>
      )}

      {escrow && (
        <div className="space-y-6">
          <Card>
            <div className="space-y-6">
              <div className="flex items-center gap-4">
                <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-ova-100">
                  <Car size={24} className="text-ova-navy" />
                </div>
                <div>
                  <h3 className="text-body font-medium text-ova-900">Vehicle Escrow Deal</h3>
                  <p className="text-body-sm text-ova-500">Code: {escrow.shareCode}</p>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <span className="text-caption text-ova-500 block">Sale Price</span>
                  <span className="text-h3 text-ova-900">
                    {parseFloat(escrow.saleAmount).toLocaleString('tr-TR')} {escrow.currency}
                  </span>
                </div>
                <div>
                  <span className="text-caption text-ova-500 block">Platform Fee</span>
                  <span className="text-body font-medium text-ova-900">
                    {parseFloat(escrow.feeAmount).toLocaleString('tr-TR')} {escrow.currency}
                  </span>
                </div>
              </div>

              <div className="bg-ova-50 rounded-xl p-4 flex items-start gap-3">
                <Shield size={20} className="text-ova-navy mt-0.5 shrink-0" />
                <div>
                  <p className="text-body-sm text-ova-700 font-medium">Blockchain-Secured Escrow</p>
                  <p className="text-caption text-ova-500 mt-1">
                    Your payment is locked in a smart contract. The vehicle NFT transfers to you only after both parties confirm. Neither party can cheat.
                  </p>
                </div>
              </div>

              {error && <p className="text-body-sm text-ova-red">{error}</p>}

              <Button fullWidth size="lg" loading={joining} onClick={handleJoin}>
                Join & Review Deal
              </Button>
            </div>
          </Card>
        </div>
      )}
    </div>
  );
}
