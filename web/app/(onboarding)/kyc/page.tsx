'use client';

import { useState, useEffect } from 'react';
import { ShieldCheck, Clock, CheckCircle2, Check, AlertCircle } from 'lucide-react';
import { clsx } from 'clsx';
import api from '../../../lib/api/client';
import type { KycStatus } from '../../../lib/api/types';
import Button from '../../../components/ui/Button';
import Card from '../../../components/ui/Card';
import Skeleton, { SkeletonCard } from '../../../components/ui/Skeleton';

const steps = ['Identity', 'Documents', 'Review'];

export default function KycPage() {
  const [kycStatus, setKycStatus] = useState<KycStatus | null>(null);
  const [currentStep, setCurrentStep] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    api.get<KycStatus>('/kyc/status')
      .then(({ data }) => {
        setKycStatus(data);
        if (data.status === 'pending') setCurrentStep(2);
        if (data.status === 'approved') setCurrentStep(3);
      })
      .catch(() => setKycStatus(null))
      .finally(() => setLoading(false));
  }, []);

  const initiateKyc = async () => {
    setError('');
    try {
      await api.post('/kyc/initiate', {
        provider: 'veriff',
        providerRef: `kyc-${Date.now()}`,
        level: 'basic',
      });
      setCurrentStep(1);
    } catch {
      setError('Failed to start verification. Please try again.');
    }
  };

  if (loading) {
    return (
      <div className="max-w-form mx-auto space-y-8 py-10">
        <div className="text-center space-y-2">
          <Skeleton variant="text" className="w-48 h-7 mx-auto" />
          <Skeleton variant="text" className="w-64 h-5 mx-auto" />
        </div>
        <SkeletonCard />
      </div>
    );
  }

  const isApproved = kycStatus?.status === 'approved';
  const isPending = kycStatus?.status === 'pending';
  const isRejected = kycStatus?.status === 'rejected';

  // Determine stepper state
  const stepperState = isApproved ? 3 : isPending ? 2 : currentStep;

  return (
    <div className="max-w-form mx-auto space-y-8 py-10">
      {/* Header */}
      <div className="text-center space-y-2">
        <h1 className="text-h2 text-ova-900">Verify your identity</h1>
        <p className="text-body text-ova-500">Unlock international transfers up to EUR 50,000</p>
        <p className="text-caption text-ova-400">Takes about 5 minutes</p>
      </div>

      {/* Horizontal stepper */}
      <div className="flex items-center gap-2">
        {steps.map((step, i) => (
          <div key={step} className="contents">
            <div className="flex items-center gap-3">
              <div className={clsx(
                'flex h-8 w-8 items-center justify-center rounded-full text-body-sm font-medium',
                i < stepperState ? 'bg-ova-green text-white' :
                i === stepperState ? 'bg-ova-navy text-white' :
                'bg-ova-200 text-ova-500',
              )}>
                {i < stepperState ? <Check size={16} strokeWidth={2} /> : i + 1}
              </div>
              <span className={clsx(
                'text-body-sm font-medium',
                i <= stepperState ? 'text-ova-900' : 'text-ova-400',
              )}>
                {step}
              </span>
            </div>
            {i < steps.length - 1 && (
              <div className={clsx(
                'h-px flex-1',
                i < stepperState ? 'bg-ova-green' : 'bg-ova-200',
              )} />
            )}
          </div>
        ))}
      </div>

      {/* Error banner */}
      {error && (
        <div className="p-3 bg-ova-red-light border border-ova-red/20 rounded-xl text-body-sm text-ova-red">
          {error}
        </div>
      )}

      {/* Status cards */}
      {isApproved && (
        <Card>
          <div className="text-center py-8 space-y-4">
            <div className="flex h-14 w-14 items-center justify-center rounded-full bg-ova-green-light mx-auto">
              <CheckCircle2 size={28} strokeWidth={1.5} className="text-ova-green" />
            </div>
            <h2 className="text-h3 text-ova-900">Identity verified</h2>
            <p className="text-body-sm text-ova-500">You&apos;ve unlocked:</p>
            <ul className="text-body-sm text-ova-700 space-y-2 text-left inline-block">
              <li className="flex items-center gap-2">
                <Check size={16} className="text-ova-green" /> International transfers up to EUR 50,000
              </li>
              <li className="flex items-center gap-2">
                <Check size={16} className="text-ova-green" /> Full FX conversion access
              </li>
              <li className="flex items-center gap-2">
                <Check size={16} className="text-ova-green" /> Priority support
              </li>
            </ul>
          </div>
        </Card>
      )}

      {isPending && (
        <Card>
          <div className="text-center py-8 space-y-4">
            <div className="flex h-14 w-14 items-center justify-center rounded-full bg-ova-amber-light mx-auto">
              <Clock size={28} strokeWidth={1.5} className="text-ova-amber" />
            </div>
            <p className="text-body-sm font-medium text-ova-900">We&apos;re reviewing your documents</p>
            <p className="text-body-sm text-ova-500">This usually takes a few minutes.</p>
          </div>
        </Card>
      )}

      {isRejected && (
        <Card>
          <div className="text-center py-8 space-y-4">
            <div className="flex h-14 w-14 items-center justify-center rounded-full bg-ova-red-light mx-auto">
              <AlertCircle size={28} strokeWidth={1.5} className="text-ova-red" />
            </div>
            <h2 className="text-h3 text-ova-900">Verification unsuccessful</h2>
            <p className="text-body-sm text-ova-500">
              We couldn&apos;t verify your identity. This can happen if documents are unclear or expired.
            </p>
            <div className="flex gap-3 justify-center pt-2">
              <Button onClick={initiateKyc}>Try again</Button>
              <Button variant="ghost">Contact support</Button>
            </div>
          </div>
        </Card>
      )}

      {!isApproved && !isPending && !isRejected && (
        <Card>
          <div className="text-center py-8 space-y-4">
            <div className="flex h-14 w-14 items-center justify-center rounded-full bg-ova-blue-light mx-auto">
              <ShieldCheck size={28} strokeWidth={1.5} className="text-ova-blue" />
            </div>
            <p className="text-body text-ova-700">
              Verify your identity to unlock international transfers up to EUR 50,000
            </p>
            <Button onClick={initiateKyc}>Start Verification</Button>
          </div>
        </Card>
      )}
    </div>
  );
}
