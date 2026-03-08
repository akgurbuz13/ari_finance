'use client';

import { useState, useEffect, useCallback } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import { ArrowLeft, ExternalLink, Check, Loader2, XCircle, Circle, Car, Hash } from 'lucide-react';
import api from '../../../../../lib/api/client';
import type { VehicleEscrow, Vehicle } from '../../../../../lib/api/types';
import Card from '../../../../../components/ui/Card';
import Button from '../../../../../components/ui/Button';
import StatusPill from '../../../../../components/ui/StatusPill';
import { AvalancheVerified } from '../../../../../components/ui/AvalancheBadge';

const STEPS = [
  { key: 'CREATED', label: 'Escrow Created', description: 'Smart contract initialized' },
  { key: 'JOINING', label: 'Buyer Joined', description: 'Buyer entered the deal' },
  { key: 'SETUP_COMPLETE', label: 'On-chain Setup', description: 'Contract deployed on-chain' },
  { key: 'FUNDED', label: 'Funded', description: 'Payment locked in escrow' },
  { key: 'CONFIRMED', label: 'Both Confirmed', description: 'Seller and buyer approved' },
  { key: 'COMPLETED', label: 'Completed', description: 'NFT transferred, funds released' },
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
      <div className="max-w-2xl mx-auto flex items-center justify-center py-20">
        <Loader2 size={28} className="text-ova-400 animate-spin" />
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

  const txEntries = [
    { label: 'Setup', hash: escrow.setupTxHash },
    { label: 'Funding', hash: escrow.fundTxHash },
    { label: 'Completion', hash: escrow.completeTxHash },
  ].filter(t => t.hash);

  return (
    <div className="max-w-2xl mx-auto">
      {/* Back link */}
      <Link
        href="/vehicles"
        className="inline-flex items-center gap-2 text-body-sm text-ova-500 hover:text-ova-900 transition-colors mb-8"
      >
        <ArrowLeft size={16} />
        Back to vehicles
      </Link>

      {/* Header */}
      <div className="flex items-start justify-between mb-8">
        <div>
          <h1 className="text-h2 font-display text-ova-900 mb-1">Vehicle Escrow</h1>
          {vehicle && (
            <div className="flex items-center gap-2">
              <span className="text-body-sm text-ova-500">
                {vehicle.year} {vehicle.make} {vehicle.model}
              </span>
              <span className="text-caption text-ova-300">|</span>
              <span className="text-body-sm text-ova-400">{vehicle.plateNumber}</span>
            </div>
          )}
        </div>
        {isCancelled ? (
          <StatusPill variant="error" dot>Cancelled</StatusPill>
        ) : isCompleted ? (
          <StatusPill variant="success" dot>Completed</StatusPill>
        ) : (
          <StatusPill variant="info" dot>In Progress</StatusPill>
        )}
      </div>

      <div className="space-y-6">
        {/* Progress Timeline */}
        <Card header="Progress">
          {isCancelled ? (
            <div className="flex items-center gap-3 p-3 bg-ova-red/5 rounded-xl">
              <XCircle size={20} className="text-ova-red shrink-0" />
              <div>
                <span className="text-body-sm font-medium text-ova-red">Escrow Cancelled</span>
                <p className="text-caption text-ova-500 mt-0.5">
                  This deal has been cancelled. Any funds will be returned.
                </p>
              </div>
            </div>
          ) : (
            <div className="relative">
              {/* Vertical line */}
              <div className="absolute left-[11px] top-3 bottom-3 w-px bg-ova-200/60" />

              <div className="space-y-0">
                {STEPS.map((step, i) => {
                  const done = currentStep > i || isCompleted;
                  const active = currentStep === i && !isCompleted;
                  const pending = !done && !active;

                  return (
                    <div key={step.key} className="relative flex items-start gap-4 py-3">
                      {/* Dot */}
                      <div className="relative z-10 shrink-0">
                        {done ? (
                          <div className="flex h-[22px] w-[22px] items-center justify-center rounded-full bg-ova-green">
                            <Check size={12} className="text-white" />
                          </div>
                        ) : active ? (
                          <div className="flex h-[22px] w-[22px] items-center justify-center rounded-full bg-ova-navy">
                            <div className="h-2 w-2 rounded-full bg-white animate-pulse-subtle" />
                          </div>
                        ) : (
                          <div className="flex h-[22px] w-[22px] items-center justify-center rounded-full border-2 border-ova-200 bg-white">
                            <Circle size={8} className="text-ova-300" />
                          </div>
                        )}
                      </div>

                      {/* Content */}
                      <div className="flex-1 min-w-0 -mt-0.5">
                        <span
                          className={`text-body-sm font-medium block ${
                            done
                              ? 'text-ova-900'
                              : active
                                ? 'text-ova-900'
                                : 'text-ova-400'
                          }`}
                        >
                          {step.label}
                        </span>
                        <span
                          className={`text-caption block mt-0.5 ${
                            done || active ? 'text-ova-500' : 'text-ova-300'
                          }`}
                        >
                          {step.description}
                        </span>
                      </div>

                      {/* Status indicator */}
                      {done && (
                        <span className="text-micro text-ova-green uppercase tracking-wider shrink-0 mt-0.5">
                          Done
                        </span>
                      )}
                      {active && (
                        <span className="text-micro text-ova-navy uppercase tracking-wider shrink-0 mt-0.5 animate-pulse-subtle">
                          Active
                        </span>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </Card>

        {/* Deal Summary */}
        <Card header="Deal Summary">
          <div className="space-y-5">
            {/* Vehicle mini card */}
            {vehicle && (
              <div className="flex items-center gap-3 p-3 bg-ova-50 border border-ova-200/60 rounded-xl">
                <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-white border border-ova-200/60 shrink-0">
                  <Car size={16} className="text-ova-navy" />
                </div>
                <div className="flex-1 min-w-0">
                  <span className="text-body-sm font-medium text-ova-900 block">
                    {vehicle.year} {vehicle.make} {vehicle.model}
                  </span>
                  <span className="text-caption text-ova-400 font-mono">
                    {vehicle.plateNumber}
                    {vehicle.tokenId != null && ` | Token #${vehicle.tokenId}`}
                  </span>
                </div>
              </div>
            )}

            {/* Amounts */}
            <div className="grid grid-cols-2 gap-4">
              <div>
                <span className="micro-label block mb-1">Sale Amount</span>
                <span className="text-h3 font-display text-ova-900">
                  {parseFloat(escrow.saleAmount).toLocaleString('tr-TR')}
                </span>
                <span className="text-caption text-ova-500 ml-1">{escrow.currency}</span>
              </div>
              <div>
                <span className="micro-label block mb-1">Platform Fee</span>
                <span className="text-body font-semibold text-ova-900">
                  {parseFloat(escrow.feeAmount).toLocaleString('tr-TR')}
                </span>
                <span className="text-caption text-ova-500 ml-1">{escrow.currency}</span>
              </div>
            </div>

            {/* Confirmation status */}
            <div className="border-t border-ova-100 pt-4">
              <div className="grid grid-cols-2 gap-4">
                <div className="flex items-center gap-2">
                  <div className={`h-2 w-2 rounded-full ${escrow.sellerConfirmed ? 'bg-ova-green' : 'bg-ova-300'}`} />
                  <span className="text-body-sm text-ova-700">Seller</span>
                  <span className={`text-caption font-medium ${escrow.sellerConfirmed ? 'text-ova-green' : 'text-ova-400'}`}>
                    {escrow.sellerConfirmed ? 'Confirmed' : 'Pending'}
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  <div className={`h-2 w-2 rounded-full ${escrow.buyerConfirmed ? 'bg-ova-green' : 'bg-ova-300'}`} />
                  <span className="text-body-sm text-ova-700">Buyer</span>
                  <span className={`text-caption font-medium ${escrow.buyerConfirmed ? 'text-ova-green' : 'text-ova-400'}`}>
                    {escrow.buyerConfirmed ? 'Confirmed' : 'Pending'}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </Card>

        {/* On-Chain Proof */}
        {txEntries.length > 0 && (
          <Card header="On-Chain Proof">
            <div className="space-y-4">
              {txEntries.map(tx => (
                <div key={tx.label}>
                  <AvalancheVerified txHash={tx.hash} />
                  <div className="flex items-center justify-between mt-2 px-1">
                    <span className="micro-label">{tx.label}</span>
                    <a
                      href={`https://subnets.avax.network/ari-tr/tx/${tx.hash}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1 text-caption text-ova-700 hover:text-ova-900 font-medium transition-colors"
                    >
                      View on Explorer
                      <ExternalLink size={11} />
                    </a>
                  </div>
                </div>
              ))}
            </div>
          </Card>
        )}

        {/* Actions */}
        {(canFund || canConfirm || canCancel) && (
          <div className="space-y-3">
            {canFund && (
              <Button
                fullWidth
                size="lg"
                loading={actionLoading === 'fund'}
                onClick={() => doAction('fund')}
              >
                Fund Escrow ({(parseFloat(escrow.saleAmount) + parseFloat(escrow.feeAmount)).toLocaleString('tr-TR')} TRY)
              </Button>
            )}
            {canConfirm && (
              <Button
                fullWidth
                size="lg"
                loading={actionLoading === 'confirm'}
                onClick={() => doAction('confirm')}
              >
                {isSeller ? 'Confirm as Seller' : 'Confirm as Buyer'}
              </Button>
            )}
            {canCancel && (
              <Button
                fullWidth
                variant="danger"
                size="lg"
                loading={actionLoading === 'cancel'}
                onClick={() => doAction('cancel')}
              >
                Cancel Escrow
              </Button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
