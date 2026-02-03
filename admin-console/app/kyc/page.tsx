'use client';

import { useState, useEffect, useCallback } from 'react';
import DataTable, { Column, RowAction } from '../../components/DataTable';
import Modal from '../../components/Modal';
import {
  fetchKycVerifications,
  approveKyc,
  rejectKyc,
  KycVerification,
  KycStatus,
} from '../../lib/api';

export default function KycReviewPage() {
  const [data, setData] = useState<KycVerification[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(10);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<KycStatus | ''>('');
  const [search, setSearch] = useState('');
  const [sortBy, setSortBy] = useState('created_at');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');

  const [rejectModal, setRejectModal] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await fetchKycVerifications({
        page,
        pageSize,
        status: statusFilter || undefined,
        search: search || undefined,
        sortBy,
        sortOrder,
      });
      setData(response.items);
      setTotal(response.total);
    } catch (err) {
      setError('Failed to load KYC verifications');
      console.error('Error loading KYC data:', err);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, statusFilter, search, sortBy, sortOrder]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Debounce search
  const [searchInput, setSearchInput] = useState('');
  useEffect(() => {
    const timer = setTimeout(() => {
      setSearch(searchInput);
      setPage(1);
    }, 400);
    return () => clearTimeout(timer);
  }, [searchInput]);

  const handleApprove = async (id: string) => {
    if (!confirm('Approve this KYC verification?')) return;
    try {
      setActionLoading(true);
      await approveKyc(id);
      await loadData();
    } catch (err) {
      console.error('Error approving KYC:', err);
      alert('Failed to approve KYC verification');
    } finally {
      setActionLoading(false);
    }
  };

  const handleReject = async () => {
    if (!rejectModal || !rejectReason) return;
    try {
      setActionLoading(true);
      await rejectKyc(rejectModal, rejectReason);
      setRejectModal(null);
      setRejectReason('');
      await loadData();
    } catch (err) {
      console.error('Error rejecting KYC:', err);
      alert('Failed to reject KYC verification');
    } finally {
      setActionLoading(false);
    }
  };

  const handleSort = (key: string, order: 'asc' | 'desc') => {
    setSortBy(key);
    setSortOrder(order);
  };

  const statusBadge = (status: string) => {
    const styles: Record<string, string> = {
      pending: 'bg-yellow-100 text-yellow-700',
      approved: 'bg-green-100 text-green-700',
      rejected: 'bg-red-100 text-red-700',
      in_review: 'bg-blue-100 text-blue-700',
      expired: 'bg-gray-100 text-gray-700',
    };
    return (
      <span
        className={`text-xs px-2.5 py-0.5 rounded-full font-medium ${
          styles[status] || styles.pending
        }`}
      >
        {status.replace('_', ' ')}
      </span>
    );
  };

  const columns: Column<KycVerification>[] = [
    { key: 'userName', header: 'User', sortable: true },
    { key: 'email', header: 'Email', sortable: true },
    { key: 'provider', header: 'Provider', sortable: true },
    { key: 'region', header: 'Region', sortable: false },
    {
      key: 'status',
      header: 'Status',
      sortable: true,
      render: (row) => statusBadge(row.status),
    },
    {
      key: 'submittedAt',
      header: 'Submitted',
      sortable: true,
      render: (row) => (
        <span className="text-sm text-gray-600">
          {new Date(row.submittedAt).toLocaleDateString()}
        </span>
      ),
    },
  ];

  const actions: RowAction<KycVerification>[] = [
    {
      label: 'Approve',
      onClick: (row) => handleApprove(row.id),
      variant: 'success',
      hidden: (row) => row.status !== 'pending',
    },
    {
      label: 'Reject',
      onClick: (row) => setRejectModal(row.id),
      variant: 'danger',
      hidden: (row) => row.status !== 'pending',
    },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">KYC Review</h1>
        <p className="text-gray-500 mt-1">
          Review pending identity verifications
        </p>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 flex items-center justify-between">
          <p className="text-sm text-red-700">{error}</p>
          <button
            onClick={loadData}
            className="text-sm text-red-700 underline hover:text-red-900"
          >
            Retry
          </button>
        </div>
      )}

      {/* Filters */}
      <div className="flex flex-wrap gap-4 items-center">
        <input
          type="text"
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          placeholder="Search by email or name..."
          className="px-4 py-2 border border-gray-300 rounded-lg text-sm w-72 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <select
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(e.target.value as KycStatus | '');
            setPage(1);
          }}
          className="px-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">All Statuses</option>
          <option value="pending">Pending</option>
          <option value="approved">Approved</option>
          <option value="rejected">Rejected</option>
          <option value="expired">Expired</option>
        </select>
      </div>

      <DataTable
        columns={columns}
        data={data}
        rowKey={(row) => row.id}
        actions={actions}
        page={page}
        pageSize={pageSize}
        total={total}
        onPageChange={setPage}
        sortBy={sortBy}
        sortOrder={sortOrder}
        onSort={handleSort}
        loading={loading || actionLoading}
        emptyMessage="No KYC verifications found."
      />

      <Modal
        open={!!rejectModal}
        onClose={() => {
          setRejectModal(null);
          setRejectReason('');
        }}
        title="Reject KYC Verification"
        description="Please provide a reason for rejecting this verification."
        footer={
          <>
            <button
              onClick={() => {
                setRejectModal(null);
                setRejectReason('');
              }}
              className="px-4 py-2 text-sm text-gray-600 hover:text-gray-900"
            >
              Cancel
            </button>
            <button
              onClick={handleReject}
              disabled={!rejectReason || actionLoading}
              className="px-4 py-2 text-sm bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50"
            >
              {actionLoading ? 'Rejecting...' : 'Reject'}
            </button>
          </>
        }
      >
        <textarea
          value={rejectReason}
          onChange={(e) => setRejectReason(e.target.value)}
          placeholder="Rejection reason..."
          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          rows={3}
        />
      </Modal>
    </div>
  );
}
