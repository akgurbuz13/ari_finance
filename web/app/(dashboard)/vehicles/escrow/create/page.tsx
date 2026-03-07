'use client';

import { useState, useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { ArrowLeft, Copy, Check } from 'lucide-react';
import api from '../../../../../lib/api/client';
import type { Vehicle, VehicleEscrow } from '../../../../../lib/api/types';
import Card from '../../../../../components/ui/Card';
import Button from '../../../../../components/ui/Button';
import Input from '../../../../../components/ui/Input';

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

  if (created) {
    return (
      <div className="max-w-2xl mx-auto">
        <h1 className="text-h2 text-ova-900 mb-2">Escrow Created</h1>
        <p className="text-body-sm text-ova-500 mb-8">Share this link with the buyer to start the deal</p>

        <Card>
          <div className="space-y-6">
            <div>
              <span className="text-caption text-ova-500 block mb-1">Share Code</span>
              <span className="text-h2 text-ova-navy font-mono tracking-widest">{created.shareCode}</span>
            </div>

            <div>
              <span className="text-caption text-ova-500 block mb-2">Share Link</span>
              <div className="flex items-center gap-2">
                <input readOnly value={shareLink}
                  className="flex-1 h-12 px-4 bg-ova-50 border border-ova-200 rounded-xl text-body-sm text-ova-700 font-mono" />
                <Button variant="secondary" onClick={copyLink}>
                  {copied ? <Check size={18} /> : <Copy size={18} />}
                </Button>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4 pt-4 border-t border-ova-100">
              <div>
                <span className="text-caption text-ova-500 block">Sale Amount</span>
                <span className="text-body font-medium text-ova-900">
                  {parseFloat(created.saleAmount).toLocaleString('tr-TR')} {created.currency}
                </span>
              </div>
              <div>
                <span className="text-caption text-ova-500 block">Fee</span>
                <span className="text-body font-medium text-ova-900">
                  {parseFloat(created.feeAmount).toLocaleString('tr-TR')} {created.currency}
                </span>
              </div>
            </div>

            <Button fullWidth onClick={() => router.push(`/vehicles/escrow/${created.id}`)}>
              View Escrow Details
            </Button>
          </div>
        </Card>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto">
      <Link href="/vehicles" className="inline-flex items-center gap-2 text-body-sm text-ova-500 hover:text-ova-700 mb-6">
        <ArrowLeft size={16} /> Back to vehicles
      </Link>

      <h1 className="text-h2 text-ova-900 mb-2">Create Escrow</h1>
      <p className="text-body-sm text-ova-500 mb-8">Set a price and create a secure escrow for your vehicle sale</p>

      {vehicle && (
        <Card className="mb-6">
          <div className="flex items-center gap-4">
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-ova-100">
              <span className="text-body font-bold text-ova-navy">{vehicle.year.toString().slice(-2)}</span>
            </div>
            <div>
              <h3 className="text-body font-medium text-ova-900">{vehicle.make} {vehicle.model}</h3>
              <p className="text-body-sm text-ova-500">{vehicle.plateNumber} · Token #{vehicle.tokenId}</p>
            </div>
          </div>
        </Card>
      )}

      <Card>
        <form onSubmit={handleCreate} className="space-y-5">
          <Input label="Sale Amount (TRY)" type="number" value={saleAmount}
            onChange={e => setSaleAmount(e.target.value)}
            placeholder="150000" min={1} step="0.01" required />

          <div className="bg-ova-50 rounded-xl p-4">
            <div className="flex justify-between text-body-sm">
              <span className="text-ova-500">Platform Fee</span>
              <span className="text-ova-700 font-medium">50.00 TRY</span>
            </div>
            <div className="flex justify-between text-body-sm mt-2">
              <span className="text-ova-500">Buyer pays total</span>
              <span className="text-ova-900 font-medium">
                {saleAmount ? `${(parseFloat(saleAmount) + 50).toLocaleString('tr-TR')} TRY` : '—'}
              </span>
            </div>
          </div>

          {error && <p className="text-body-sm text-ova-red">{error}</p>}

          <Button type="submit" loading={loading} fullWidth size="lg">
            Create Escrow
          </Button>
        </form>
      </Card>
    </div>
  );
}
