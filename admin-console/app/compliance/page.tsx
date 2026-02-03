'use client';

import { useState, useEffect, useCallback } from 'react';
import DataTable, { Column, RowAction } from '../../components/DataTable';
import Modal from '../../components/Modal';
import {
  fetchComplianceCases,
  resolveCase,
  ComplianceCase,
  CaseStatus,
  CaseType,
} from '../../lib/api';

export default function CompliancePage() {
  const [data, setData] = useState<ComplianceCase[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(10);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<CaseStatus | ''>('');
  const [typeFilter, setTypeFilter] = useState<CaseType | ''>('');
  const [search, setSearch] = useState('');
  const [sortBy, setSortBy] = useState('created_at');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');

  const [resolveModal, setResolveModal] = useState<string | null>(null);
  const [resolution, setResolution] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await fetchComplianceCases({
        page,
        pageSize,
        status: statusFilter || undefined,
        type: typeFilter || undefined,
        search: search || undefined,
        sortBy,
        sortOrder,
      });
      setData(response.items);
      setTotal(response.total);
    } catch (err) {
      setError('Failed to load compliance cases');
      console.error('Error loading compliance data:', err);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, statusFilter, typeFilter, search, sortBy, sortOrder]);

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

  const handleResolve = async () => {
    if (!resolveModal || !resolution) return;
    try {
      setActionLoading(true);
      await resolveCase(resolveModal, resolution);
      setResolveModal(null);
      setResolution('');
      await loadData();
    } catch (err) {
      console.error('Error resolving case:', err);
      alert('Failed to resolve compliance case');
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
      open: 'bg-yellow-100 text-yellow-700',
      in_review: 'bg-blue-100 text-blue-700',
      escalated: 'bg-orange-100 text-orange-700',
      resolved: 'bg-green-100 text-green-700',
      closed: 'bg-gray-100 text-gray-700',
    };
    return (
      <span
        className={`text-xs px-2.5 py-0.5 rounded-full font-medium ${
          styles[status] || styles.open
        }`}
      >
        {status.replace('_', ' ')}
      </span>
    );
  };

  const priorityBadge = (priority: string) => {
    const styles: Record<string, string> = {
      critical: 'bg-red-100 text-red-700',
      high: 'bg-orange-100 text-orange-700',
      medium: 'bg-yellow-100 text-yellow-700',
      low: 'bg-gray-100 text-gray-700',
    };
    return (
      <span
        className={`text-xs px-2.5 py-0.5 rounded-full font-medium ${
          styles[priority] || styles.medium
        }`}
      >
        {priority}
      </span>
    );
  };

  const columns: Column<ComplianceCase>[] = [
    {
      key: 'type',
      header: 'Type',
      sortable: true,
      render: (row) => (
        <span className="text-sm font-medium">
          {row.type.replace(/_/g, ' ')}
        </span>
      ),
    },
    { key: 'userName', header: 'User', sortable: false },
    {
      key: 'description',
      header: 'Description',
      sortable: false,
      render: (row) => (
        <span className="text-sm text-gray-600 truncate max-w-xs block">
          {row.description}
        </span>
      ),
    },
    {
      key: 'priority',
      header: 'Priority',
      sortable: false,
      render: (row) => priorityBadge(row.priority),
    },
    {
      key: 'status',
      header: 'Status',
      sortable: true,
      render: (row) => statusBadge(row.status),
    },
    {
      key: 'createdAt',
      header: 'Created',
      sortable: true,
      render: (row) => (
        <span className="text-sm text-gray-600">
          {new Date(row.createdAt).toLocaleDateString()}
        </span>
      ),
    },
  ];

  const actions: RowAction<ComplianceCase>[] = [
    {
      label: 'Resolve',
      onClick: (row) => setResolveModal(row.id),
      variant: 'success',
      hidden: (row) => row.status === 'resolved' || row.status === 'closed',
    },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Compliance Cases</h1>
        <p className="text-gray-500 mt-1">
          Manage compliance alerts and cases
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
          placeholder="Search cases..."
          className="px-4 py-2 border border-gray-300 rounded-lg text-sm w-72 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <select
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(e.target.value as CaseStatus | '');
            setPage(1);
          }}
          className="px-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">All Statuses</option>
          <option value="open">Open</option>
          <option value="in_review">In Review</option>
          <option value="escalated">Escalated</option>
          <option value="resolved">Resolved</option>
          <option value="closed">Closed</option>
        </select>
        <select
          value={typeFilter}
          onChange={(e) => {
            setTypeFilter(e.target.value as CaseType | '');
            setPage(1);
          }}
          className="px-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">All Types</option>
          <option value="suspicious_activity">Suspicious Activity</option>
          <option value="sanctions_hit">Sanctions Hit</option>
          <option value="pep_match">PEP Match</option>
          <option value="velocity_breach">Velocity Breach</option>
          <option value="manual_review">Manual Review</option>
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
        emptyMessage="No compliance cases found."
      />

      <Modal
        open={!!resolveModal}
        onClose={() => {
          setResolveModal(null);
          setResolution('');
        }}
        title="Resolve Compliance Case"
        description="Provide a resolution for this compliance case."
        footer={
          <>
            <button
              onClick={() => {
                setResolveModal(null);
                setResolution('');
              }}
              className="px-4 py-2 text-sm text-gray-600 hover:text-gray-900"
            >
              Cancel
            </button>
            <button
              onClick={handleResolve}
              disabled={!resolution || actionLoading}
              className="px-4 py-2 text-sm bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50"
            >
              {actionLoading ? 'Resolving...' : 'Resolve Case'}
            </button>
          </>
        }
      >
        <textarea
          value={resolution}
          onChange={(e) => setResolution(e.target.value)}
          placeholder="Enter resolution details..."
          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          rows={4}
        />
      </Modal>
    </div>
  );
}
