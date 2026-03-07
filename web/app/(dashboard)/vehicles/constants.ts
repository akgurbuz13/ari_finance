export const STATUS_COLORS: Record<string, string> = {
  PENDING: 'bg-yellow-100 text-yellow-800',
  MINTED: 'bg-green-100 text-green-800',
  IN_ESCROW: 'bg-blue-100 text-blue-800',
  TRANSFERRED: 'bg-purple-100 text-purple-800',
};

export const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Minting...',
  MINTED: 'Ready',
  IN_ESCROW: 'In Escrow',
  TRANSFERRED: 'Transferred',
};
