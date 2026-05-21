import { http } from "./http";
import type {
  PageResult,
  RobotFactsheet,
  Robot,
  RobotConnection,
  RobotPosition,
  RobotRealtime,
  RobotState
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

export const getRobotTrajectory = async (robotId: string, limit = 240) => {
  const response = await http.get<RobotPosition[]>(`/robots/${robotId}/trajectory`, {
    params: { limit }
  });
  return response.data;
};

export const getRobotFactsheet = async (robotId: string) => {
  const response = await http.get<RobotFactsheet>(`/robots/${robotId}/factsheet`);
  return response.data;
};
