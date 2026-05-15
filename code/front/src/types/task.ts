export type OrderStatus = "pending" | "active" | "completed" | "failed" | "cancelled";

export interface TaskNode {
  nodeId: string;
  x: number;
  y: number;
  theta?: number;
}

export interface CreateOrderRequest {
  orderId?: string;
  maxSpeed?: number;
  nodes: TaskNode[];
}

export interface Order {
  id: string;
  robotId: string;
  robotSn?: string;
  orderId: string;
  status: OrderStatus;
  payload: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface InstantActionRequest {
  actionType: "stopPause" | "startPause" | "cancelOrder" | "factsheetRequest";
  orderId?: string;
}
