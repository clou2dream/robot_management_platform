import { http } from "./http";
import type { PageResult } from "../types/robot";
import type {
  AlertQuery,
  AlertRecord,
  AlertSummary
} from "../types/alert";

export const getAlerts = async (query: AlertQuery = {}) => {
  const response = await http.get<PageResult<AlertRecord>>("/alerts", {
    params: query
  });
  return response.data;
};

export const getAlert = async (alertId: string) => {
  const response = await http.get<AlertRecord>(`/alerts/${alertId}`);
  return response.data;
};

export const getAlertSummary = async () => {
  const response = await http.get<AlertSummary>("/alerts/summary");
  return response.data;
};

export const resolveAlert = async (alertId: string) => {
  const response = await http.put<AlertRecord>(`/alerts/${alertId}/resolve`);
  return response.data;
};
