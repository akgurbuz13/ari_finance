'use client';

import { useState } from 'react';
import DataTable from '../../components/DataTable';
import Modal from '../../components/Modal';

interface KycRow {
  id: string;
  userId: string;
  userEmail: string;
  provider: string;
  level: string;
  status: string;
  createdAt: string;
}

const columns = [
  { key: 'userEmail', label: 'User' },
  { key: 'provider', label: 'Provider' },
  { key: 'level', label: 'Level' },
  { key: 'status', label: 'Status' },
  { key: 'createdAt', label: 'Submitted' },
];

export default function KycReviewPage() {
  const [rejectModal, setRejectModal] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  // Placeholder data - replace with API calls
  const data: KycRow[] = [];

  const handleApprove = async (id: string) => {
    if (!confirm('Approve this KYC verification?')) return;
    // await adminApi.post(`/kyc/${id}/approve`);
    alert(`Approved: ${id}`);
  };

  const handleReject = async () => {
    if (!rejectModal || !rejectReason) return;
    // await adminApi.post(`/kyc/${rejectModal}/reject`, { reason: rejectReason });
    alert(`Rejected: ${rejectModal} - ${rejectReason}`);
    setRejectModal(null);
    setRejectReason('');
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">KYC Review</h1>
        <p className="text-gray-500 mt-1">Review pending identity verifications</p>
      </div>

      <DataTable
        columns={columns}
        data={data}
        actions={(row: KycRow) => (
          <div className="flex gap-2">
            <button
              onClick={() => handleApprove(row.id)}
              className="text-xs px-3 py-1 bg-green-100 text-green-700 rounded-full hover:bg-green-200"
            >
              Approve
            </button>
            <button
              onClick={() => setRejectModal(row.id)}
              className="text-xs px-3 py-1 bg-red-100 text-red-700 rounded-full hover:bg-red-200"
            >
              Reject
            </button>
          </div>
        )}
      />

      <Modal
        isOpen={!!rejectModal}
        onClose={() => setRejectModal(null)}
        title="Reject KYC"
      >
        <div className="space-y-4">
          <textarea
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
            placeholder="Rejection reason..."
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            rows={3}
          />
          <div className="flex justify-end gap-2">
            <button onClick={() => setRejectModal(null)} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-900">
              Cancel
            </button>
            <button
              onClick={handleReject}
              disabled={!rejectReason}
              className="px-4 py-2 text-sm bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50"
            >
              Reject
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
