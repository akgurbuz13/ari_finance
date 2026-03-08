'use client';

import { useState } from 'react';
import { User, Shield, SlidersHorizontal, Copy, Check, ShieldCheck, X } from 'lucide-react';
import { clsx } from 'clsx';
import { useAuth } from '../../../lib/hooks/useAuth';
import api from '../../../lib/api/client';
import Input from '../../../components/ui/Input';
import Button from '../../../components/ui/Button';
import Card from '../../../components/ui/Card';
import StatusPill from '../../../components/ui/StatusPill';
import PasswordStrength from '../../../components/ui/PasswordStrength';

type SettingsTab = 'profile' | 'security' | 'preferences';

const tabs: { id: SettingsTab; label: string; icon: typeof User }[] = [
  { id: 'profile', label: 'Profile', icon: User },
  { id: 'security', label: 'Security', icon: Shield },
  { id: 'preferences', label: 'Preferences', icon: SlidersHorizontal },
];

function CopyableSecret({ value, label }: { value: string; label: string }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard API may not be available
    }
  };

  return (
    <div className="flex items-center gap-2">
      <span className="text-caption text-ari-400">{label}:</span>
      <code className="font-mono text-body-sm text-ari-700 bg-ari-100 px-2 py-1 rounded-xl select-all break-all">
        {value}
      </code>
      <button
        onClick={handleCopy}
        className="inline-flex items-center gap-1 text-caption text-ari-blue hover:underline cursor-pointer"
      >
        {copied ? <Check size={12} strokeWidth={2} /> : <Copy size={12} strokeWidth={2} />}
        {copied ? 'Copied' : 'Copy'}
      </button>
    </div>
  );
}

function ProfileTab() {
  const { user, refetch } = useAuth();
  const [firstName, setFirstName] = useState(user?.firstName || '');
  const [lastName, setLastName] = useState(user?.lastName || '');
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setMessage(null);
    try {
      await api.patch('/users/me', { firstName, lastName });
      await refetch();
      setMessage({ type: 'success', text: 'Profile updated successfully' });
    } catch {
      setMessage({ type: 'error', text: 'Failed to update profile. Please try again.' });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6">
      <Card>
        <form onSubmit={handleSave} className="space-y-4">
          {message && (
            <div role="alert" className={clsx(
              'p-3 rounded-xl text-body-sm border',
              message.type === 'success'
                ? 'bg-ari-green-light border-ari-green/20 text-ari-green'
                : 'bg-ari-red-light border-ari-red/20 text-ari-red',
            )}>
              {message.text}
            </div>
          )}
          <div className="grid grid-cols-2 gap-4">
            <Input label="First Name" value={firstName} onChange={(e) => setFirstName(e.target.value)} />
            <Input label="Last Name" value={lastName} onChange={(e) => setLastName(e.target.value)} />
          </div>
          <Input label="Email" value={user?.email || ''} disabled />
          <Input label="Phone" value={user?.phone || ''} disabled />
          <div className="grid grid-cols-2 gap-4">
            <Input label="Region" value={user?.region || ''} disabled />
            <div className="w-full">
              <label className="block text-body-sm font-medium text-ari-700 mb-3">KYC Status</label>
              <div className="h-12 flex items-center">
                <StatusPill variant={user?.status === 'active' ? 'success' : 'warning'}>
                  {user?.status === 'active' ? 'Verified' : 'Pending Verification'}
                </StatusPill>
              </div>
            </div>
          </div>
          <Button type="submit" disabled={saving}>
            {saving ? 'Saving...' : 'Save Changes'}
          </Button>
        </form>
      </Card>
    </div>
  );
}

function SecurityTab() {
  const { user } = useAuth();
  const [setting2fa, setSetting2fa] = useState(false);
  const [totpData, setTotpData] = useState<{ secret: string; uri: string } | null>(null);
  const [error2fa, setError2fa] = useState('');
  const [changingPassword, setChangingPassword] = useState(false);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [passwordMessage, setPasswordMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const setup2FA = async () => {
    setSetting2fa(true);
    setError2fa('');
    try {
      const { data } = await api.post<{ secret: string; uri: string }>('/auth/2fa/setup');
      setTotpData(data);
    } catch {
      setError2fa('Failed to set up 2FA. Please try again.');
    } finally {
      setSetting2fa(false);
    }
  };

  const dismiss2FA = () => {
    setTotpData(null);
  };

  const handlePasswordChange = async (e: React.FormEvent) => {
    e.preventDefault();
    setPasswordMessage(null);
    try {
      await api.post('/auth/change-password', { currentPassword, newPassword });
      setPasswordMessage({ type: 'success', text: 'Password updated successfully' });
      setChangingPassword(false);
      setCurrentPassword('');
      setNewPassword('');
    } catch {
      setPasswordMessage({ type: 'error', text: 'Failed to update password. Please check your current password.' });
    }
  };

  return (
    <div className="space-y-6">
      {/* 2FA Section */}
      <Card>
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-ari-100">
                <ShieldCheck size={20} strokeWidth={1.5} className="text-ari-500" />
              </div>
              <div>
                <p className="text-body-sm font-medium text-ari-900">Two-Factor Authentication</p>
                <p className="text-caption text-ari-400">
                  {user?.totpEnabled ? 'Your account is protected with 2FA' : 'Add an extra layer of security'}
                </p>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <StatusPill variant={user?.totpEnabled ? 'success' : 'warning'}>
                {user?.totpEnabled ? 'Enabled' : 'Disabled'}
              </StatusPill>
              {!user?.totpEnabled && !totpData && (
                <Button variant="secondary" onClick={setup2FA} disabled={setting2fa}>
                  {setting2fa ? 'Setting up...' : 'Enable 2FA'}
                </Button>
              )}
            </div>
          </div>

          {error2fa && (
            <div role="alert" className="p-3 bg-ari-red-light border border-ari-red/20 rounded-xl text-body-sm text-ari-red">
              {error2fa}
            </div>
          )}

          {totpData && (
            <div className="mt-4 p-4 bg-ari-50 border border-ari-200 rounded-xl space-y-4">
              <div className="flex items-center justify-between">
                <h4 className="text-body-sm font-medium text-ari-900">Set up your authenticator app</h4>
                <button
                  onClick={dismiss2FA}
                  className="flex h-6 w-6 items-center justify-center rounded-full hover:bg-ari-200 transition-colors duration-fast cursor-pointer"
                >
                  <X size={14} strokeWidth={2} className="text-ari-400" />
                </button>
              </div>
              <ol className="list-decimal list-inside space-y-2 text-body-sm text-ari-700">
                <li>Open your authenticator app (Google Authenticator, Authy, etc.)</li>
                <li>Copy the secret key below and add it as a new account</li>
                <li>Enter the 6-digit code from the app on your next login</li>
              </ol>
              <div className="space-y-3">
                <CopyableSecret label="URI" value={totpData.uri} />
                <CopyableSecret label="Secret" value={totpData.secret} />
              </div>
            </div>
          )}
        </div>
      </Card>

      {/* Password Section */}
      <Card>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-full bg-ari-100">
              <Shield size={20} strokeWidth={1.5} className="text-ari-500" />
            </div>
            <div>
              <p className="text-body-sm font-medium text-ari-900">Password</p>
              <p className="text-caption text-ari-400">Change your account password</p>
            </div>
          </div>
          {!changingPassword && (
            <Button variant="secondary" onClick={() => setChangingPassword(true)}>Change Password</Button>
          )}
        </div>
        {passwordMessage && (
          <div className={clsx(
            'mt-4 p-3 rounded-xl text-body-sm border',
            passwordMessage.type === 'success'
              ? 'bg-ari-green-light border-ari-green/20 text-ari-green'
              : 'bg-ari-red-light border-ari-red/20 text-ari-red',
          )}>
            {passwordMessage.text}
          </div>
        )}
        {changingPassword && (
          <form onSubmit={handlePasswordChange} className="mt-4 space-y-4">
            <Input label="Current Password" type="password" value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} required />
            <Input label="New Password" type="password" value={newPassword} onChange={e => setNewPassword(e.target.value)} required />
            {newPassword && <PasswordStrength password={newPassword} />}
            <div className="flex gap-2">
              <Button type="submit">Update Password</Button>
              <Button variant="ghost" type="button" onClick={() => { setChangingPassword(false); setCurrentPassword(''); setNewPassword(''); }}>Cancel</Button>
            </div>
          </form>
        )}
      </Card>
    </div>
  );
}

function PreferencesTab() {
  return (
    <div className="space-y-6">
      <Card>
        <div className="space-y-6">
          {/* Language */}
          <div>
            <label className="block text-body-sm font-medium text-ari-700 mb-3">Language</label>
            <select
              defaultValue="en"
              className="h-11 w-full px-4 bg-ari-50 border border-ari-200 rounded-xl text-body-sm text-ari-900 focus:outline-none focus:bg-white focus:border-ari-900 focus:ring-1 focus:ring-ari-900/10 transition-all duration-base appearance-none cursor-pointer"
            >
              <option value="en">English</option>
              <option value="tr">Turkish</option>
            </select>
          </div>

          {/* Currency Display */}
          <div>
            <label className="block text-body-sm font-medium text-ari-700 mb-3">Currency Display</label>
            <select
              defaultValue="symbol"
              className="h-11 w-full px-4 bg-ari-50 border border-ari-200 rounded-xl text-body-sm text-ari-900 focus:outline-none focus:bg-white focus:border-ari-900 focus:ring-1 focus:ring-ari-900/10 transition-all duration-base appearance-none cursor-pointer"
            >
              <option value="symbol">Symbol (₺, €)</option>
              <option value="code">Code (TRY, EUR)</option>
            </select>
          </div>
        </div>
      </Card>

      <p className="text-caption text-ari-400 text-center">
        Preference changes will apply on next page load.
      </p>
    </div>
  );
}

export default function SettingsPage() {
  const [activeTab, setActiveTab] = useState<SettingsTab>('profile');

  return (
    <div className="max-w-form mx-auto space-y-6">
      <h1 className="text-h2 font-display text-ari-900">Settings</h1>

      {/* Tabs */}
      <div className="inline-flex bg-ari-100 rounded-xl p-1 gap-1">
        {tabs.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            onClick={() => setActiveTab(id)}
            className={clsx(
              'flex items-center gap-2 px-4 py-2.5 rounded-lg text-body-sm font-medium transition-all duration-fast cursor-pointer',
              activeTab === id
                ? 'bg-white text-ari-900 shadow-sm'
                : 'text-ari-500 hover:text-ari-700',
            )}
          >
            <Icon size={16} strokeWidth={1.5} />
            {label}
          </button>
        ))}
      </div>

      {/* Tab content */}
      {activeTab === 'profile' && <ProfileTab />}
      {activeTab === 'security' && <SecurityTab />}
      {activeTab === 'preferences' && <PreferencesTab />}
    </div>
  );
}
