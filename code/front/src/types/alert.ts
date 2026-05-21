export type AlertLevel = "WARNING" | "URGENT" | "CRITICAL" | "FATAL";
export type AlertStatus = "open" | "resolved";

export interface AlertRecord {
  id: string;
  robotId?: string;
  robotSn?: string;
  manufacturer?: string;
  status: AlertStatus;
  level: AlertLevel;
  errorType: string;
  description?: string;
  hint?: string;
  rawError?: Record<string, unknown>;
  triggeredAt: string;
  resolvedAt?: string;
}

export interface AlertSummary {
  openTotal: number;
  resolvedTotal: number;
  warning: number;
  urgent: number;
  critical: number;
  fatal: number;
}

export interface AlertQuery {
  page?: number;
  size?: number;
  status?: AlertStatus;
  level?: AlertLevel;
  robotId?: string;
  keyword?: string;
}
