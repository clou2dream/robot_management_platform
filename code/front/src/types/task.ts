export type OrderStatus = "pending" | "active" | "completed" | "failed" | "cancelled";

export interface TaskNode {
  nodeId: string;
  x: number;
  y: number;
  theta?: number;
  mapId: string;
  allowedDeviationXY?: {
    a: number;
    b: number;
    theta?: number;
  };
  allowedDeviationTheta?: number;
  actions?: Record<string, unknown>[];
}

export interface TaskEdge {
  edgeId?: string;
  startNodeId: string;
  endNodeId: string;
  maximumSpeed?: number;
  orientation?: number;
  orientationType?: "GLOBAL" | "TANGENTIAL";
  reachOrientationBeforeEntering?: boolean;
  actions?: Record<string, unknown>[];
}

export interface CreateOrderRequest {
  orderId?: string;
  orderUpdateId?: number;
  orderDescription?: string;
  maximumSpeed?: number;
  nodes: TaskNode[];
  edges?: TaskEdge[];
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
