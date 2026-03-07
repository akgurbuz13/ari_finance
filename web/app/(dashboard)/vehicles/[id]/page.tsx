'use client';

import { useState, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { ArrowLeft, ExternalLink, Shield, Tag } from 'lucide-react';
import api from '../../../../lib/api/client';
import type { Vehicle } from '../../../../lib/api/types';
import Card from '../../../../components/ui/Card';
import Button from '../../../../components/ui/Button';
import Skeleton from '../../../../components/ui/Skeleton';
import { STATUS_COLORS } from '../constants';

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
        <Card><Skeleton className="h-64 w-full" /></Card>
      </div>
    );
  }

  if (!vehicle) {
    return (
      <div className="max-w-2xl mx-auto text-center py-12">
        <p className="text-ova-500">Vehicle not found</p>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto">
      <Link href="/vehicles" className="inline-flex items-center gap-2 text-body-sm text-ova-500 hover:text-ova-700 mb-6">
        <ArrowLeft size={16} /> Back to vehicles
      </Link>

      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-h2 text-ova-900">{vehicle.year} {vehicle.make} {vehicle.model}</h1>
          <p className="text-body-sm text-ova-500 mt-1">{vehicle.plateNumber}</p>
        </div>
        <span className={`px-3 py-1 rounded-full text-caption font-medium ${STATUS_COLORS[vehicle.status] || 'bg-ova-100 text-ova-600'}`}>
          {vehicle.status}
        </span>
      </div>

      <div className="space-y-6">
        <Card header="Vehicle Details">
          <div className="grid grid-cols-2 gap-4">
            <Detail label="Make" value={vehicle.make} />
            <Detail label="Model" value={vehicle.model} />
            <Detail label="Year" value={String(vehicle.year)} />
            <Detail label="Plate" value={vehicle.plateNumber} />
            {vehicle.color && <Detail label="Color" value={vehicle.color} />}
            {vehicle.mileage && <Detail label="Mileage" value={`${vehicle.mileage.toLocaleString()} km`} />}
            {vehicle.fuelType && <Detail label="Fuel" value={vehicle.fuelType} />}
            {vehicle.transmission && <Detail label="Transmission" value={vehicle.transmission} />}
          </div>
        </Card>

        <Card header="Blockchain">
          <div className="space-y-3">
            {vehicle.tokenId != null ? (
              <>
                <div className="flex items-center gap-2">
                  <Shield size={16} className="text-green-600" />
                  <span className="text-body-sm text-ova-700">
                    NFT Token #{vehicle.tokenId} on Chain {vehicle.chainId}
                  </span>
                </div>
                {vehicle.mintTxHash && (
                  <div className="flex items-center gap-2">
                    <Tag size={16} className="text-ova-500" />
                    <span className="text-caption text-ova-500 font-mono truncate">{vehicle.mintTxHash}</span>
                    <a href={`https://subnets.avax.network/ari-tr/tx/${vehicle.mintTxHash}`}
                      target="_blank" rel="noopener noreferrer"
                      className="text-ova-blue hover:underline inline-flex items-center gap-1 text-caption">
                      Verify <ExternalLink size={12} />
                    </a>
                  </div>
                )}
              </>
            ) : (
              <div className="flex items-center gap-2">
                <div className="h-4 w-4 rounded-full border-2 border-yellow-500 border-t-transparent animate-spin" />
                <span className="text-body-sm text-ova-500">Minting NFT on blockchain...</span>
              </div>
            )}
          </div>
        </Card>

        {vehicle.status === 'MINTED' && (
          <Button fullWidth size="lg" onClick={() => router.push(`/vehicles/escrow/create?vehicleId=${vehicle.id}`)}>
            Sell This Vehicle
          </Button>
        )}
      </div>
    </div>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <span className="text-caption text-ova-500 block">{label}</span>
      <span className="text-body-sm text-ova-900 font-medium">{value}</span>
    </div>
  );
}
