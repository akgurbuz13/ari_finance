"use client";

import { useState, useMemo, useCallback } from "react";
import clsx from "clsx";

// ──────────────────────────────────────────────
// Types
// ──────────────────────────────────────────────

export interface Column<T> {
  key: string;
  header: string;
  sortable?: boolean;
  width?: string;
  render?: (row: T) => React.ReactNode;
}

export interface RowAction<T> {
  label: string;
  onClick: (row: T) => void;
  variant?: "default" | "danger" | "success" | "warning";
  hidden?: (row: T) => boolean;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  data: T[];
  rowKey: (row: T) => string;
  actions?: RowAction<T>[];
  page?: number;
  pageSize?: number;
  total?: number;
  onPageChange?: (page: number) => void;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
  onSort?: (key: string, order: "asc" | "desc") => void;
  loading?: boolean;
  emptyMessage?: string;
}

// ──────────────────────────────────────────────
// Component
// ──────────────────────────────────────────────

export default function DataTable<T>({
  columns,
  data,
  rowKey,
  actions,
  page = 1,
  pageSize = 10,
  total,
  onPageChange,
  sortBy,
  sortOrder = "asc",
  onSort,
  loading = false,
  emptyMessage = "No data found.",
}: DataTableProps<T>) {
  const [localSortBy, setLocalSortBy] = useState(sortBy || "");
  const [localSortOrder, setLocalSortOrder] = useState<"asc" | "desc">(sortOrder);

  const effectiveSortBy = sortBy ?? localSortBy;
  const effectiveSortOrder = sortOrder ?? localSortOrder;

  const handleSort = useCallback(
    (key: string) => {
      const newOrder =
        effectiveSortBy === key && effectiveSortOrder === "asc" ? "desc" : "asc";

      if (onSort) {
        onSort(key, newOrder);
      } else {
        setLocalSortBy(key);
        setLocalSortOrder(newOrder);
      }
    },
    [effectiveSortBy, effectiveSortOrder, onSort]
  );

  const sortedData = useMemo(() => {
    if (onSort || !effectiveSortBy) return data;

    return [...data].sort((a, b) => {
      const aVal = (a as Record<string, unknown>)[effectiveSortBy];
      const bVal = (b as Record<string, unknown>)[effectiveSortBy];

      if (aVal == null && bVal == null) return 0;
      if (aVal == null) return 1;
      if (bVal == null) return -1;

      const comparison =
        typeof aVal === "string"
          ? aVal.localeCompare(bVal as string)
          : (aVal as number) - (bVal as number);

      return effectiveSortOrder === "asc" ? comparison : -comparison;
    });
  }, [data, effectiveSortBy, effectiveSortOrder, onSort]);

  const totalPages = total ? Math.ceil(total / pageSize) : 1;
  const showPagination = total !== undefined && total > pageSize;

  const actionVariantStyles = {
    default: "text-admin-accent hover:text-admin-accent-hover",
    danger: "text-admin-danger hover:text-admin-danger-hover",
    success: "text-admin-success hover:text-emerald-700",
    warning: "text-admin-warning hover:text-amber-700",
  };

  return (
    <div className="admin-card overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          {/* Head */}
          <thead>
            <tr className="border-b border-admin-border bg-gray-50">
              {columns.map((col) => (
                <th
                  key={col.key}
                  className={clsx(
                    "px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-admin-text-secondary",
                    col.sortable && "cursor-pointer select-none hover:text-admin-text"
                  )}
                  style={col.width ? { width: col.width } : undefined}
                  onClick={col.sortable ? () => handleSort(col.key) : undefined}
                >
                  <div className="flex items-center gap-1.5">
                    {col.header}
                    {col.sortable && effectiveSortBy === col.key && (
                      <svg
                        className={clsx("w-3.5 h-3.5 transition-transform", effectiveSortOrder === "desc" && "rotate-180")}
                        fill="none"
                        viewBox="0 0 24 24"
                        strokeWidth={2}
                        stroke="currentColor"
                      >
                        <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 15.75l7.5-7.5 7.5 7.5" />
                      </svg>
                    )}
                  </div>
                </th>
              ))}
              {actions && actions.length > 0 && (
                <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wider text-admin-text-secondary">
                  Actions
                </th>
              )}
            </tr>
          </thead>

          {/* Body */}
          <tbody className="divide-y divide-admin-border-light">
            {loading ? (
              <tr>
                <td
                  colSpan={columns.length + (actions ? 1 : 0)}
                  className="px-4 py-12 text-center"
                >
                  <div className="flex items-center justify-center gap-2 text-admin-text-secondary">
                    <svg className="w-5 h-5 animate-spin" viewBox="0 0 24 24" fill="none">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                    </svg>
                    Loading...
                  </div>
                </td>
              </tr>
            ) : sortedData.length === 0 ? (
              <tr>
                <td
                  colSpan={columns.length + (actions ? 1 : 0)}
                  className="px-4 py-12 text-center text-admin-text-secondary"
                >
                  {emptyMessage}
                </td>
              </tr>
            ) : (
              sortedData.map((row) => (
                <tr
                  key={rowKey(row)}
                  className="group hover:bg-gray-50/50 transition-colors"
                >
                  {columns.map((col) => (
                    <td key={col.key} className="px-4 py-3 text-admin-text">
                      {col.render
                        ? col.render(row)
                        : String((row as Record<string, unknown>)[col.key] ?? "")}
                    </td>
                  ))}
                  {actions && actions.length > 0 && (
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-2">
                        {actions.map((action, idx) => {
                          if (action.hidden?.(row)) return null;
                          return (
                            <button
                              key={idx}
                              onClick={() => action.onClick(row)}
                              className={clsx(
                                "text-xs font-medium px-2.5 py-1 rounded-md transition-colors",
                                actionVariantStyles[action.variant || "default"]
                              )}
                            >
                              {action.label}
                            </button>
                          );
                        })}
                      </div>
                    </td>
                  )}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {showPagination && (
        <div className="flex items-center justify-between px-4 py-3 border-t border-admin-border bg-gray-50">
          <p className="text-sm text-admin-text-secondary">
            Showing{" "}
            <span className="font-medium text-admin-text">
              {(page - 1) * pageSize + 1}
            </span>{" "}
            to{" "}
            <span className="font-medium text-admin-text">
              {Math.min(page * pageSize, total!)}
            </span>{" "}
            of{" "}
            <span className="font-medium text-admin-text">{total}</span> results
          </p>
          <div className="flex items-center gap-1">
            <button
              onClick={() => onPageChange?.(page - 1)}
              disabled={page <= 1}
              className="admin-btn-ghost px-2 py-1 disabled:opacity-30"
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 19.5 8.25 12l7.5-7.5" />
              </svg>
            </button>
            {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => {
              let pageNum: number;
              if (totalPages <= 5) {
                pageNum = i + 1;
              } else if (page <= 3) {
                pageNum = i + 1;
              } else if (page >= totalPages - 2) {
                pageNum = totalPages - 4 + i;
              } else {
                pageNum = page - 2 + i;
              }
              return (
                <button
                  key={pageNum}
                  onClick={() => onPageChange?.(pageNum)}
                  className={clsx(
                    "min-w-[32px] h-8 rounded-md text-sm font-medium transition-colors",
                    page === pageNum
                      ? "bg-admin-accent text-white"
                      : "text-admin-text-secondary hover:bg-gray-200"
                  )}
                >
                  {pageNum}
                </button>
              );
            })}
            <button
              onClick={() => onPageChange?.(page + 1)}
              disabled={page >= totalPages}
              className="admin-btn-ghost px-2 py-1 disabled:opacity-30"
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" d="m8.25 4.5 7.5 7.5-7.5 7.5" />
              </svg>
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
