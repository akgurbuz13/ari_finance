'use client';

import { useState, useEffect } from 'react';
import { fetchSystemConfig, updateSystemConfig, type SystemConfig } from '../../lib/api';

export default function SettingsPage() {
  const [config, setConfig] = useState<SystemConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    loadConfig();
  }, []);

  async function loadConfig() {
    try {
      const data = await fetchSystemConfig();
      setConfig(data);
    } catch {
      setError('Failed to load configuration');
    } finally {
      setLoading(false);
    }
  }

  async function handleSave() {
    if (!config) return;
    setError('');
    setSuccess('');
    setSaving(true);

    try {
      const updated = await updateSystemConfig(config);
      setConfig(updated);
      setSuccess('Configuration saved successfully');
    } catch {
      setError('Failed to save configuration');
    } finally {
      setSaving(false);
    }
  }

  function updateField<K extends keyof SystemConfig>(key: K, value: SystemConfig[K]) {
    setConfig(prev => prev ? { ...prev, [key]: value } : prev);
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-400">Loading configuration...</div>
      </div>
    );
  }

  if (!config) {
    return (
      <div className="space-y-4">
        <h1 className="text-2xl font-bold text-gray-900">Settings</h1>
        <div className="p-4 bg-red-50 border border-red-200 rounded-lg text-sm text-red-600">
          {error || 'Failed to load configuration'}
        </div>
        <button onClick={loadConfig} className="px-4 py-2 bg-black text-white text-sm rounded-lg hover:bg-gray-800">
          Retry
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Settings</h1>
        <p className="text-gray-500 mt-1">System configuration</p>
      </div>

      {success && (
        <div className="p-3 bg-green-50 border border-green-200 rounded-lg text-sm text-green-700">
          {success}
        </div>
      )}
      {error && (
        <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-600">
          {error}
        </div>
      )}

      {/* Feature Toggles */}
      <div className="bg-white rounded-xl border border-gray-200 p-6 space-y-4">
        <h2 className="text-lg font-semibold text-gray-900">Feature Flags</h2>
        <div className="space-y-3">
          <ToggleRow
            label="Maintenance Mode"
            description="Disable all user access to the platform"
            checked={config.maintenanceMode}
            onChange={(v) => updateField('maintenanceMode', v)}
          />
          <ToggleRow
            label="User Registration"
            description="Allow new users to sign up"
            checked={config.registrationEnabled}
            onChange={(v) => updateField('registrationEnabled', v)}
          />
          <ToggleRow
            label="Cross-Border Transfers"
            description="Enable cross-border payment flows"
            checked={config.crossBorderEnabled}
            onChange={(v) => updateField('crossBorderEnabled', v)}
          />
          <ToggleRow
            label="KYC Auto-Approve"
            description="Automatically approve KYC verifications"
            checked={config.kycAutoApproveEnabled}
            onChange={(v) => updateField('kycAutoApproveEnabled', v)}
          />
          <ToggleRow
            label="Sanctions Screening"
            description="Run sanctions checks on transactions"
            checked={config.sanctionsScreeningEnabled}
            onChange={(v) => updateField('sanctionsScreeningEnabled', v)}
          />
        </div>
      </div>

      {/* Transaction Limits */}
      <div className="bg-white rounded-xl border border-gray-200 p-6 space-y-6">
        <h2 className="text-lg font-semibold text-gray-900">Transaction Limits</h2>
        <div className="grid grid-cols-2 gap-4">
          <NumberField
            label="Daily Limit (TRY)"
            value={config.maxDailyTransferTRY}
            onChange={(v) => updateField('maxDailyTransferTRY', v)}
          />
          <NumberField
            label="Daily Limit (EUR)"
            value={config.maxDailyTransferEUR}
            onChange={(v) => updateField('maxDailyTransferEUR', v)}
          />
          <NumberField
            label="Single Tx Alert (TRY)"
            value={config.maxSingleTransferTRY}
            onChange={(v) => updateField('maxSingleTransferTRY', v)}
          />
          <NumberField
            label="Single Tx Alert (EUR)"
            value={config.maxSingleTransferEUR}
            onChange={(v) => updateField('maxSingleTransferEUR', v)}
          />
        </div>
      </div>

      {/* Save */}
      <div className="flex justify-end">
        <button
          onClick={handleSave}
          disabled={saving}
          className="px-6 py-2.5 bg-black text-white text-sm font-medium rounded-lg hover:bg-gray-800 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          {saving ? 'Saving...' : 'Save Changes'}
        </button>
      </div>
    </div>
  );
}

function ToggleRow({
  label,
  description,
  checked,
  onChange,
}: {
  label: string;
  description: string;
  checked: boolean;
  onChange: (value: boolean) => void;
}) {
  return (
    <div className="flex items-center justify-between py-2">
      <div>
        <p className="text-sm font-medium text-gray-900">{label}</p>
        <p className="text-xs text-gray-500">{description}</p>
      </div>
      <button
        type="button"
        onClick={() => onChange(!checked)}
        className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
          checked ? 'bg-black' : 'bg-gray-300'
        }`}
      >
        <span
          className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
            checked ? 'translate-x-6' : 'translate-x-1'
          }`}
        />
      </button>
    </div>
  );
}

function NumberField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: number;
  onChange: (value: number) => void;
}) {
  return (
    <div>
      <label className="block text-sm text-gray-600 mb-1">{label}</label>
      <input
        type="number"
        value={value}
        onChange={(e) => onChange(parseFloat(e.target.value) || 0)}
        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-black/20 focus:border-gray-400"
      />
    </div>
  );
}
