import axios, { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from "axios";

const API_BASE_URL = process.env.NEXT_PUBLIC_ADMIN_API_URL || "http://localhost:8080/admin/api/v1";

const adminApi: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    "Content-Type": "application/json",
  },
});

// Request interceptor: attach JWT token
adminApi.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    if (typeof window !== "undefined") {
      const token = localStorage.getItem("ova_admin_token");
      if (token && config.headers) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    }
    return config;
  },
  (error: AxiosError) => Promise.reject(error)
);

// Response interceptor: handle auth errors
adminApi.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      if (typeof window !== "undefined") {
        localStorage.removeItem("ova_admin_token");
        window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  }
);

// ──────────────────────────────────────────────
// Dashboard
// ──────────────────────────────────────────────

export interface DashboardMetrics {
  totalUsers: number;
  totalUsersTrend: number;
  pendingKyc: number;
  pendingKycTrend: number;
  activeCases: number;
  activeCasesTrend: number;
  transactionVolume: number;
  transactionVolumeTrend: number;
  transactionVolumeCurrency: string;
}

export interface ActivityItem {
  id: string;
  type: "kyc" | "compliance" | "user" | "transaction" | "system";
  message: string;
  timestamp: string;
  actor?: string;
}

export async function fetchDashboardMetrics(): Promise<DashboardMetrics> {
  const { data } = await adminApi.get<DashboardMetrics>("/dashboard/metrics");
  return data;
}

export async function fetchRecentActivity(limit = 20): Promise<ActivityItem[]> {
  const { data } = await adminApi.get<ActivityItem[]>("/dashboard/activity", {
    params: { limit },
  });
  return data;
}

// ──────────────────────────────────────────────
// KYC
// ──────────────────────────────────────────────

export type KycStatus = "pending" | "approved" | "rejected" | "in_review" | "expired";

export interface KycVerification {
  id: string;
  userId: string;
  userName: string;
  email: string;
  status: KycStatus;
  provider: string;
  region: string;
  submittedAt: string;
  reviewedAt?: string;
  reviewedBy?: string;
  rejectionReason?: string;
}

export interface KycListResponse {
  items: KycVerification[];
  total: number;
  page: number;
  pageSize: number;
}

export async function fetchKycVerifications(params: {
  page?: number;
  pageSize?: number;
  status?: KycStatus;
  search?: string;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
}): Promise<KycListResponse> {
  const { data } = await adminApi.get<KycListResponse>("/kyc/verifications", { params });
  return data;
}

export async function approveKyc(verificationId: string): Promise<void> {
  await adminApi.post(`/kyc/verifications/${verificationId}/approve`);
}

export async function rejectKyc(verificationId: string, reason: string): Promise<void> {
  await adminApi.post(`/kyc/verifications/${verificationId}/reject`, { reason });
}

// ──────────────────────────────────────────────
// Compliance
// ──────────────────────────────────────────────

export type CaseStatus = "open" | "in_review" | "escalated" | "resolved" | "closed";
export type CaseType = "suspicious_activity" | "sanctions_hit" | "pep_match" | "velocity_breach" | "manual_review";

export interface ComplianceCase {
  id: string;
  type: CaseType;
  userId: string;
  userName: string;
  description: string;
  assignedTo: string;
  status: CaseStatus;
  priority: "low" | "medium" | "high" | "critical";
  createdAt: string;
  updatedAt: string;
  resolvedAt?: string;
  resolution?: string;
}

export interface CaseListResponse {
  items: ComplianceCase[];
  total: number;
  page: number;
  pageSize: number;
}

export async function fetchComplianceCases(params: {
  page?: number;
  pageSize?: number;
  status?: CaseStatus;
  type?: CaseType;
  search?: string;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
}): Promise<CaseListResponse> {
  const { data } = await adminApi.get<CaseListResponse>("/compliance/cases", { params });
  return data;
}

export async function resolveCase(caseId: string, resolution: string): Promise<void> {
  await adminApi.post(`/compliance/cases/${caseId}/resolve`, { resolution });
}

// ──────────────────────────────────────────────
// Users
// ──────────────────────────────────────────────

export type UserStatus = "active" | "suspended" | "pending" | "closed";
export type AccountStatus = "active" | "frozen" | "closed";

export interface UserAccount {
  id: string;
  currency: string;
  balance: number;
  status: AccountStatus;
  iban?: string;
}

export interface AdminUser {
  id: string;
  name: string;
  email: string;
  phone?: string;
  status: UserStatus;
  kycStatus: KycStatus;
  region: string;
  createdAt: string;
  lastLoginAt?: string;
  accounts: UserAccount[];
}

export interface UserListResponse {
  items: AdminUser[];
  total: number;
  page: number;
  pageSize: number;
}

export async function fetchUsers(params: {
  page?: number;
  pageSize?: number;
  status?: UserStatus;
  kycStatus?: KycStatus;
  region?: string;
  search?: string;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
}): Promise<UserListResponse> {
  const { data } = await adminApi.get<UserListResponse>("/users", { params });
  return data;
}

export async function suspendUser(userId: string, reason: string): Promise<void> {
  await adminApi.post(`/users/${userId}/suspend`, { reason });
}

export async function reactivateUser(userId: string): Promise<void> {
  await adminApi.post(`/users/${userId}/reactivate`);
}

export async function freezeAccount(userId: string, accountId: string, reason: string): Promise<void> {
  await adminApi.post(`/users/${userId}/accounts/${accountId}/freeze`, { reason });
}

export async function unfreezeAccount(userId: string, accountId: string): Promise<void> {
  await adminApi.post(`/users/${userId}/accounts/${accountId}/unfreeze`);
}

// ──────────────────────────────────────────────
// Settings
// ──────────────────────────────────────────────

export interface SystemConfig {
  maintenanceMode: boolean;
  registrationEnabled: boolean;
  crossBorderEnabled: boolean;
  maxDailyTransferTRY: number;
  maxDailyTransferEUR: number;
  maxSingleTransferTRY: number;
  maxSingleTransferEUR: number;
  kycAutoApproveEnabled: boolean;
  sanctionsScreeningEnabled: boolean;
}

export async function fetchSystemConfig(): Promise<SystemConfig> {
  const { data } = await adminApi.get<SystemConfig>("/settings/config");
  return data;
}

export async function updateSystemConfig(config: Partial<SystemConfig>): Promise<SystemConfig> {
  const { data } = await adminApi.patch<SystemConfig>("/settings/config", config);
  return data;
}

export default adminApi;
