import { http } from "./http";
import type { PageResult } from "../types/robot";
import type { CreateOrderRequest, InstantActionRequest, Order } from "../types/task";

export interface OrderQuery {
  page?: number;
  size?: number;
  status?: string;
}

export const createOrder = async (robotId: string, payload: CreateOrderRequest) => {
  const response = await http.post<Order>(`/robots/${robotId}/orders`, payload);
  return response.data;
};

export const getRobotOrders = async (robotId: string, query: OrderQuery = {}) => {
  const response = await http.get<PageResult<Order>>(`/robots/${robotId}/orders`, {
    params: query
  });
  return response.data;
};

export const getRobotOrder = async (robotId: string, orderRecordId: string) => {
  const response = await http.get<Order>(`/robots/${robotId}/orders/${orderRecordId}`);
  return response.data;
};

export const cancelOrder = async (robotId: string, orderRecordId: string) => {
  const response = await http.put<Order>(`/robots/${robotId}/orders/${orderRecordId}/cancel`);
  return response.data;
};

export const createInstantAction = async (robotId: string, payload: InstantActionRequest) => {
  const response = await http.post(`/robots/${robotId}/instant-actions`, payload);
  return response.data;
};
