'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { ArrowLeft } from 'lucide-react';
import Link from 'next/link';
import api from '../../../../lib/api/client';
import Card from '../../../../components/ui/Card';
import Button from '../../../../components/ui/Button';
import Input from '../../../../components/ui/Input';

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
      <Link href="/vehicles" className="inline-flex items-center gap-2 text-body-sm text-ova-500 hover:text-ova-700 mb-6">
        <ArrowLeft size={16} /> Back to vehicles
      </Link>

      <h1 className="text-h2 text-ova-900 mb-2">Register Vehicle</h1>
      <p className="text-body-sm text-ova-500 mb-8">
        Register your vehicle to mint an NFT on the ARI blockchain. Ownership is verified on-chain.
      </p>

      <Card>
        <form onSubmit={handleSubmit} className="space-y-5">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
            <Input label="VIN (17 characters)" value={form.vin} onChange={e => update('vin', e.target.value.toUpperCase())}
              placeholder="WVWZZZ3CZWE123456" maxLength={17} required />
            <Input label="Plate Number" value={form.plateNumber} onChange={e => update('plateNumber', e.target.value.toUpperCase())}
              placeholder="34 ABC 123" required />
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-5">
            <Input label="Make" value={form.make} onChange={e => update('make', e.target.value)}
              placeholder="Volkswagen" required />
            <Input label="Model" value={form.model} onChange={e => update('model', e.target.value)}
              placeholder="Golf" required />
            <Input label="Year" type="number" value={form.year} onChange={e => update('year', e.target.value)}
              placeholder="2024" min={1900} max={2030} required />
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
            <Input label="Color" value={form.color} onChange={e => update('color', e.target.value)}
              placeholder="White" />
            <Input label="Mileage (km)" type="number" value={form.mileage} onChange={e => update('mileage', e.target.value)}
              placeholder="45000" />
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
            <div>
              <label className="block text-body-sm font-medium text-ova-700 mb-3">Fuel Type</label>
              <select value={form.fuelType} onChange={e => update('fuelType', e.target.value)}
                className="w-full h-12 px-4 bg-white border border-ova-300 rounded-xl text-ova-900">
                <option value="">Select...</option>
                <option value="Gasoline">Gasoline</option>
                <option value="Diesel">Diesel</option>
                <option value="Electric">Electric</option>
                <option value="Hybrid">Hybrid</option>
                <option value="LPG">LPG</option>
              </select>
            </div>
            <div>
              <label className="block text-body-sm font-medium text-ova-700 mb-3">Transmission</label>
              <select value={form.transmission} onChange={e => update('transmission', e.target.value)}
                className="w-full h-12 px-4 bg-white border border-ova-300 rounded-xl text-ova-900">
                <option value="">Select...</option>
                <option value="Manual">Manual</option>
                <option value="Automatic">Automatic</option>
              </select>
            </div>
          </div>

          {error && <p className="text-body-sm text-ova-red">{error}</p>}

          <Button type="submit" loading={loading} fullWidth size="lg">
            Register & Mint NFT
          </Button>
        </form>
      </Card>
    </div>
  );
}
