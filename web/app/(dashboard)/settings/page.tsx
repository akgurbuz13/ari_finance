'use client';

import { useState } from 'react';
import { useAuth } from '../../../lib/hooks/useAuth';
import api from '../../../lib/api/client';
import Input from '../../../components/ui/Input';
import Button from '../../../components/ui/Button';
import Card from '../../../components/ui/Card';

export default function SettingsPage() {
  const { user, refetch } = useAuth();
  const [firstName, setFirstName] = useState(user?.firstName || '');
  const [lastName, setLastName] = useState(user?.lastName || '');
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setMessage('');
    try {
      await api.patch('/users/me', { firstName, lastName });
      await refetch();
      setMessage('Profile updated');
    } catch {
      setMessage('Failed to update profile');
    } finally {
      setSaving(false);
    }
  };

  const setup2FA = async () => {
    try {
      const { data } = await api.post<{ secret: string; uri: string }>('/auth/2fa/setup');
      alert(`Scan this URI in your authenticator app:\n\n${data.uri}\n\nSecret: ${data.secret}`);
    } catch {
      alert('Failed to set up 2FA');
    }
  };

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-black">Settings</h1>

      <Card header="Profile">
        <form onSubmit={handleSave} className="space-y-4">
          {message && (
            <div className="p-3 bg-gray-50 border border-gray-200 rounded-lg text-sm">{message}</div>
          )}
          <div className="grid grid-cols-2 gap-4">
            <Input label="First Name" value={firstName} onChange={(e) => setFirstName(e.target.value)} />
            <Input label="Last Name" value={lastName} onChange={(e) => setLastName(e.target.value)} />
          </div>
          <Input label="Email" value={user?.email || ''} disabled />
          <Input label="Phone" value={user?.phone || ''} disabled />
          <Input label="Region" value={user?.region || ''} disabled />
          <Button type="submit" disabled={saving}>
            {saving ? 'Saving...' : 'Save Changes'}
          </Button>
        </form>
      </Card>

      <Card header="Security">
        <div className="space-y-4">
          <div className="flex justify-between items-center">
            <div>
              <p className="text-sm font-medium text-black">Two-Factor Authentication</p>
              <p className="text-xs text-gray-500">
                {user?.totpEnabled ? 'Enabled' : 'Not enabled'}
              </p>
            </div>
            {!user?.totpEnabled && (
              <Button variant="secondary" onClick={setup2FA}>Set up 2FA</Button>
            )}
          </div>
        </div>
      </Card>
    </div>
  );
}
