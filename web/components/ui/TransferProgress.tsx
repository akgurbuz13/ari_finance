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
      <div className="flex h-7 w-7 items-center justify-center rounded-full bg-ari-green text-white shrink-0">
        <Check size={14} strokeWidth={3} />
      </div>
    );
  }
  if (status === 'active') {
    return (
      <div className="relative shrink-0">
        <div className="absolute -inset-1 rounded-full bg-ari-900/10 animate-pulse" />
        <div className="relative flex h-7 w-7 items-center justify-center rounded-full bg-ari-navy">
          <div className="h-2 w-2 rounded-full bg-white animate-pulse" />
        </div>
      </div>
    );
  }
  return (
    <div className="flex h-7 w-7 items-center justify-center rounded-full border-2 border-ari-200 bg-white shrink-0">
      <div className="h-2 w-2 rounded-full bg-ari-200" />
    </div>
  );
}

function ExpandableDetails({ details }: { details: StepDetail[] }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="mt-2">
      <button
        onClick={() => setExpanded(!expanded)}
        className="inline-flex items-center gap-1 text-caption text-ari-700 cursor-pointer hover:text-ari-900 transition-colors"
      >
        <ChevronRight size={12} strokeWidth={2} className={clsx('transition-transform duration-fast', expanded && 'rotate-90')} />
        {expanded ? 'Hide details' : 'View details'}
      </button>
      {expanded && (
        <div className="mt-2 space-y-1.5 pl-1 bg-ari-50 rounded-lg p-3">
          {details.map((detail) => (
            <div key={detail.label} className="flex items-center justify-between gap-2">
              <span className="text-caption text-ari-500">{detail.label}</span>
              <span className="font-mono text-caption text-ari-700">
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
          <div key={step.label} className="flex gap-4">
            {/* Timeline column */}
            <div className="flex flex-col items-center">
              <StepIndicator status={step.status} />
              {!isLast && (
                <div className={clsx(
                  'w-px flex-1 min-h-[32px] transition-colors duration-300',
                  step.status === 'completed' ? 'bg-ari-green' : 'bg-ari-200',
                )} />
              )}
            </div>

            {/* Content column */}
            <div className={clsx('pb-7 pt-0.5', isLast && 'pb-0')}>
              <div className="flex items-center gap-2.5">
                <span className={clsx(
                  'text-body-sm font-medium leading-none',
                  step.status === 'completed' ? 'text-ari-900' :
                  step.status === 'active' ? 'text-ari-900' : 'text-ari-400',
                )}>
                  {step.label}
                </span>
                {step.status === 'completed' && (
                  <span className="text-micro font-medium text-ari-green bg-ari-green/10 px-1.5 py-0.5 rounded-full">Done</span>
                )}
                {step.status === 'active' && (
                  <span className="text-micro font-medium text-ari-navy bg-ari-navy/10 px-1.5 py-0.5 rounded-full">Processing</span>
                )}
              </div>
              {step.timestamp && (
                <p className={clsx(
                  'text-caption mt-1 font-mono',
                  step.status === 'active' ? 'text-ari-500' : 'text-ari-400',
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
