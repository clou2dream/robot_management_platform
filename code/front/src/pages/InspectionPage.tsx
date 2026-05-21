import {
  AlertOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  ReloadOutlined
} from "@ant-design/icons";
import { Client, type IMessage } from "@stomp/stompjs";
import { App, Button, Card, Col, Empty, List, Row, Space, Tag, Typography } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { getRobotRealtime, getRobotTrajectory, getRobots } from "../api/robots";
import { createInstantAction } from "../api/tasks";
import { getAccessToken } from "../stores/authStore";
import type { Robot, RobotConnection, RobotPosition, RobotRealtime, RobotState } from "../types/robot";

const formatDateTime = (value?: string) => {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString("zh-CN", { hour12: false });
};

const MAP_PADDING_PERCENT = 8;
const MAP_DRAWING_PERCENT = 100 - MAP_PADDING_PERCENT * 2;
const COORDINATE_EPSILON = 0.000001;
const TRAJECTORY_POINT_LIMIT = 240;
const TRAJECTORY_POLL_INTERVAL_MS = 1000;

const currentMapPosition = (realtime?: RobotRealtime): RobotPosition | undefined => realtime?.position;

const hasCoordinates = (position?: RobotPosition): position is RobotPosition & { x: number; y: number } =>
  typeof position?.x === "number"
  && Number.isFinite(position.x)
  && typeof position.y === "number"
  && Number.isFinite(position.y);

const appendTrajectoryPoint = (points: RobotPosition[], position?: RobotPosition) => {
  if (!hasCoordinates(position)) {
    return points;
  }

  const last = points.at(-1);
  if (last && last.x === position.x && last.y === position.y && last.time === position.time) {
    return points;
  }

  return [...points, position].slice(-TRAJECTORY_POINT_LIMIT);
};

const trajectoryPointKey = (point: RobotPosition) =>
  `${point.time ?? ""}:${point.x ?? ""}:${point.y ?? ""}:${point.theta ?? ""}:${point.mapId ?? ""}`;

const pointTimeValue = (point: RobotPosition) => {
  if (!point.time) {
    return Number.MAX_SAFE_INTEGER;
  }
  const value = new Date(point.time).getTime();
  return Number.isNaN(value) ? Number.MAX_SAFE_INTEGER : value;
};

const mergeTrajectoryPoints = (current: RobotPosition[], incoming: RobotPosition[]) => {
  const merged = new Map<string, RobotPosition>();
  [...current, ...incoming].filter(hasCoordinates).forEach((point) => {
    merged.set(trajectoryPointKey(point), point);
  });

  return [...merged.values()]
    .sort((left, right) => pointTimeValue(left) - pointTimeValue(right))
    .slice(-TRAJECTORY_POINT_LIMIT);
};

const axisToPercent = (value: number, min: number, max: number) => {
  const span = max - min;
  if (span < COORDINATE_EPSILON) {
    return 50;
  }
  return MAP_PADDING_PERCENT + ((value - min) / span) * MAP_DRAWING_PERCENT;
};

const buildMapProjection = (points: RobotPosition[], currentPosition?: RobotPosition) => {
  const coordinates = [...points, currentPosition].filter(hasCoordinates);

  if (coordinates.length === 0) {
    return {
      current: undefined,
      trajectory: []
    };
  }

  const xs = coordinates.map((point) => point.x);
  const ys = coordinates.map((point) => point.y);
  const minX = Math.min(...xs);
  const maxX = Math.max(...xs);
  const minY = Math.min(...ys);
  const maxY = Math.max(...ys);

  const project = (point?: RobotPosition) => {
    if (!hasCoordinates(point)) {
      return undefined;
    }

    return {
      screenX: axisToPercent(point.x, minX, maxX),
      screenY: 100 - axisToPercent(point.y, minY, maxY)
    };
  };

  return {
    current: project(currentPosition),
    trajectory: points
      .filter(hasCoordinates)
      .map((point) => ({
        point,
        ...project(point)!
      }))
  };
};

const parseFrame = <T,>(frame: IMessage): T | null => {
  try {
    return JSON.parse(frame.body) as T;
  } catch {
    return null;
  }
};

const mergeConnection = (current: RobotRealtime | undefined, connection: RobotConnection) => {
  if (!current) {
    return current;
  }
  return {
    ...current,
    connection,
    robot: {
      ...current.robot,
      online: connection.online
    }
  };
};

const mergeState = (current: RobotRealtime | undefined, state: RobotState) => {
  if (!current) {
    return current;
  }
  return {
    ...current,
    state
  };
};

const mergePosition = (current: RobotRealtime | undefined, position: RobotPosition) => {
  if (!current) {
    return current;
  }
  return {
    ...current,
    position
  };
};

const mergeRealtime = (
  current: RobotRealtime | undefined,
  patch: Partial<RobotRealtime>
): RobotRealtime | undefined => {
  if (!current) {
    return patch.robot ? (patch as RobotRealtime) : current;
  }
  return {
    robot: patch.robot ? { ...current.robot, ...patch.robot } : current.robot,
    connection: patch.connection ?? current.connection,
    state: patch.state ?? current.state,
    position: patch.position ?? current.position
  };
};

export function InspectionPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { message } = App.useApp();
  const preferredRobotId = (location.state as { robotId?: string } | null)?.robotId;
  const [robots, setRobots] = useState<Robot[]>([]);
  const [selectedRobotId, setSelectedRobotId] = useState<string>();
  const [realtime, setRealtime] = useState<RobotRealtime>();
  const [trajectoryPoints, setTrajectoryPoints] = useState<RobotPosition[]>([]);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);

  const selectedRobot = useMemo(
    () => robots.find((robot) => robot.id === selectedRobotId),
    [robots, selectedRobotId]
  );

  const loadRobots = useCallback(async () => {
    setLoading(true);
    try {
      const result = await getRobots({ page: 1, size: 1000 });
      setRobots(result.records);
      setSelectedRobotId((current) =>
        current ?? result.records.find((robot) => robot.id === preferredRobotId)?.id ?? result.records[0]?.id
      );
    } catch {
      message.error("机器人列表加载失败");
    } finally {
      setLoading(false);
    }
  }, [message, preferredRobotId]);

  const loadRealtime = useCallback(async () => {
    if (!selectedRobotId) {
      setRealtime(undefined);
      setTrajectoryPoints([]);
      return;
    }

    setLoading(true);
    try {
      const [result, trajectory] = await Promise.all([
        getRobotRealtime(selectedRobotId),
        getRobotTrajectory(selectedRobotId, TRAJECTORY_POINT_LIMIT).catch(() => [])
      ]);
      setRealtime(result);
      setTrajectoryPoints(trajectory.length > 0 ? trajectory : appendTrajectoryPoint([], currentMapPosition(result)));
    } catch {
      message.error("机器人实时状态加载失败");
    } finally {
      setLoading(false);
    }
  }, [message, selectedRobotId]);

  useEffect(() => {
    void loadRobots();
  }, [loadRobots]);

  useEffect(() => {
    setTrajectoryPoints([]);
    void loadRealtime();
  }, [loadRealtime, selectedRobotId]);

  useEffect(() => {
    if (!selectedRobotId) {
      return;
    }

    let stopped = false;
    let polling = false;

    const refreshTrajectory = async () => {
      if (polling) {
        return;
      }

      polling = true;
      try {
        const trajectory = await getRobotTrajectory(selectedRobotId, TRAJECTORY_POINT_LIMIT);
        if (!stopped && trajectory.length > 0) {
          setTrajectoryPoints((current) => mergeTrajectoryPoints(current, trajectory));
        }
      } catch {
        // WebSocket remains the primary realtime path; polling quietly retries on the next tick.
      } finally {
        polling = false;
      }
    };

    const timer = window.setInterval(() => {
      void refreshTrajectory();
    }, TRAJECTORY_POLL_INTERVAL_MS);

    return () => {
      stopped = true;
      window.clearInterval(timer);
    };
  }, [selectedRobotId]);

  useEffect(() => {
    const token = getAccessToken();
    if (!selectedRobotId || !token) {
      return;
    }

    const wsProtocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const client = new Client({
      brokerURL: `${wsProtocol}//${window.location.host}/ws`,
      connectHeaders: {
        Authorization: `Bearer ${token}`
      },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        client.subscribe(`/topic/robots/${selectedRobotId}/connection`, (frame) => {
          const connection = parseFrame<RobotConnection>(frame);
          if (!connection) {
            return;
          }
          setRealtime((current) => mergeConnection(current, connection));
          setRobots((current) =>
            current.map((robot) =>
              robot.id === selectedRobotId ? { ...robot, online: connection.online } : robot
            )
          );
        });
        client.subscribe(`/topic/robots/${selectedRobotId}/state`, (frame) => {
          const state = parseFrame<RobotState>(frame);
          if (!state) {
            return;
          }
          setRealtime((current) => mergeState(current, state));
        });
        client.subscribe(`/topic/robots/${selectedRobotId}/position`, (frame) => {
          const position = parseFrame<RobotPosition>(frame);
          if (!position) {
            return;
          }
          setRealtime((current) => mergePosition(current, position));
          setTrajectoryPoints((current) => appendTrajectoryPoint(current, position));
        });
        client.subscribe(`/topic/robots/${selectedRobotId}/realtime`, (frame) => {
          const realtimePatch = parseFrame<Partial<RobotRealtime>>(frame);
          if (!realtimePatch) {
            return;
          }
          setRealtime((current) => mergeRealtime(current, realtimePatch));
          setTrajectoryPoints((current) => appendTrajectoryPoint(current, realtimePatch.position));
        });
      }
    });

    client.activate();
    return () => {
      void client.deactivate();
    };
  }, [selectedRobotId]);

  const position = currentMapPosition(realtime);
  const mapProjection = useMemo(
    () => buildMapProjection(trajectoryPoints, position),
    [position, trajectoryPoints]
  );
  const pinStyle = mapProjection.current
    ? {
        left: `${mapProjection.current.screenX}%`,
        top: `${mapProjection.current.screenY}%`
      }
    : undefined;
  const trajectoryPolyline = mapProjection.trajectory
    .map((point) => `${point.screenX},${point.screenY}`)
    .join(" ");

  const handleInstantAction = async (actionType: "stopPause" | "startPause") => {
    if (!selectedRobotId) {
      return;
    }
    setActionLoading(true);
    try {
      await createInstantAction(selectedRobotId, { actionType });
      message.success(actionType === "stopPause" ? "已创建暂停/停止动作" : "已创建继续动作");
    } catch {
      message.error("即时指令创建失败");
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div className="page-stack">
      <div className="page-heading">
        <Typography.Title level={2}>实时巡检</Typography.Title>
        <Typography.Text type="secondary">
          查看机器人连接、位置、电量和任务状态，随现场上报实时刷新。
        </Typography.Text>
      </div>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={7} xl={6}>
          <Card
            title="机器人列表"
            className="fill-card"
            extra={<Button size="small" icon={<ReloadOutlined />} onClick={loadRobots} />}
          >
            <List
              loading={loading}
              dataSource={robots}
              renderItem={(robot) => (
                <List.Item
                  className={robot.id === selectedRobotId ? "robot-list-item active" : "robot-list-item"}
                  onClick={() => setSelectedRobotId(robot.id)}
                >
                  <Space>
                    <span className={robot.online ? "online-dot" : "offline-dot"} />
                    <Typography.Text strong>{robot.serialNumber}</Typography.Text>
                    <Tag color={robot.status === "active" ? "green" : "default"}>{robot.status}</Tag>
                  </Space>
                </List.Item>
              )}
            />
          </Card>
        </Col>
        <Col xs={24} lg={17} xl={18}>
          <Card className="map-card">
            {selectedRobot ? (
              <div className="ops-map">
                <svg className="trajectory-layer" viewBox="0 0 100 100" preserveAspectRatio="none">
                  {trajectoryPolyline ? <polyline className="trajectory-line" points={trajectoryPolyline} /> : null}
                  {mapProjection.trajectory.map(({ point, screenX, screenY }, index) => (
                    <circle
                      className={index === mapProjection.trajectory.length - 1 ? "trajectory-dot current" : "trajectory-dot"}
                      cx={screenX}
                      cy={screenY}
                      r={index === mapProjection.trajectory.length - 1 ? 1.6 : 0.8}
                      key={`${point.time ?? index}-${point.x}-${point.y}`}
                    />
                  ))}
                </svg>
                {pinStyle ? (
                  <div className="robot-pin" style={pinStyle}>
                    {selectedRobot.serialNumber}
                  </div>
                ) : null}
              </div>
            ) : (
              <Empty description="暂无机器人" />
            )}
          </Card>
          <Card className="status-bar">
            <Space wrap size={16}>
              <Tag color={realtime?.connection?.online ? "green" : "default"}>
                {realtime?.connection?.online ? "在线" : "离线"}
              </Tag>
              <Tag color="green">电量 {realtime?.state?.batterySoc ?? "-"}%</Tag>
              <Tag>模式 {realtime?.state?.operatingMode ?? "-"}</Tag>
              <Tag>地图 {position?.mapId ?? "-"}</Tag>
              <Tag color="blue">任务 {realtime?.state?.orderId ?? "-"}</Tag>
              <Tag>更新时间 {formatDateTime(realtime?.position?.time ?? realtime?.state?.time ?? realtime?.connection?.time)}</Tag>
              <Button
                danger
                icon={<PauseCircleOutlined />}
                loading={actionLoading}
                disabled={!selectedRobotId}
                onClick={() => handleInstantAction("stopPause")}
              >
                暂停 / 停止运动
              </Button>
              <Button
                type="primary"
                icon={<PlayCircleOutlined />}
                loading={actionLoading}
                disabled={!selectedRobotId}
                onClick={() => handleInstantAction("startPause")}
              >
                继续
              </Button>
              <Button icon={<AlertOutlined />} onClick={() => navigate("/alerts")}>
                查看告警
              </Button>
            </Space>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
