'use client';

import { useState, useEffect, useCallback } from 'react';
import DataTable, { Column } from '../../components/DataTable';
import { fetchAuditLog, AuditLogEntry } from '../../lib/api';

export default function AuditLogPage() {
  const [data, setData] = useState<AuditLogEntry[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(20);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionFilter, setActionFilter] = useState('');
  const [actorTypeFilter, setActorTypeFilter] = useState('');
  const [resourceTypeFilter, setResourceTypeFilter] = useState('');
  const [search, setSearch] = useState('');
  const [sortBy, setSortBy] = useState('created_at');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');

  const [expandedRow, setExpandedRow] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await fetchAuditLog({
        page,
        pageSize,
        action: actionFilter || undefined,
        actorType: actorTypeFilter || undefined,
        resourceType: resourceTypeFilter || undefined,
        search: search || undefined,
        sortBy,
        sortOrder,
      });
      setData(response.items);
      setTotal(response.total);
    } catch (err) {
      setError('Failed to load audit log');
      console.error('Error loading audit log:', err);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, actionFilter, actorTypeFilter, resourceTypeFilter, search, sortBy, sortOrder]);

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

  const handleSort = (key: string, order: 'asc' | 'desc') => {
    setSortBy(key);
    setSortOrder(order);
  };

  const actorTypeBadge = (type: string) => {
    const styles: Record<string, string> = {
      admin: 'bg-purple-100 text-purple-700',
      user: 'bg-blue-100 text-blue-700',
      system: 'bg-gray-100 text-gray-700',
    };
    return (
      <span
        className={`text-xs px-2.5 py-0.5 rounded-full font-medium ${
          styles[type] || styles.system
        }`}
      >
        {type}
      </span>
    );
  };

  const actionBadge = (action: string) => {
    let style = 'bg-gray-100 text-gray-700';
    if (action.includes('approve') || action.includes('reactivate') || action.includes('unfreeze')) {
      style = 'bg-green-100 text-green-700';
    } else if (action.includes('reject') || action.includes('suspend') || action.includes('freeze')) {
      style = 'bg-red-100 text-red-700';
    } else if (action.includes('create') || action.includes('initiate')) {
      style = 'bg-blue-100 text-blue-700';
    } else if (action.includes('update') || action.includes('resolve')) {
      style = 'bg-yellow-100 text-yellow-700';
    }
    return (
      <span className={`text-xs px-2.5 py-0.5 rounded-full font-medium ${style}`}>
        {action.replace(/_/g, ' ')}
      </span>
    );
  };

  const columns: Column<AuditLogEntry>[] = [
    {
      key: 'createdAt',
      header: 'Timestamp',
      sortable: true,
      render: (row) => (
        <span className="text-sm text-gray-600 whitespace-nowrap">
          {new Date(row.createdAt).toLocaleString()}
        </span>
      ),
    },
    {
      key: 'actorType',
      header: 'Actor',
      sortable: true,
      render: (row) => (
        <div className="flex flex-col gap-0.5">
          {actorTypeBadge(row.actorType)}
          {row.actorId && (
            <span className="text-xs text-gray-400 truncate max-w-[120px]">
              {row.actorId.substring(0, 8)}...
            </span>
          )}
        </div>
      ),
    },
    {
      key: 'action',
      header: 'Action',
      sortable: true,
      render: (row) => actionBadge(row.action),
    },
    {
      key: 'resourceType',
      header: 'Resource',
      sortable: true,
      render: (row) => (
        <div className="flex flex-col gap-0.5">
          <span className="text-sm font-medium text-gray-700">{row.resourceType}</span>
          <span className="text-xs text-gray-400 truncate max-w-[120px]">
            {row.resourceId.substring(0, 8)}...
          </span>
        </div>
      ),
    },
    {
      key: 'details',
      header: 'Details',
      sortable: false,
      render: (row) => {
        if (!row.details) {
          return <span className="text-xs text-gray-400">-</span>;
        }
        try {
          const parsed = JSON.parse(row.details);
          const summary = Object.entries(parsed)
            .map(([k, v]) => `${k}: ${v}`)
            .join(', ');
          return (
            <span
              className="text-xs text-gray-600 truncate max-w-xs block cursor-pointer hover:text-gray-900"
              title={summary}
              onClick={() => setExpandedRow(expandedRow === row.id ? null : row.id)}
            >
              {expandedRow === row.id ? summary : summary.substring(0, 40) + (summary.length > 40 ? '...' : '')}
            </span>
          );
        } catch {
          return <span className="text-xs text-gray-600">{row.details}</span>;
        }
      },
    },
    {
      key: 'ipAddress',
      header: 'IP',
      sortable: false,
      render: (row) => (
        <span className="text-xs text-gray-500">{row.ipAddress || '-'}</span>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Audit Log</h1>
        <p className="text-gray-500 mt-1">
          Complete audit trail of system actions
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
          placeholder="Search audit log..."
          className="px-4 py-2 border border-gray-300 rounded-lg text-sm w-72 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <select
          value={actorTypeFilter}
          onChange={(e) => {
            setActorTypeFilter(e.target.value);
            setPage(1);
          }}
          className="px-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">All Actors</option>
          <option value="admin">Admin</option>
          <option value="user">User</option>
          <option value="system">System</option>
        </select>
        <select
          value={resourceTypeFilter}
          onChange={(e) => {
            setResourceTypeFilter(e.target.value);
            setPage(1);
          }}
          className="px-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">All Resources</option>
          <option value="user">User</option>
          <option value="kyc">KYC</option>
          <option value="account">Account</option>
          <option value="compliance_case">Compliance</option>
          <option value="transaction">Transaction</option>
        </select>
        <select
          value={actionFilter}
          onChange={(e) => {
            setActionFilter(e.target.value);
            setPage(1);
          }}
          className="px-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">All Actions</option>
          <option value="approve_kyc">Approve KYC</option>
          <option value="reject_kyc">Reject KYC</option>
          <option value="suspend_user">Suspend User</option>
          <option value="reactivate_user">Reactivate User</option>
          <option value="freeze_account">Freeze Account</option>
          <option value="unfreeze_account">Unfreeze Account</option>
          <option value="create_compliance_case">Create Case</option>
          <option value="resolve_compliance_case">Resolve Case</option>
        </select>
      </div>

      <DataTable
        columns={columns}
        data={data}
        rowKey={(row) => row.id}
        page={page}
        pageSize={pageSize}
        total={total}
        onPageChange={setPage}
        sortBy={sortBy}
        sortOrder={sortOrder}
        onSort={handleSort}
        loading={loading}
        emptyMessage="No audit log entries found."
      />
    </div>
  );
}
