'use client';

import { useState, useEffect } from 'react';
import api from '../../../lib/api/client';
import type { KycStatus } from '../../../lib/api/types';
import Button from '../../../components/ui/Button';
import Card from '../../../components/ui/Card';

const steps = ['Identity', 'Documents', 'Review'];

export default function KycPage() {
  const [kycStatus, setKycStatus] = useState<KycStatus | null>(null);
  const [currentStep, setCurrentStep] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get<KycStatus>('/kyc/status')
      .then(({ data }) => setKycStatus(data))
      .catch(() => setKycStatus(null))
      .finally(() => setLoading(false));
  }, []);

  const initiateKyc = async () => {
    try {
      await api.post('/kyc/initiate', {
        provider: 'veriff',
        providerRef: `kyc-${Date.now()}`,
        level: 'basic',
      });
      setCurrentStep(1);
    } catch {
      alert('Failed to start KYC');
    }
  };

  if (loading) {
    return <div className="flex items-center justify-center h-64 text-gray-400">Loading...</div>;
  }

  if (kycStatus?.status === 'approved') {
    return (
      <div className="max-w-lg mx-auto text-center py-20">
        <div className="text-5xl mb-4">✓</div>
        <h1 className="text-2xl font-bold text-black">Verified</h1>
        <p className="text-gray-500 mt-2">Your identity has been verified.</p>
      </div>
    );
  }

  return (
    <div className="max-w-lg mx-auto space-y-8 py-10">
      <div className="text-center">
        <h1 className="text-3xl font-bold text-black">Ova</h1>
        <p className="text-gray-500 mt-2">Verify your identity</p>
      </div>

      {/* Steps */}
      <div className="flex justify-center gap-4">
        {steps.map((step, i) => (
          <div key={step} className="flex items-center gap-2">
            <div className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-medium ${
              i <= currentStep ? 'bg-black text-white' : 'bg-gray-200 text-gray-500'
            }`}>
              {i + 1}
            </div>
            <span className={`text-sm ${i <= currentStep ? 'text-black' : 'text-gray-400'}`}>
              {step}
            </span>
            {i < steps.length - 1 && <div className="w-8 h-px bg-gray-300 mx-2" />}
          </div>
        ))}
      </div>

      <Card>
        {kycStatus?.status === 'pending' ? (
          <div className="text-center py-8">
            <div className="text-3xl mb-4">⏳</div>
            <p className="font-medium text-black">Verification in progress</p>
            <p className="text-sm text-gray-500 mt-2">We are reviewing your documents.</p>
          </div>
        ) : kycStatus?.status === 'rejected' ? (
          <div className="text-center py-8">
            <div className="text-3xl mb-4">✕</div>
            <p className="font-medium text-red-600">Verification failed</p>
            <p className="text-sm text-gray-500 mt-2">Please try again.</p>
            <Button className="mt-4" onClick={initiateKyc}>Retry Verification</Button>
          </div>
        ) : (
          <div className="text-center py-8">
            <p className="text-sm text-gray-500 mb-6">
              We need to verify your identity to comply with financial regulations.
            </p>
            <Button onClick={initiateKyc}>Start Verification</Button>
          </div>
        )}
      </Card>
    </div>
  );
}
