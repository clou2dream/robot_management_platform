export type UserRole = "admin" | "operator" | "viewer";

export interface CurrentUser {
  operatorId: string;
  tenantId: string;
  username: string;
  role: UserRole;
  tenantName?: string;
  debugPermission?: boolean;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  currentUser: CurrentUser;
}
