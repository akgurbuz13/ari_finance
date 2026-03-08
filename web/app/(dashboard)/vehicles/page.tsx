'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { Car, Plus, ArrowRight, ChevronRight } from 'lucide-react';
import api from '../../../lib/api/client';
import type { Vehicle } from '../../../lib/api/types';
import Button from '../../../components/ui/Button';
import Skeleton from '../../../components/ui/Skeleton';
import StatusPill from '../../../components/ui/StatusPill';
import AvalancheBadge from '../../../components/ui/AvalancheBadge';
import { STATUS_VARIANT, STATUS_LABELS } from './constants';

export default function VehiclesPage() {
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get('/vehicles').then(res => {
      setVehicles(res.data);
      setLoading(false);
    }).catch(() => setLoading(false));
  }, []);

  return (
    <div className="max-w-4xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between mb-10">
        <div>
          <h1 className="text-h2 font-display text-ova-900">My Vehicles</h1>
          <p className="text-body-sm text-ova-500 mt-1">
            Your vehicles registered as NFTs on Avalanche
          </p>
        </div>
        <Link href="/vehicles/register">
          <Button size="md">
            <Plus size={16} className="mr-1.5" />
            Register Vehicle
          </Button>
        </Link>
      </div>

      {/* Loading */}
      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3].map(i => (
            <div key={i} className="bg-white border border-ova-200/60 rounded-2xl p-5">
              <div className="flex items-center gap-4">
                <Skeleton variant="rectangular" className="h-12 w-12 rounded-xl" />
                <div className="flex-1">
                  <Skeleton className="h-4 w-48 mb-2" />
                  <Skeleton className="h-3 w-32" />
                </div>
                <Skeleton className="h-6 w-20 rounded-full" />
              </div>
            </div>
          ))}
        </div>
      ) : vehicles.length === 0 ? (
        /* Empty State */
        <div className="bg-white border border-ova-200/60 rounded-2xl">
          <div className="text-center py-16 px-6">
            <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-ova-50 border border-ova-200/60 mx-auto mb-5">
              <Car size={28} className="text-ova-400" />
            </div>
            <h3 className="text-h3 font-display text-ova-900 mb-2">No vehicles registered</h3>
            <p className="text-body-sm text-ova-500 mb-8 max-w-sm mx-auto">
              Register your first vehicle to mint it as an NFT on the Avalanche blockchain
            </p>
            <Link href="/vehicles/register">
              <Button size="lg">
                <Plus size={16} className="mr-1.5" />
                Register Your First Vehicle
              </Button>
            </Link>
          </div>
        </div>
      ) : (
        /* Vehicle List */
        <div className="space-y-3">
          {vehicles.map(vehicle => (
            <Link key={vehicle.id} href={`/vehicles/${vehicle.id}`}>
              <div className="bg-white border border-ova-200/60 rounded-2xl p-5 hover:border-ova-300 hover:shadow-card-hover transition-all duration-base cursor-pointer group mb-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-4">
                    <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-ova-50 border border-ova-200/60">
                      <Car size={22} className="text-ova-navy" />
                    </div>
                    <div>
                      <h3 className="text-body font-semibold text-ova-900">
                        {vehicle.year} {vehicle.make} {vehicle.model}
                      </h3>
                      <div className="flex items-center gap-2 mt-0.5">
                        <span className="text-body-sm text-ova-500">{vehicle.plateNumber}</span>
                        {vehicle.tokenId != null && (
                          <span className="text-caption text-ova-400 font-mono">
                            Token #{vehicle.tokenId}
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    <StatusPill
                      variant={STATUS_VARIANT[vehicle.status] || 'neutral'}
                      dot
                    >
                      {STATUS_LABELS[vehicle.status] || vehicle.status}
                    </StatusPill>
                    {vehicle.tokenId != null && (
                      <AvalancheBadge size="sm" />
                    )}
                    <ChevronRight size={16} className="text-ova-300 group-hover:text-ova-500 transition-colors" />
                  </div>
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
