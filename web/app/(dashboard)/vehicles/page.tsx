'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { Car, Plus, ArrowRight } from 'lucide-react';
import api from '../../../lib/api/client';
import type { Vehicle } from '../../../lib/api/types';
import Card from '../../../components/ui/Card';
import Button from '../../../components/ui/Button';
import Skeleton from '../../../components/ui/Skeleton';
import { STATUS_COLORS, STATUS_LABELS } from './constants';

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
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-h2 text-ova-900">My Vehicles</h1>
          <p className="text-body-sm text-ova-500 mt-1">Register and manage your vehicle NFTs</p>
        </div>
        <Link href="/vehicles/register">
          <Button>
            <Plus size={18} className="mr-2" />
            Register Vehicle
          </Button>
        </Link>
      </div>

      {loading ? (
        <div className="space-y-4">
          {[1, 2].map(i => (
            <Card key={i}><Skeleton className="h-24 w-full" /></Card>
          ))}
        </div>
      ) : vehicles.length === 0 ? (
        <Card>
          <div className="text-center py-12">
            <Car size={48} className="mx-auto text-ova-300 mb-4" />
            <h3 className="text-h3 text-ova-700 mb-2">No vehicles registered</h3>
            <p className="text-body-sm text-ova-500 mb-6">Register your first vehicle to mint an NFT on the blockchain</p>
            <Link href="/vehicles/register">
              <Button>Register Your First Vehicle</Button>
            </Link>
          </div>
        </Card>
      ) : (
        <div className="space-y-4">
          {vehicles.map(vehicle => (
            <Link key={vehicle.id} href={`/vehicles/${vehicle.id}`}>
              <Card hover className="mb-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-4">
                    <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-ova-100">
                      <Car size={24} className="text-ova-navy" />
                    </div>
                    <div>
                      <h3 className="text-body font-medium text-ova-900">
                        {vehicle.year} {vehicle.make} {vehicle.model}
                      </h3>
                      <p className="text-body-sm text-ova-500">
                        {vehicle.plateNumber}
                        {vehicle.tokenId != null && ` · Token #${vehicle.tokenId}`}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className={`px-3 py-1 rounded-full text-caption font-medium ${STATUS_COLORS[vehicle.status] || 'bg-ova-100 text-ova-600'}`}>
                      {STATUS_LABELS[vehicle.status] || vehicle.status}
                    </span>
                    <ArrowRight size={16} className="text-ova-400" />
                  </div>
                </div>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
