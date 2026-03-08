export const STATUS_VARIANT: Record<string, 'success' | 'warning' | 'error' | 'info' | 'neutral'> = {
  PENDING: 'warning',
  MINTED: 'success',
  IN_ESCROW: 'info',
  TRANSFERRED: 'neutral',
};

export const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Minting...',
  MINTED: 'Ready',
  IN_ESCROW: 'In Escrow',
  TRANSFERRED: 'Transferred',
};

export const ESCROW_STATE_LABELS: Record<string, string> = {
  CREATED: 'Escrow Created',
  JOINING: 'Buyer Joining',
  SETUP_COMPLETE: 'On-chain Setup',
  FUNDING: 'Funding',
  FUNDED: 'Funded',
  SELLER_CONFIRMED: 'Seller Confirmed',
  BUYER_CONFIRMED: 'Buyer Confirmed',
  COMPLETING: 'Completing',
  COMPLETED: 'Completed',
  CANCELLING: 'Cancelling',
  CANCELLED: 'Cancelled',
};
