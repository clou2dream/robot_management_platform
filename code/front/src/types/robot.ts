export type RobotSourceStatus = "active" | "disabled" | "revoked";

export interface Robot {
  id: string;
  externalRobotId: string;
  serialNumber: string;
  manufacturer?: string;
  sourceAppId?: string;
  status: RobotSourceStatus;
  online: boolean;
  syncedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

export interface RobotConnection {
  robotId: string;
  connectionState: string;
  online: boolean;
  time?: string;
}

export interface RobotPosition {
  robotId: string;
  x?: number;
  y?: number;
  theta?: number;
  mapId?: string;
  time?: string;
}

export interface RobotState {
  robotId: string;
  batterySoc?: number;
  operatingMode?: string;
  orderId?: string;
  position?: RobotPosition;
  agvPosition?: RobotPosition;
  rawPayload?: Record<string, unknown>;
  time?: string;
}

export interface RobotFactsheet {
  robotId: string;
  receivedAt?: string;
  rawPayload?: Record<string, unknown>;
}

export interface RobotRealtime {
  robot: Robot;
  connection?: RobotConnection;
  state?: RobotState;
  position?: RobotPosition;
}
