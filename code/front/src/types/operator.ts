import type { CurrentUser, UserRole } from "./auth";

export interface Operator {
  id: string;
  tenantId: string;
  username: string;
  role: UserRole;
  robotAccessCount: number;
  passwordResetRequired: boolean;
  createdAt?: string;
}

export interface CreateOperatorRequest {
  username: string;
  password: string;
  role: CurrentUser["role"];
}
