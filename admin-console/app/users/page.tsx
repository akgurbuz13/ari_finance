'use client';

import { useState } from 'react';
import DataTable from '../../components/DataTable';

const columns = [
  { key: 'email', label: 'Email' },
  { key: 'phone', label: 'Phone' },
  { key: 'status', label: 'Status' },
  { key: 'region', label: 'Region' },
  { key: 'createdAt', label: 'Joined' },
];

export default function UsersPage() {
  const [search, setSearch] = useState('');
  const data: Record<string, string>[] = [];

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Users</h1>
          <p className="text-gray-500 mt-1">Manage platform users</p>
        </div>
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search by email or phone..."
          className="px-4 py-2 border border-gray-300 rounded-lg text-sm w-72 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>

      <DataTable
        columns={columns}
        data={data}
        actions={(row: Record<string, string>) => (
          <div className="flex gap-2">
            <button className="text-xs px-3 py-1 bg-gray-100 text-gray-700 rounded-full hover:bg-gray-200">
              View
            </button>
            <button className="text-xs px-3 py-1 bg-yellow-100 text-yellow-700 rounded-full hover:bg-yellow-200">
              {row.status === 'suspended' ? 'Reactivate' : 'Suspend'}
            </button>
          </div>
        )}
      />
    </div>
  );
}
