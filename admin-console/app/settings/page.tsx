'use client';

export default function SettingsPage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Settings</h1>
        <p className="text-gray-500 mt-1">System configuration</p>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 p-6 space-y-6">
        <div>
          <h2 className="text-lg font-semibold text-gray-900">Transaction Limits</h2>
          <div className="mt-4 grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm text-gray-600 mb-1">Daily Limit (TRY)</label>
              <input type="number" defaultValue={500000} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
            </div>
            <div>
              <label className="block text-sm text-gray-600 mb-1">Daily Limit (EUR)</label>
              <input type="number" defaultValue={15000} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
            </div>
            <div>
              <label className="block text-sm text-gray-600 mb-1">Single Tx Alert (TRY)</label>
              <input type="number" defaultValue={100000} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
            </div>
            <div>
              <label className="block text-sm text-gray-600 mb-1">Single Tx Alert (EUR)</label>
              <input type="number" defaultValue={3000} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
            </div>
          </div>
        </div>

        <div className="border-t border-gray-100 pt-6">
          <h2 className="text-lg font-semibold text-gray-900">FX Configuration</h2>
          <div className="mt-4 grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm text-gray-600 mb-1">Default Spread (%)</label>
              <input type="number" defaultValue={0.3} step={0.1} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
            </div>
            <div>
              <label className="block text-sm text-gray-600 mb-1">Quote TTL (seconds)</label>
              <input type="number" defaultValue={30} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
            </div>
          </div>
        </div>

        <div className="pt-4">
          <button className="px-6 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700">
            Save Changes
          </button>
        </div>
      </div>
    </div>
  );
}
