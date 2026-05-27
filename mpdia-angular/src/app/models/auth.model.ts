export interface AuthRequest {
  email: string;
  password: string;
  role?: string;
}

export interface AuthResponse {
  token: string;
  userId: string;
  email: string;
  role: string;
}
