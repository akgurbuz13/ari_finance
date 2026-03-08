'use client';

import { useState, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { ArrowLeft, ExternalLink, Car, Hash, MapPin, Gauge, Fuel, Settings2 } from 'lucide-react';
import api from '../../../../lib/api/client';
import type { Vehicle } from '../../../../lib/api/types';
import Card from '../../../../components/ui/Card';
import Button from '../../../../components/ui/Button';
import Skeleton from '../../../../components/ui/Skeleton';
import StatusPill from '../../../../components/ui/StatusPill';
import GradientHero from '../../../../components/ui/GradientHero';
import AvalancheBadge from '../../../../components/ui/AvalancheBadge';
import { AvalancheVerified } from '../../../../components/ui/AvalancheBadge';
import { STATUS_VARIANT, STATUS_LABELS } from '../constants';

export default function VehicleDetailPage() {
  const params = useParams();
  const router = useRouter();
  const [vehicle, setVehicle] = useState<Vehicle | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get(`/vehicles/${params.id}`).then(res => {
      setVehicle(res.data);
      setLoading(false);
    }).catch(() => setLoading(false));
  }, [params.id]);

  if (loading) {
    return (
      <div className="max-w-2xl mx-auto">
        <Skeleton className="h-6 w-32 mb-8" />
        <div className="bg-white border border-ari-200/60 rounded-2xl p-6">
          <Skeleton variant="rectangular" className="h-48 w-full rounded-xl mb-4" />
          <Skeleton className="h-5 w-64 mb-2" />
          <Skeleton className="h-4 w-40" />
        </div>
      </div>
    );
  }

  if (!vehicle) {
    return (
      <div className="max-w-2xl mx-auto text-center py-16">
        <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-ari-50 border border-ari-200/60 mx-auto mb-4">
          <Car size={28} className="text-ari-400" />
        </div>
        <h3 className="text-h3 font-display text-ari-900 mb-1">Vehicle not found</h3>
        <p className="text-body-sm text-ari-500">This vehicle may have been removed or does not exist</p>
      </div>
    );
  }

  const explorerBase = vehicle.chainId
    ? `https://subnets.avax.network/ari-tr`
    : `https://subnets.avax.network/ari-tr`;

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

      <div className="space-y-6">
        {/* Hero */}
        <GradientHero>
          <div className="flex items-start justify-between mb-6">
            <StatusPill
              variant={STATUS_VARIANT[vehicle.status] || 'neutral'}
              dot
            >
              {STATUS_LABELS[vehicle.status] || vehicle.status}
            </StatusPill>
            {vehicle.tokenId != null && (
              <AvalancheBadge size="md" />
            )}
          </div>

          <h1 className="text-h1 font-display text-white mb-3">
            {vehicle.year} {vehicle.make} {vehicle.model}
          </h1>

          <div className="flex items-center gap-3 flex-wrap">
            <span className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-white/10 rounded-lg text-body-sm text-white/80 font-medium">
              <MapPin size={14} />
              {vehicle.plateNumber}
            </span>
            {vehicle.tokenId != null && (
              <span className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-white/10 rounded-lg text-body-sm text-white/80 font-mono">
                <Hash size={14} />
                Token #{vehicle.tokenId}
              </span>
            )}
          </div>

          {/* Pending indicator */}
          {vehicle.status === 'PENDING' && (
            <div className="flex items-center gap-2 mt-6 pt-5 border-t border-white/10">
              <div className="h-2 w-2 rounded-full bg-ari-amber animate-pulse-subtle" />
              <span className="text-body-sm text-white/60">Minting NFT on blockchain...</span>
            </div>
          )}
        </GradientHero>

        {/* On-Chain Proof */}
        {vehicle.tokenId != null && vehicle.mintTxHash && (
          <Card header="On-Chain Proof">
            <div className="space-y-5">
              <AvalancheVerified txHash={vehicle.mintTxHash} />

              <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
                <div>
                  <span className="micro-label block mb-1">Token ID</span>
                  <span className="text-body font-semibold text-ari-900 font-mono">
                    #{vehicle.tokenId}
                  </span>
                </div>
                <div>
                  <span className="micro-label block mb-1">Chain ID</span>
                  <span className="text-body font-semibold text-ari-900 font-mono">
                    {vehicle.chainId}
                  </span>
                </div>
                <div>
                  <span className="micro-label block mb-1">VIN</span>
                  <span className="text-caption text-ari-700 font-mono truncate block">
                    {vehicle.vin}
                  </span>
                </div>
              </div>

              <a
                href={`${explorerBase}/tx/${vehicle.mintTxHash}`}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1.5 text-body-sm text-ari-700 hover:text-ari-900 font-medium transition-colors"
              >
                View on Explorer
                <ExternalLink size={14} />
              </a>
            </div>
          </Card>
        )}

        {/* Vehicle Details */}
        <Card header="Vehicle Details">
          <div className="grid grid-cols-2 gap-x-6 gap-y-5">
            <DetailItem icon={Car} label="Make" value={vehicle.make} />
            <DetailItem icon={Car} label="Model" value={vehicle.model} />
            <DetailItem icon={Hash} label="Year" value={String(vehicle.year)} />
            <DetailItem icon={MapPin} label="Plate" value={vehicle.plateNumber} />
            {vehicle.color && (
              <DetailItem label="Color" value={vehicle.color} />
            )}
            {vehicle.mileage != null && (
              <DetailItem icon={Gauge} label="Mileage" value={`${vehicle.mileage.toLocaleString()} km`} />
            )}
            {vehicle.fuelType && (
              <DetailItem icon={Fuel} label="Fuel" value={vehicle.fuelType} />
            )}
            {vehicle.transmission && (
              <DetailItem icon={Settings2} label="Transmission" value={vehicle.transmission} />
            )}
          </div>
        </Card>

        {/* Sell action */}
        {vehicle.status === 'MINTED' && (
          <Button
            fullWidth
            size="lg"
            onClick={() => router.push(`/vehicles/escrow/create?vehicleId=${vehicle.id}`)}
          >
            Sell This Vehicle
          </Button>
        )}
      </div>
    </div>
  );
}

function DetailItem({
  icon: Icon,
  label,
  value,
}: {
  icon?: React.ElementType;
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-start gap-3">
      {Icon && (
        <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-ari-50 shrink-0 mt-0.5">
          <Icon size={14} className="text-ari-400" />
        </div>
      )}
      <div>
        <span className="micro-label block mb-0.5">{label}</span>
        <span className="text-body-sm font-medium text-ari-900">{value}</span>
      </div>
    </div>
  );
}
