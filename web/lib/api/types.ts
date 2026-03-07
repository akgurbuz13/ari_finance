export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  userId: string;
}

export interface User {
  id: string;
  email: string;
  phone: string;
  firstName: string | null;
  lastName: string | null;
  dateOfBirth: string | null;
  nationality: string | null;
  status: 'pending_kyc' | 'active' | 'suspended' | 'closed';
  region: 'TR' | 'EU';
  totpEnabled: boolean;
  createdAt: string;
}

export interface Account {
  id: string;
  currency: 'TRY' | 'EUR';
  accountType: string;
  status: 'active' | 'frozen' | 'closed';
  balance: string;
  region: 'TR' | 'EU';
  createdAt: string;
}

export interface Transaction {
  id: string;
  type: 'deposit' | 'withdrawal' | 'p2p_transfer' | 'fx_conversion' | 'cross_border' | 'mint' | 'burn' | 'fee';
  status: 'pending' | 'completed' | 'failed' | 'reversed';
  referenceId: string | null;
  metadata: Record<string, unknown> | null;
  createdAt: string;
  completedAt: string | null;
}

export interface PaymentOrder {
  id: string;
  type: 'deposit' | 'withdrawal' | 'domestic_p2p' | 'cross_border' | 'cross_border_same_ccy';
  status: string;
  senderAccountId: string;
  receiverAccountId: string;
  amount: string;
  currency: string;
  feeAmount: string;
  feeCurrency: string | null;
  fxQuoteId: string | null;
  ledgerTransactionId: string | null;
  description: string | null;
  metadata: Record<string, unknown> | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
}

export interface FxQuote {
  quoteId: string;
  sourceCurrency: string;
  targetCurrency: string;
  sourceAmount: string;
  targetAmount: string;
  customerRate: string;
  spread: string;
  expiresAt: string;
  status: string;
}

export interface KycStatus {
  id: string;
  status: 'pending' | 'approved' | 'rejected' | 'expired';
  level: 'basic' | 'enhanced';
  provider: string;
  createdAt: string;
}

export interface Vehicle {
  id: string;
  tokenId: number | null;
  ownerUserId: string;
  vin: string;
  plateNumber: string;
  make: string;
  model: string;
  year: number;
  color: string | null;
  mileage: number | null;
  fuelType: string | null;
  transmission: string | null;
  chainId: number;
  mintTxHash: string | null;
  status: 'PENDING' | 'MINTED' | 'IN_ESCROW' | 'TRANSFERRED';
  createdAt: string;
}

export interface VehicleEscrow {
  id: string;
  onChainEscrowId: number | null;
  vehicleRegistrationId: string;
  sellerUserId: string;
  buyerUserId: string | null;
  saleAmount: string;
  feeAmount: string;
  currency: string;
  state: string;
  sellerConfirmed: boolean;
  buyerConfirmed: boolean;
  shareCode: string;
  setupTxHash: string | null;
  fundTxHash: string | null;
  completeTxHash: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface FxRate {
  sourceCurrency: string;
  targetCurrency: string;
  rate: string;
  inverseRate: string;
  fetchedAt: string;
}
