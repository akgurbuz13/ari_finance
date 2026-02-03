'use client';

import { useState, useEffect } from 'react';
import MetricCard from '../../components/MetricCard';
import {
  fetchDashboardMetrics,
  fetchRecentActivity,
  DashboardMetrics,
  ActivityItem,
} from '../../lib/api';

export default function DashboardPage() {
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [activity, setActivity] = useState<ActivityItem[]>([]);
  const [metricsLoading, setMetricsLoading] = useState(true);
  const [activityLoading, setActivityLoading] = useState(true);
  const [metricsError, setMetricsError] = useState<string | null>(null);
  const [activityError, setActivityError] = useState<string | null>(null);

  useEffect(() => {
    loadMetrics();
    loadActivity();
  }, []);

  const loadMetrics = async () => {
    try {
      setMetricsLoading(true);
      setMetricsError(null);
      const data = await fetchDashboardMetrics();
      setMetrics(data);
    } catch (err) {
      setMetricsError('Failed to load dashboard metrics');
      console.error('Error loading metrics:', err);
    } finally {
      setMetricsLoading(false);
    }
  };

  const loadActivity = async () => {
    try {
      setActivityLoading(true);
      setActivityError(null);
      const data = await fetchRecentActivity(20);
      setActivity(data);
    } catch (err) {
      setActivityError('Failed to load recent activity');
      console.error('Error loading activity:', err);
    } finally {
      setActivityLoading(false);
    }
  };

  const formatVolume = (value: number, currency: string) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: currency,
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(value);
  };

  const formatTimestamp = (timestamp: string) => {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);

    if (diffMins < 1) return 'just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours}h ago`;
    const diffDays = Math.floor(diffHours / 24);
    return `${diffDays}d ago`;
  };

  const activityTypeColors: Record<string, string> = {
    kyc: 'bg-blue-100 text-blue-700',
    compliance: 'bg-red-100 text-red-700',
    user: 'bg-green-100 text-green-700',
    transaction: 'bg-yellow-100 text-yellow-700',
    system: 'bg-gray-100 text-gray-700',
  };

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
        <p className="text-gray-500 mt-1">System overview</p>
      </div>

      {metricsError && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 flex items-center justify-between">
          <p className="text-sm text-red-700">{metricsError}</p>
          <button
            onClick={loadMetrics}
            className="text-sm text-red-700 underline hover:text-red-900"
          >
            Retry
          </button>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {metricsLoading ? (
          <>
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="admin-card p-6 animate-pulse">
                <div className="h-4 bg-gray-200 rounded w-24 mb-3" />
                <div className="h-8 bg-gray-200 rounded w-16 mb-2" />
                <div className="h-3 bg-gray-200 rounded w-20" />
              </div>
            ))}
          </>
        ) : metrics ? (
          <>
            <MetricCard
              title="Total Users"
              value={metrics.totalUsers.toLocaleString()}
              trend={metrics.totalUsersTrend}
              variant="accent"
            />
            <MetricCard
              title="Pending KYC"
              value={metrics.pendingKyc.toLocaleString()}
              trend={metrics.pendingKycTrend}
              variant="warning"
            />
            <MetricCard
              title="Active Cases"
              value={metrics.activeCases.toLocaleString()}
              trend={metrics.activeCasesTrend}
              variant="danger"
            />
            <MetricCard
              title="Tx Volume (24h)"
              value={formatVolume(
                metrics.transactionVolume,
                metrics.transactionVolumeCurrency
              )}
              trend={metrics.transactionVolumeTrend}
              variant="success"
            />
          </>
        ) : (
          <>
            {['Total Users', 'Pending KYC', 'Active Cases', 'Tx Volume (24h)'].map(
              (title) => (
                <MetricCard key={title} title={title} value="--" />
              )
            )}
          </>
        )}
      </div>

      <div className="bg-white rounded-xl border border-gray-200 p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">
          Recent Activity
        </h2>

        {activityError && (
          <div className="text-sm text-red-500 mb-4 flex items-center justify-between">
            <span>{activityError}</span>
            <button
              onClick={loadActivity}
              className="text-sm text-red-500 underline hover:text-red-700"
            >
              Retry
            </button>
          </div>
        )}

        {activityLoading ? (
          <div className="space-y-3">
            {[1, 2, 3, 4, 5].map((i) => (
              <div key={i} className="flex items-center gap-3 animate-pulse">
                <div className="h-6 w-20 bg-gray-200 rounded-full" />
                <div className="h-4 bg-gray-200 rounded flex-1" />
                <div className="h-3 bg-gray-200 rounded w-12" />
              </div>
            ))}
          </div>
        ) : activity.length === 0 ? (
          <div className="text-sm text-gray-400 text-center py-8">
            No recent activity
          </div>
        ) : (
          <div className="space-y-3">
            {activity.map((item) => (
              <div
                key={item.id}
                className="flex items-center gap-3 py-2 border-b border-gray-100 last:border-0"
              >
                <span
                  className={`text-xs px-2.5 py-0.5 rounded-full font-medium ${
                    activityTypeColors[item.type] || activityTypeColors.system
                  }`}
                >
                  {item.type}
                </span>
                <span className="text-sm text-gray-700 flex-1">
                  {item.message}
                </span>
                <span className="text-xs text-gray-400 shrink-0">
                  {formatTimestamp(item.timestamp)}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
