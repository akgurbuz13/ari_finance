'use client';

import { useState, useEffect, useCallback } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import { ArrowLeft, ExternalLink, CheckCircle2, Circle, Loader2, XCircle } from 'lucide-react';
import api from '../../../../../lib/api/client';
import type { VehicleEscrow, Vehicle } from '../../../../../lib/api/types';
import Card from '../../../../../components/ui/Card';
import Button from '../../../../../components/ui/Button';

const STEPS = [
  { key: 'CREATED', label: 'Escrow Created' },
  { key: 'JOINING', label: 'Buyer Joining' },
  { key: 'SETUP_COMPLETE', label: 'On-chain Setup' },
  { key: 'FUNDED', label: 'Funded' },
  { key: 'CONFIRMED', label: 'Confirmed' },
  { key: 'COMPLETED', label: 'Completed' },
];

const STATE_ORDER: Record<string, number> = {
  CREATED: 0, JOINING: 1, SETUP_COMPLETE: 2, FUNDING: 3, FUNDED: 3,
  SELLER_CONFIRMED: 4, BUYER_CONFIRMED: 4, COMPLETING: 5, COMPLETED: 5,
  CANCELLING: -1, CANCELLED: -1,
};

export default function EscrowDetailPage() {
  const params = useParams();
  const [escrow, setEscrow] = useState<VehicleEscrow | null>(null);
  const [vehicle, setVehicle] = useState<Vehicle | null>(null);
  const [userId, setUserId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState('');

  const fetchEscrow = useCallback(async () => {
    try {
      const res = await api.get(`/vehicles/escrow/${params.id}`);
      setEscrow(res.data);
      const vRes = await api.get(`/vehicles/${res.data.vehicleRegistrationId}`).catch(() => null);
      if (vRes) setVehicle(vRes.data);
    } catch { /* ignore */ }
    setLoading(false);
  }, [params.id]);

  useEffect(() => {
    fetchEscrow();
    setUserId(typeof window !== 'undefined' ? localStorage.getItem('userId') : null);
    const interval = setInterval(fetchEscrow, 5000);
    return () => clearInterval(interval);
  }, [fetchEscrow]);

  const doAction = async (action: string) => {
    setActionLoading(action);
    try {
      await api.post(`/vehicles/escrow/${params.id}/${action}`);
      await fetchEscrow();
    } catch { /* ignore */ }
    setActionLoading('');
  };

  if (loading || !escrow) {
    return (
      <div className="max-w-2xl mx-auto text-center py-12">
        <Loader2 size={32} className="mx-auto text-ova-500 animate-spin" />
      </div>
    );
  }

  const isSeller = userId === escrow.sellerUserId;
  const isBuyer = userId === escrow.buyerUserId;
  const currentStep = STATE_ORDER[escrow.state] ?? 0;
  const isCancelled = escrow.state === 'CANCELLED' || escrow.state === 'CANCELLING';
  const isCompleted = escrow.state === 'COMPLETED';

  const canFund = isBuyer && escrow.state === 'SETUP_COMPLETE';
  const canConfirm = (escrow.state === 'FUNDED' || escrow.state === 'SELLER_CONFIRMED' || escrow.state === 'BUYER_CONFIRMED')
    && ((isSeller && !escrow.sellerConfirmed) || (isBuyer && !escrow.buyerConfirmed));
  const canCancel = !isCompleted && !isCancelled && !(escrow.sellerConfirmed && escrow.buyerConfirmed);

  return (
    <div className="max-w-2xl mx-auto">
      <Link href="/vehicles" className="inline-flex items-center gap-2 text-body-sm text-ova-500 hover:text-ova-700 mb-6">
        <ArrowLeft size={16} /> Back to vehicles
      </Link>

      <h1 className="text-h2 text-ova-900 mb-2">Vehicle Escrow</h1>
      {vehicle && (
        <p className="text-body-sm text-ova-500 mb-8">
          {vehicle.year} {vehicle.make} {vehicle.model} · {vehicle.plateNumber}
        </p>
      )}

      <div className="space-y-6">
        {/* Progress Timeline */}
        <Card header="Progress">
          <div className="space-y-4">
            {isCancelled ? (
              <div className="flex items-center gap-3 text-ova-red">
                <XCircle size={20} />
                <span className="text-body font-medium">Escrow Cancelled</span>
              </div>
            ) : (
              STEPS.map((step, i) => {
                const done = currentStep > i || isCompleted;
                const active = currentStep === i && !isCompleted;
                return (
                  <div key={step.key} className="flex items-center gap-3">
                    {done ? (
                      <CheckCircle2 size={20} className="text-green-600 shrink-0" />
                    ) : active ? (
                      <Loader2 size={20} className="text-ova-blue animate-spin shrink-0" />
                    ) : (
                      <Circle size={20} className="text-ova-300 shrink-0" />
                    )}
                    <span className={`text-body-sm ${done ? 'text-ova-900 font-medium' : active ? 'text-ova-blue font-medium' : 'text-ova-400'}`}>
                      {step.label}
                    </span>
                  </div>
                );
              })
            )}
          </div>
        </Card>

        {/* Deal Info */}
        <Card header="Deal Summary">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <span className="text-caption text-ova-500 block">Sale Amount</span>
              <span className="text-body font-medium text-ova-900">
                {parseFloat(escrow.saleAmount).toLocaleString('tr-TR')} {escrow.currency}
              </span>
            </div>
            <div>
              <span className="text-caption text-ova-500 block">Fee</span>
              <span className="text-body font-medium text-ova-900">
                {parseFloat(escrow.feeAmount).toLocaleString('tr-TR')} {escrow.currency}
              </span>
            </div>
            <div>
              <span className="text-caption text-ova-500 block">Seller Confirmed</span>
              <span className={`text-body-sm font-medium ${escrow.sellerConfirmed ? 'text-green-600' : 'text-ova-400'}`}>
                {escrow.sellerConfirmed ? 'Yes' : 'No'}
              </span>
            </div>
            <div>
              <span className="text-caption text-ova-500 block">Buyer Confirmed</span>
              <span className={`text-body-sm font-medium ${escrow.buyerConfirmed ? 'text-green-600' : 'text-ova-400'}`}>
                {escrow.buyerConfirmed ? 'Yes' : 'No'}
              </span>
            </div>
          </div>
        </Card>

        {/* On-chain Proof */}
        {(escrow.setupTxHash || escrow.fundTxHash || escrow.completeTxHash) && (
          <Card header="On-chain Proof">
            <div className="space-y-3">
              {[
                { label: 'Setup', hash: escrow.setupTxHash },
                { label: 'Funding', hash: escrow.fundTxHash },
                { label: 'Completion', hash: escrow.completeTxHash },
              ].filter(t => t.hash).map(tx => (
                <div key={tx.label} className="flex items-center justify-between">
                  <span className="text-caption text-ova-500">{tx.label}</span>
                  <a href={`https://subnets.avax.network/ari-tr/tx/${tx.hash}`}
                    target="_blank" rel="noopener noreferrer"
                    className="text-ova-blue hover:underline text-caption inline-flex items-center gap-1 font-mono">
                    {tx.hash!.slice(0, 10)}... <ExternalLink size={12} />
                  </a>
                </div>
              ))}
            </div>
          </Card>
        )}

        {/* Actions */}
        <div className="space-y-3">
          {canFund && (
            <Button fullWidth size="lg" loading={actionLoading === 'fund'} onClick={() => doAction('fund')}>
              Fund Escrow ({(parseFloat(escrow.saleAmount) + parseFloat(escrow.feeAmount)).toLocaleString('tr-TR')} TRY)
            </Button>
          )}
          {canConfirm && (
            <Button fullWidth size="lg" loading={actionLoading === 'confirm'} onClick={() => doAction('confirm')}>
              {isSeller ? 'Confirm as Seller' : 'Confirm as Buyer'}
            </Button>
          )}
          {canCancel && (
            <Button fullWidth variant="danger" size="lg" loading={actionLoading === 'cancel'} onClick={() => doAction('cancel')}>
              Cancel Escrow
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
