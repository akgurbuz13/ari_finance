'use client';

import { useState, useEffect, useCallback } from 'react';
import DataTable, { Column, RowAction } from '../../components/DataTable';
import Modal from '../../components/Modal';
import {
  fetchUsers,
  suspendUser,
  reactivateUser,
  freezeAccount,
  unfreezeAccount,
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
  const [freezeModal, setFreezeModal] = useState<{ userId: string; accountId: string; currency: string } | null>(null);
  const [freezeReason, setFreezeReason] = useState('');
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

  const handleFreeze = async () => {
    if (!freezeModal || !freezeReason) return;
    try {
      setActionLoading(true);
      await freezeAccount(freezeModal.userId, freezeModal.accountId, freezeReason);
      setFreezeModal(null);
      setFreezeReason('');
      await loadData();
    } catch (err) {
      console.error('Error freezing account:', err);
      alert('Failed to freeze account');
    } finally {
      setActionLoading(false);
    }
  };

  const handleUnfreeze = async (userId: string, accountId: string) => {
    if (!confirm('Unfreeze this account?')) return;
    try {
      setActionLoading(true);
      await unfreezeAccount(userId, accountId);
      await loadData();
    } catch (err) {
      console.error('Error unfreezing account:', err);
      alert('Failed to unfreeze account');
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
          <div className="flex flex-col gap-1">
            {row.accounts.map((acc) => (
              <div key={acc.id} className="flex items-center gap-2">
                <span className="text-xs text-gray-600">
                  {acc.currency}: {Number(acc.balance).toLocaleString(undefined, { minimumFractionDigits: 2 })}
                </span>
                {acc.status === 'frozen' ? (
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      handleUnfreeze(row.id, acc.id);
                    }}
                    disabled={actionLoading}
                    className="text-[10px] px-1.5 py-0.5 bg-red-100 text-red-700 rounded hover:bg-red-200 font-medium"
                    title="Unfreeze this account"
                  >
                    Frozen - Unfreeze
                  </button>
                ) : (
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setFreezeModal({ userId: row.id, accountId: acc.id, currency: acc.currency });
                    }}
                    disabled={actionLoading}
                    className="text-[10px] px-1.5 py-0.5 text-gray-400 rounded hover:bg-blue-50 hover:text-blue-600 font-medium opacity-0 group-hover:opacity-100 transition-opacity"
                    title="Freeze this account"
                  >
                    Freeze
                  </button>
                )}
              </div>
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

      <Modal
        open={!!freezeModal}
        onClose={() => {
          setFreezeModal(null);
          setFreezeReason('');
        }}
        title="Freeze Account"
        description={`Freeze the ${freezeModal?.currency || ''} account. The user will not be able to transact with this account until it is unfrozen.`}
        footer={
          <>
            <button
              onClick={() => {
                setFreezeModal(null);
                setFreezeReason('');
              }}
              className="px-4 py-2 text-sm text-gray-600 hover:text-gray-900"
            >
              Cancel
            </button>
            <button
              onClick={handleFreeze}
              disabled={!freezeReason || actionLoading}
              className="px-4 py-2 text-sm bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50"
            >
              {actionLoading ? 'Freezing...' : 'Freeze Account'}
            </button>
          </>
        }
      >
        <textarea
          value={freezeReason}
          onChange={(e) => setFreezeReason(e.target.value)}
          placeholder="Reason for freezing this account..."
          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          rows={3}
        />
      </Modal>
    </div>
  );
}
