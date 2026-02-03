'use client';

import { useState, useEffect, useCallback } from 'react';
import DataTable, { Column, RowAction } from '../../components/DataTable';
import Modal from '../../components/Modal';
import {
  fetchUsers,
  suspendUser,
  reactivateUser,
  AdminUser,
  UserStatus,
} from '../../lib/api';

export default function UsersPage() {
  const [data, setData] = useState<AdminUser[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(10);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<UserStatus | ''>('');
  const [regionFilter, setRegionFilter] = useState('');
  const [search, setSearch] = useState('');
  const [sortBy, setSortBy] = useState('created_at');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');

  const [suspendModal, setSuspendModal] = useState<string | null>(null);
  const [suspendReason, setSuspendReason] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await fetchUsers({
        page,
        pageSize,
        status: statusFilter || undefined,
        region: regionFilter || undefined,
        search: search || undefined,
        sortBy,
        sortOrder,
      });
      setData(response.items);
      setTotal(response.total);
    } catch (err) {
      setError('Failed to load users');
      console.error('Error loading users:', err);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, statusFilter, regionFilter, search, sortBy, sortOrder]);

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

  const handleSuspend = async () => {
    if (!suspendModal || !suspendReason) return;
    try {
      setActionLoading(true);
      await suspendUser(suspendModal, suspendReason);
      setSuspendModal(null);
      setSuspendReason('');
      await loadData();
    } catch (err) {
      console.error('Error suspending user:', err);
      alert('Failed to suspend user');
    } finally {
      setActionLoading(false);
    }
  };

  const handleReactivate = async (userId: string) => {
    if (!confirm('Reactivate this user?')) return;
    try {
      setActionLoading(true);
      await reactivateUser(userId);
      await loadData();
    } catch (err) {
      console.error('Error reactivating user:', err);
      alert('Failed to reactivate user');
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
      active: 'bg-green-100 text-green-700',
      suspended: 'bg-red-100 text-red-700',
      pending_kyc: 'bg-yellow-100 text-yellow-700',
      pending: 'bg-yellow-100 text-yellow-700',
      closed: 'bg-gray-100 text-gray-700',
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

  const kycBadge = (kycStatus: string) => {
    const styles: Record<string, string> = {
      approved: 'bg-green-100 text-green-700',
      pending: 'bg-yellow-100 text-yellow-700',
      rejected: 'bg-red-100 text-red-700',
      expired: 'bg-gray-100 text-gray-700',
    };
    return (
      <span
        className={`text-xs px-2.5 py-0.5 rounded-full font-medium ${
          styles[kycStatus] || styles.pending
        }`}
      >
        {kycStatus}
      </span>
    );
  };

  const columns: Column<AdminUser>[] = [
    { key: 'name', header: 'Name', sortable: false },
    { key: 'email', header: 'Email', sortable: true },
    { key: 'phone', header: 'Phone', sortable: false },
    {
      key: 'status',
      header: 'Status',
      sortable: true,
      render: (row) => statusBadge(row.status),
    },
    {
      key: 'kycStatus',
      header: 'KYC',
      sortable: false,
      render: (row) => kycBadge(row.kycStatus),
    },
    {
      key: 'region',
      header: 'Region',
      sortable: true,
      render: (row) => (
        <span className="text-xs px-2 py-0.5 bg-gray-100 text-gray-700 rounded font-medium">
          {row.region}
        </span>
      ),
    },
    {
      key: 'accounts',
      header: 'Accounts',
      sortable: false,
      render: (row) =>
        row.accounts.length > 0 ? (
          <div className="flex flex-col gap-0.5">
            {row.accounts.map((acc) => (
              <span key={acc.id} className="text-xs text-gray-600">
                {acc.currency}: {Number(acc.balance).toLocaleString(undefined, { minimumFractionDigits: 2 })}
                {acc.status === 'frozen' && (
                  <span className="text-red-500 ml-1">(frozen)</span>
                )}
              </span>
            ))}
          </div>
        ) : (
          <span className="text-xs text-gray-400">No accounts</span>
        ),
    },
    {
      key: 'createdAt',
      header: 'Joined',
      sortable: true,
      render: (row) => (
        <span className="text-sm text-gray-600">
          {new Date(row.createdAt).toLocaleDateString()}
        </span>
      ),
    },
  ];

  const actions: RowAction<AdminUser>[] = [
    {
      label: 'Suspend',
      onClick: (row) => setSuspendModal(row.id),
      variant: 'warning',
      hidden: (row) => row.status === 'suspended' || row.status === 'closed',
    },
    {
      label: 'Reactivate',
      onClick: (row) => handleReactivate(row.id),
      variant: 'success',
      hidden: (row) => row.status !== 'suspended',
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Users</h1>
          <p className="text-gray-500 mt-1">Manage platform users</p>
        </div>
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
          placeholder="Search by email or phone..."
          className="px-4 py-2 border border-gray-300 rounded-lg text-sm w-72 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <select
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(e.target.value as UserStatus | '');
            setPage(1);
          }}
          className="px-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">All Statuses</option>
          <option value="active">Active</option>
          <option value="suspended">Suspended</option>
          <option value="pending">Pending</option>
          <option value="closed">Closed</option>
        </select>
        <select
          value={regionFilter}
          onChange={(e) => {
            setRegionFilter(e.target.value);
            setPage(1);
          }}
          className="px-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">All Regions</option>
          <option value="TR">Turkey (TR)</option>
          <option value="EU">Europe (EU)</option>
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
        emptyMessage="No users found."
      />

      <Modal
        open={!!suspendModal}
        onClose={() => {
          setSuspendModal(null);
          setSuspendReason('');
        }}
        title="Suspend User"
        description="Provide a reason for suspending this user account."
        footer={
          <>
            <button
              onClick={() => {
                setSuspendModal(null);
                setSuspendReason('');
              }}
              className="px-4 py-2 text-sm text-gray-600 hover:text-gray-900"
            >
              Cancel
            </button>
            <button
              onClick={handleSuspend}
              disabled={!suspendReason || actionLoading}
              className="px-4 py-2 text-sm bg-yellow-600 text-white rounded-lg hover:bg-yellow-700 disabled:opacity-50"
            >
              {actionLoading ? 'Suspending...' : 'Suspend User'}
            </button>
          </>
        }
      >
        <textarea
          value={suspendReason}
          onChange={(e) => setSuspendReason(e.target.value)}
          placeholder="Suspension reason..."
          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          rows={3}
        />
      </Modal>
    </div>
  );
}
