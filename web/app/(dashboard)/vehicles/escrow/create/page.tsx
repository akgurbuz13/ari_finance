'use client';

import { useState, useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { ArrowLeft, Copy, Check, Car, Hash, ArrowRight } from 'lucide-react';
import api from '../../../../../lib/api/client';
import type { Vehicle, VehicleEscrow } from '../../../../../lib/api/types';
import Card from '../../../../../components/ui/Card';
import Button from '../../../../../components/ui/Button';
import Input from '../../../../../components/ui/Input';
import AvalancheBadge from '../../../../../components/ui/AvalancheBadge';

export default function CreateEscrowPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const vehicleId = searchParams.get('vehicleId');

  const [vehicle, setVehicle] = useState<Vehicle | null>(null);
  const [saleAmount, setSaleAmount] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [created, setCreated] = useState<VehicleEscrow | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (vehicleId) {
      api.get(`/vehicles/${vehicleId}`).then(res => setVehicle(res.data)).catch(() => {});
    }
  }, [vehicleId]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const res = await api.post('/vehicles/escrow', {
        vehicleRegistrationId: vehicleId,
        saleAmount: parseFloat(saleAmount),
      });
      setCreated(res.data);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to create escrow');
    } finally {
      setLoading(false);
    }
  };

  const shareLink = created ? `${window.location.origin}/vehicles/escrow/join?code=${created.shareCode}` : '';

  const copyLink = () => {
    navigator.clipboard.writeText(shareLink);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  /* --- Created State --- */
  if (created) {
    return (
      <div className="max-w-2xl mx-auto">
        {/* Success Header */}
        <div className="text-center mb-8">
          <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-ari-green/10 mx-auto mb-4">
            <Check size={24} className="text-ari-green" />
          </div>
          <h1 className="text-h2 font-display text-ari-900 mb-1">Escrow Created</h1>
          <p className="text-body-sm text-ari-500">Share this with the buyer to start the deal</p>
        </div>

        {/* Share Code Card */}
        <Card className="mb-6">
          <div className="text-center py-4">
            <span className="micro-label block mb-3">Share Code</span>
            <span className="text-h1 font-display text-ari-navy font-mono tracking-[0.2em]">
              {created.shareCode}
            </span>
          </div>
        </Card>

        {/* Share Link */}
        <Card className="mb-6">
          <div className="space-y-4">
            <span className="micro-label block">Share Link</span>
            <div className="flex items-center gap-2">
              <input
                readOnly
                value={shareLink}
                className="flex-1 h-11 px-4 bg-ari-50 border border-ari-200/60 rounded-xl text-body-sm text-ari-700 font-mono truncate"
              />
              <Button variant="secondary" onClick={copyLink} size="md">
                {copied ? <Check size={16} /> : <Copy size={16} />}
              </Button>
            </div>
          </div>
        </Card>

        {/* Deal Summary */}
        <Card className="mb-6">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <span className="micro-label block mb-1">Sale Amount</span>
              <span className="text-body font-semibold text-ari-900">
                {parseFloat(created.saleAmount).toLocaleString('tr-TR')} {created.currency}
              </span>
            </div>
            <div>
              <span className="micro-label block mb-1">Platform Fee</span>
              <span className="text-body font-semibold text-ari-900">
                {parseFloat(created.feeAmount).toLocaleString('tr-TR')} {created.currency}
              </span>
            </div>
          </div>
        </Card>

        <Button fullWidth size="lg" onClick={() => router.push(`/vehicles/escrow/${created.id}`)}>
          View Escrow Details
          <ArrowRight size={16} className="ml-1.5" />
        </Button>
      </div>
    );
  }

  /* --- Create Form --- */
  return (
    <div className="max-w-2xl mx-auto">
      {/* Back link */}
      <Link
        href="/vehicles"
        className="inline-flex items-center gap-2 text-body-sm text-ari-500 hover:text-ari-900 transition-colors mb-8"
      >
        <ArrowLeft size={16} />
        Back to vehicles
      </Link>

      {/* Header */}
      <div className="mb-8">
        <h1 className="text-h2 font-display text-ari-900 mb-2">Create Escrow</h1>
        <p className="text-body-sm text-ari-500">
          Set a price and create a secure, blockchain-backed escrow for your vehicle sale
        </p>
      </div>

      {/* Vehicle Preview */}
      {vehicle && (
        <div className="flex items-center gap-4 p-4 bg-ari-50 border border-ari-200/60 rounded-xl mb-6">
          <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-white border border-ari-200/60 shrink-0">
            <Car size={18} className="text-ari-navy" />
          </div>
          <div className="flex-1 min-w-0">
            <h3 className="text-body-sm font-semibold text-ari-900">
              {vehicle.make} {vehicle.model}
            </h3>
            <div className="flex items-center gap-2 mt-0.5">
              <span className="text-caption text-ari-500">{vehicle.plateNumber}</span>
              {vehicle.tokenId != null && (
                <span className="text-caption text-ari-400 font-mono flex items-center gap-1">
                  <Hash size={10} />
                  {vehicle.tokenId}
                </span>
              )}
            </div>
          </div>
          <AvalancheBadge size="sm" />
        </div>
      )}

      {/* Sale Form */}
      <Card>
        <form onSubmit={handleCreate} className="space-y-6">
          {/* Amount input - display style */}
          <div>
            <span className="micro-label block mb-3">Sale Amount</span>
            <div className="relative">
              <input
                type="number"
                value={saleAmount}
                onChange={e => setSaleAmount(e.target.value)}
                placeholder="0"
                min={1}
                step="0.01"
                required
                className="w-full h-16 px-4 pr-16 bg-ari-50 border border-ari-200 rounded-xl text-h2 font-display text-ari-900 placeholder:text-ari-300 transition-all duration-base focus:outline-none focus:bg-white focus:border-ari-900 focus:ring-1 focus:ring-ari-900/10 [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none"
              />
              <span className="absolute right-4 top-1/2 -translate-y-1/2 text-body-sm font-medium text-ari-400">
                TRY
              </span>
            </div>
          </div>

          {/* Fee breakdown */}
          <div className="bg-ari-50 border border-ari-200/60 rounded-xl p-4 space-y-3">
            <div className="flex justify-between items-center">
              <span className="text-body-sm text-ari-500">Platform Fee</span>
              <span className="text-body-sm font-medium text-ari-700">50.00 TRY</span>
            </div>
            <div className="border-t border-ari-200/60" />
            <div className="flex justify-between items-center">
              <span className="text-body-sm text-ari-700 font-medium">Buyer pays total</span>
              <span className="text-body font-semibold text-ari-900">
                {saleAmount
                  ? `${(parseFloat(saleAmount) + 50).toLocaleString('tr-TR')} TRY`
                  : '---'
                }
              </span>
            </div>
          </div>

          {/* Error */}
          {error && (
            <div className="p-3 bg-ari-red-light rounded-xl border border-ari-red/10">
              <p className="text-body-sm text-ari-red">{error}</p>
            </div>
          )}

          {/* Submit */}
          <Button type="submit" loading={loading} fullWidth size="lg">
            Create Escrow
          </Button>
        </form>
      </Card>
    </div>
  );
}
