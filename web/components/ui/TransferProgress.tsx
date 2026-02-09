'use client';

import { useState } from 'react';
import { Check, ChevronRight } from 'lucide-react';
import { clsx } from 'clsx';

type StepStatus = 'pending' | 'active' | 'completed';

interface StepDetail {
  label: string;
  value: string;
}

export interface TransferStep {
  label: string;
  status: StepStatus;
  timestamp?: string;
  details?: StepDetail[];
}

interface TransferProgressProps {
  steps: TransferStep[];
  className?: string;
}

function StepIndicator({ status }: { status: StepStatus }) {
  if (status === 'completed') {
    return (
      <div className="flex h-6 w-6 items-center justify-center rounded-full bg-ova-green text-white">
        <Check size={14} strokeWidth={2.5} />
      </div>
    );
  }
  if (status === 'active') {
    return (
      <div className="flex h-6 w-6 items-center justify-center rounded-full bg-ova-blue animate-pulse">
        <div className="h-2 w-2 rounded-full bg-white" />
      </div>
    );
  }
  return (
    <div className="h-6 w-6 rounded-full bg-ova-300" />
  );
}

function ExpandableDetails({ details }: { details: StepDetail[] }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="mt-2">
      <button
        onClick={() => setExpanded(!expanded)}
        className="inline-flex items-center gap-1 text-caption text-ova-blue cursor-pointer hover:underline"
      >
        <ChevronRight size={12} strokeWidth={2} className={clsx('transition-transform duration-fast', expanded && 'rotate-90')} />
        {expanded ? 'Hide settlement details' : 'Show settlement details'}
      </button>
      {expanded && (
        <div className="mt-2 space-y-1.5 pl-1">
          {details.map((detail) => (
            <div key={detail.label} className="flex items-center gap-2">
              <span className="text-caption text-ova-400">{detail.label}:</span>
              <span className="font-mono text-caption text-ova-500">
                {detail.value}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default function TransferProgress({ steps, className }: TransferProgressProps) {
  return (
    <div className={clsx('space-y-0', className)}>
      {steps.map((step, index) => {
        const isLast = index === steps.length - 1;
        return (
          <div key={step.label} className="flex gap-3">
            {/* Timeline column */}
            <div className="flex flex-col items-center">
              <StepIndicator status={step.status} />
              {!isLast && (
                <div className={clsx(
                  'w-0.5 flex-1 min-h-[24px]',
                  step.status === 'completed' ? 'bg-ova-green' : 'bg-ova-200',
                )} />
              )}
            </div>

            {/* Content column */}
            <div className={clsx('pb-6', isLast && 'pb-0')}>
              <div className="flex items-center gap-2 min-h-[24px]">
                <span className={clsx(
                  'text-body-sm font-medium',
                  step.status === 'completed' ? 'text-ova-900' :
                  step.status === 'active' ? 'text-ova-blue' : 'text-ova-400',
                )}>
                  {step.label}
                </span>
                {step.status === 'completed' && (
                  <span className="text-caption text-ova-green">Complete</span>
                )}
                {step.status === 'active' && (
                  <span className="text-caption text-ova-blue">In progress</span>
                )}
              </div>
              {step.timestamp && (
                <p className={clsx(
                  'text-caption mt-0.5',
                  step.status === 'active' ? 'text-ova-blue' : 'text-ova-400',
                )}>
                  {step.timestamp}
                </p>
              )}
              {step.details && step.details.length > 0 && step.status !== 'pending' && (
                <ExpandableDetails details={step.details} />
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
