export interface Indicator {
  id: string;
  factorId: string;
  factorName: string;
  factorCategory: string;
  value: number;
  unit: string | null;
  measuredAt: string;
  status: 'pendiente' | 'aprobado' | 'rechazado';
  approvedBy: string | null;
  approvedAt: string | null;
  rejectedBy: string | null;
  rejectedAt: string | null;
  rejectionReason: string | null;
}

export interface CreateIndicatorRequest {
  factorId: string;
  value: number;
  unit?: string;
}

export interface RejectIndicatorRequest {
  reason: string;
}
