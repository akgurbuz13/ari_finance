'use client';

import MetricCard from '../../components/MetricCard';

const metrics = [
  { title: 'Total Users', value: '—', trend: 'Loading...' },
  { title: 'Pending KYC', value: '—', trend: 'Loading...' },
  { title: 'Active Cases', value: '—', trend: 'Loading...' },
  { title: 'Tx Volume (24h)', value: '—', trend: 'Loading...' },
];

export default function DashboardPage() {
  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
        <p className="text-gray-500 mt-1">System overview</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {metrics.map((metric) => (
          <MetricCard key={metric.title} {...metric} />
        ))}
      </div>

      <div className="bg-white rounded-xl border border-gray-200 p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Recent Activity</h2>
        <div className="text-sm text-gray-400 text-center py-8">
          Connect to backend to load activity feed
        </div>
      </div>
    </div>
  );
}
