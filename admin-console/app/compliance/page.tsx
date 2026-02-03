'use client';

import DataTable from '../../components/DataTable';

const columns = [
  { key: 'type', label: 'Type' },
  { key: 'userId', label: 'User ID' },
  { key: 'description', label: 'Description' },
  { key: 'status', label: 'Status' },
  { key: 'createdAt', label: 'Created' },
];

export default function CompliancePage() {
  const data: Record<string, string>[] = [];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Compliance Cases</h1>
        <p className="text-gray-500 mt-1">Manage compliance alerts and cases</p>
      </div>

      <DataTable
        columns={columns}
        data={data}
        actions={(row: Record<string, string>) => (
          <button className="text-xs px-3 py-1 bg-blue-100 text-blue-700 rounded-full hover:bg-blue-200">
            Resolve
          </button>
        )}
      />

      {data.length === 0 && (
        <div className="bg-white rounded-xl border border-gray-200 p-8 text-center text-gray-400">
          No open compliance cases
        </div>
      )}
    </div>
  );
}
