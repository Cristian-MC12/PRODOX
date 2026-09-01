export interface AuthRequest {
  email: string;
  password: string;
  role?: string;
  nombre?: string;
}

export interface AuthResponse {
  token: string;
  userId: string;
  email: string;
  role: string;
  nombre?: string;
}
