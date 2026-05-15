import { http } from "./http";
import type { PageResult, Robot } from "../types/robot";
import type { CreateOperatorRequest, Operator } from "../types/operator";
import type { UserRole } from "../types/auth";

export interface OperatorQuery {
  page?: number;
  size?: number;
  keyword?: string;
}

export const getOperators = async (query: OperatorQuery = {}) => {
  const response = await http.get<PageResult<Operator>>("/operators", {
    params: query
  });
  return response.data;
};

export const createOperator = async (payload: CreateOperatorRequest) => {
  const response = await http.post<Operator>("/operators", payload);
  return response.data;
};

export const updateOperatorRole = async (operatorId: string, role: UserRole) => {
  const response = await http.put<Operator>(`/operators/${operatorId}/role`, { role });
  return response.data;
};

export const deleteOperator = async (operatorId: string) => {
  await http.delete(`/operators/${operatorId}`);
};

export const getOperatorRobots = async (operatorId: string) => {
  const response = await http.get<Robot[]>(`/operators/${operatorId}/robots`);
  return response.data;
};

export const grantRobotAccess = async (operatorId: string, robotIds: string[]) => {
  await http.post(`/operators/${operatorId}/robots`, { robotIds });
};

export const revokeRobotAccess = async (operatorId: string, robotId: string) => {
  await http.delete(`/operators/${operatorId}/robots/${robotId}`);
};
