import {
  AlertOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  ReloadOutlined
} from "@ant-design/icons";
import { App, Button, Card, Col, Empty, List, Row, Space, Tag, Typography } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { createDemoTelemetry, getRobotRealtime, getRobots } from "../api/robots";
import { createInstantAction } from "../api/tasks";
import type { Robot, RobotRealtime } from "../types/robot";

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

const clampPercent = (value?: number, fallback = 50) => {
  if (value === undefined || Number.isNaN(value)) {
    return fallback;
  }
  return Math.max(8, Math.min(92, value));
};

export function InspectionPage() {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [robots, setRobots] = useState<Robot[]>([]);
  const [selectedRobotId, setSelectedRobotId] = useState<string>();
  const [realtime, setRealtime] = useState<RobotRealtime>();
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
      setSelectedRobotId((current) => current ?? result.records[0]?.id);
    } catch {
      message.error("机器人列表加载失败");
    } finally {
      setLoading(false);
    }
  }, [message]);

  const loadRealtime = useCallback(async () => {
    if (!selectedRobotId) {
      setRealtime(undefined);
      return;
    }

    setLoading(true);
    try {
      const result = await getRobotRealtime(selectedRobotId);
      setRealtime(result);
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
    void loadRealtime();
  }, [loadRealtime]);

  const position = realtime?.position ?? realtime?.state?.position;
  const pinStyle = {
    left: `${clampPercent(position?.x)}%`,
    top: `${clampPercent(position?.y)}%`
  };

  const handleCreateDemo = async () => {
    if (!selectedRobotId) {
      return;
    }
    setActionLoading(true);
    try {
      const result = await createDemoTelemetry(selectedRobotId);
      setRealtime(result);
      message.success("已生成演示状态");
    } catch {
      message.error("演示状态生成失败");
    } finally {
      setActionLoading(false);
    }
  };

  const handleInstantAction = async (actionType: "stopPause" | "startPause") => {
    if (!selectedRobotId) {
      return;
    }
    setActionLoading(true);
    try {
      await createInstantAction(selectedRobotId, { actionType });
      message.success(actionType === "stopPause" ? "已创建暂停指令" : "已创建继续指令");
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
          展示机器人最新连接、位置、电量和任务状态，后续接入 WebSocket 后切换为实时推送。
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
              <div className="mock-map">
                <div className="robot-pin" style={pinStyle}>
                  {selectedRobot.serialNumber}
                </div>
                <div className="path-line" />
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
              <Tag>更新时间 {formatDateTime(realtime?.state?.time ?? realtime?.connection?.time)}</Tag>
              <Button
                danger
                icon={<PauseCircleOutlined />}
                loading={actionLoading}
                disabled={!selectedRobotId}
                onClick={() => handleInstantAction("stopPause")}
              >
                暂停 / 停止移动
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
              <Button loading={actionLoading} disabled={!selectedRobotId} onClick={handleCreateDemo}>
                生成演示状态
              </Button>
            </Space>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
