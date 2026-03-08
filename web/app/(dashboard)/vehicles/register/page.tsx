'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { ArrowLeft, Hexagon } from 'lucide-react';
import Link from 'next/link';
import api from '../../../../lib/api/client';
import Card from '../../../../components/ui/Card';
import Button from '../../../../components/ui/Button';
import Input from '../../../../components/ui/Input';
import AvalancheBadge from '../../../../components/ui/AvalancheBadge';

export default function RegisterVehiclePage() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [form, setForm] = useState({
    vin: '', plateNumber: '', make: '', model: '',
    year: '', color: '', mileage: '', fuelType: '', transmission: '',
  });

  const update = (field: string, value: string) =>
    setForm(prev => ({ ...prev, [field]: value }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      await api.post('/vehicles', {
        vin: form.vin,
        plateNumber: form.plateNumber,
        make: form.make,
        model: form.model,
        year: parseInt(form.year),
        color: form.color || null,
        mileage: form.mileage ? parseInt(form.mileage) : null,
        fuelType: form.fuelType || null,
        transmission: form.transmission || null,
      });
      router.push('/vehicles');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to register vehicle');
      setLoading(false);
    }
  };

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
      <div className="mb-8">
        <h1 className="text-h2 font-display text-ova-900 mb-2">Register Vehicle</h1>
        <p className="text-body-sm text-ova-500">
          Your vehicle details will be hashed and stored on-chain as a unique NFT
        </p>
      </div>

      {/* Avalanche info banner */}
      <div className="flex items-center gap-3 p-4 bg-ova-50 border border-ova-200/60 rounded-xl mb-6">
        <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-white border border-ova-200/60 shrink-0">
          <Hexagon size={16} className="text-ova-navy" />
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-body-sm text-ova-700 font-medium">
            Your vehicle will be minted as an NFT on Avalanche L1
          </p>
          <p className="text-caption text-ova-400 mt-0.5">
            Immutable ownership proof secured by blockchain
          </p>
        </div>
        <AvalancheBadge size="sm" />
      </div>

      {/* Form */}
      <Card>
        <form onSubmit={handleSubmit} className="space-y-6">
          {/* VIN + Plate */}
          <div>
            <span className="micro-label block mb-4">Identification</span>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
              <Input
                label="VIN (17 characters)"
                value={form.vin}
                onChange={e => update('vin', e.target.value.toUpperCase())}
                placeholder="WVWZZZ3CZWE123456"
                maxLength={17}
                required
              />
              <Input
                label="Plate Number"
                value={form.plateNumber}
                onChange={e => update('plateNumber', e.target.value.toUpperCase())}
                placeholder="34 ABC 123"
                required
              />
            </div>
          </div>

          {/* Make / Model / Year */}
          <div>
            <span className="micro-label block mb-4">Vehicle Details</span>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-5">
              <Input
                label="Make"
                value={form.make}
                onChange={e => update('make', e.target.value)}
                placeholder="Volkswagen"
                required
              />
              <Input
                label="Model"
                value={form.model}
                onChange={e => update('model', e.target.value)}
                placeholder="Golf"
                required
              />
              <Input
                label="Year"
                type="number"
                value={form.year}
                onChange={e => update('year', e.target.value)}
                placeholder="2024"
                min={1900}
                max={2030}
                required
              />
            </div>
          </div>

          {/* Color / Mileage */}
          <div>
            <span className="micro-label block mb-4">Specifications</span>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
              <Input
                label="Color"
                value={form.color}
                onChange={e => update('color', e.target.value)}
                placeholder="White"
              />
              <Input
                label="Mileage (km)"
                type="number"
                value={form.mileage}
                onChange={e => update('mileage', e.target.value)}
                placeholder="45000"
              />
            </div>
          </div>

          {/* Fuel / Transmission */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
            <div>
              <label className="block text-body-sm font-medium text-ova-700 mb-2">
                Fuel Type
              </label>
              <select
                value={form.fuelType}
                onChange={e => update('fuelType', e.target.value)}
                className="w-full h-11 px-4 bg-ova-50 border border-ova-200 rounded-xl text-ova-900 text-body-sm transition-all duration-base focus:outline-none focus:bg-white focus:border-ova-900 focus:ring-1 focus:ring-ova-900/10"
              >
                <option value="">Select...</option>
                <option value="Gasoline">Gasoline</option>
                <option value="Diesel">Diesel</option>
                <option value="Electric">Electric</option>
                <option value="Hybrid">Hybrid</option>
                <option value="LPG">LPG</option>
              </select>
            </div>
            <div>
              <label className="block text-body-sm font-medium text-ova-700 mb-2">
                Transmission
              </label>
              <select
                value={form.transmission}
                onChange={e => update('transmission', e.target.value)}
                className="w-full h-11 px-4 bg-ova-50 border border-ova-200 rounded-xl text-ova-900 text-body-sm transition-all duration-base focus:outline-none focus:bg-white focus:border-ova-900 focus:ring-1 focus:ring-ova-900/10"
              >
                <option value="">Select...</option>
                <option value="Manual">Manual</option>
                <option value="Automatic">Automatic</option>
              </select>
            </div>
          </div>

          {/* Divider */}
          <div className="border-t border-ova-100" />

          {/* Error */}
          {error && (
            <div className="p-3 bg-ova-red-light rounded-xl border border-ova-red/10">
              <p className="text-body-sm text-ova-red">{error}</p>
            </div>
          )}

          {/* Submit */}
          <Button type="submit" loading={loading} fullWidth size="lg">
            Register & Mint NFT
          </Button>
        </form>
      </Card>
    </div>
  );
}
