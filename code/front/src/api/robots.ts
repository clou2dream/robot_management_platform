import { http } from "./http";
import type {
  CreateDemoTelemetryRequest,
  PageResult,
  Robot,
  RobotConnection,
  RobotPosition,
  RobotRealtime,
  RobotState,
  RobotSyncResult
} from "../types/robot";

export interface RobotQuery {
  page?: number;
  size?: number;
  keyword?: string;
  status?: string;
}

export const getRobots = async (query: RobotQuery = {}) => {
  const response = await http.get<PageResult<Robot>>("/robots", {
    params: query
  });
  return response.data;
};

export const syncRobots = async () => {
  const response = await http.post<RobotSyncResult>("/robots/sync");
  return response.data;
};

export const getRobotRealtime = async (robotId: string) => {
  const response = await http.get<RobotRealtime>(`/robots/${robotId}/realtime`);
  return response.data;
};

export const getRobotConnection = async (robotId: string) => {
  const response = await http.get<RobotConnection>(`/robots/${robotId}/connection`);
  return response.data;
};

export const getRobotState = async (robotId: string) => {
  const response = await http.get<RobotState>(`/robots/${robotId}/state`);
  return response.data;
};

export const getRobotPosition = async (robotId: string) => {
  const response = await http.get<RobotPosition>(`/robots/${robotId}/position`);
  return response.data;
};

export const createDemoTelemetry = async (
  robotId: string,
  payload: CreateDemoTelemetryRequest = {}
) => {
  const response = await http.post<RobotRealtime>(`/robots/${robotId}/telemetry/demo`, payload);
  return response.data;
};
