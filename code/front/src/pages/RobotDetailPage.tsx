import {
  ArrowLeftOutlined,
  FileTextOutlined,
  ReloadOutlined,
  ThunderboltOutlined
} from "@ant-design/icons";
import { App, Button, Card, Col, Collapse, Descriptions, Empty, Row, Space, Spin, Tag, Typography } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { getCurrentUser } from "../api/auth";
import { getRobotFactsheet, getRobotRealtime } from "../api/robots";
import { useAuthStore } from "../stores/authStore";
import type { RobotFactsheet, RobotPosition, RobotRealtime } from "../types/robot";

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

const formatNumber = (value?: number) => {
  if (value === undefined || Number.isNaN(value)) {
    return "-";
  }
  return Number(value).toFixed(2);
};

const asRecord = (value: unknown): Record<string, unknown> =>
  value && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : {};

const asNumber = (value: unknown) => {
  if (typeof value === "number") {
    return value;
  }
  if (typeof value === "string" && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  }
  return undefined;
};

const asStringArray = (value: unknown) =>
  Array.isArray(value) ? value.map((item) => String(item)).filter(Boolean) : [];

const unique = (values: string[]) => Array.from(new Set(values.filter(Boolean)));

const currentPosition = (realtime?: RobotRealtime): RobotPosition | undefined =>
  realtime?.position ?? realtime?.state?.position ?? realtime?.state?.agvPosition;

const prettyJson = (value?: Record<string, unknown>) => JSON.stringify(value ?? {}, null, 2);

const factsheetSummary = (factsheet?: RobotFactsheet | null) => {
  const raw = factsheet?.rawPayload ?? {};
  const physical = asRecord(raw.physicalParameters);
  const typeSpecification = asRecord(raw.typeSpecification);
  const protocolFeatures = asRecord(raw.protocolFeatures);
  const actions = Array.isArray(protocolFeatures.mobileRobotActions)
    ? protocolFeatures.mobileRobotActions.map(asRecord)
    : [];

  return {
    version: typeof raw.version === "string" ? raw.version : "-",
    maximumSpeed: asNumber(physical.maximumSpeed),
    width: asNumber(physical.width),
    length: asNumber(physical.length),
    navigationTypes: asStringArray(typeSpecification.navigationTypes),
    localizationTypes: asStringArray(typeSpecification.localizationTypes),
    actionTypes: unique(actions.map((action) => String(action.actionType ?? "")))
  };
};

const renderTags = (values: string[], color = "blue") => {
  if (values.length === 0) {
    return "-";
  }
  return (
    <Space size={[4, 4]} wrap>
      {values.map((value) => (
        <Tag color={color} key={value}>
          {value}
        </Tag>
      ))}
    </Space>
  );
};

export function RobotDetailPage() {
  const navigate = useNavigate();
  const { robotId } = useParams();
  const { message } = App.useApp();
  const currentUser = useAuthStore((state) => state.currentUser);
  const setCurrentUser = useAuthStore((state) => state.setCurrentUser);
  const [realtime, setRealtime] = useState<RobotRealtime>();
  const [factsheet, setFactsheet] = useState<RobotFactsheet | null>(null);
  const [loading, setLoading] = useState(false);

  const position = useMemo(() => currentPosition(realtime), [realtime]);
  const factsheetView = useMemo(() => factsheetSummary(factsheet), [factsheet]);

  const loadDetail = useCallback(async () => {
    if (!robotId) {
      return;
    }
    setLoading(true);
    try {
      const [nextRealtime, nextFactsheet, nextUser] = await Promise.all([
        getRobotRealtime(robotId),
        getRobotFactsheet(robotId).catch(() => null),
        getCurrentUser().catch(() => null)
      ]);
      setRealtime(nextRealtime);
      setFactsheet(nextFactsheet);
      if (nextUser) {
        setCurrentUser(nextUser);
      }
    } catch {
      message.error("机器人详情加载失败");
    } finally {
      setLoading(false);
    }
  }, [message, robotId, setCurrentUser]);

  useEffect(() => {
    void loadDetail();
  }, [loadDetail]);

  const debugPayload = {
    internalRobotId: realtime?.robot.id,
    externalRobotId: realtime?.robot.externalRobotId,
    connectionState: realtime?.connection?.connectionState,
    connectionTime: realtime?.connection?.time,
    topics: realtime?.robot.manufacturer && realtime?.robot.serialNumber
      ? {
          connection: `uagv/v3/${realtime.robot.manufacturer}/${realtime.robot.serialNumber}/connection`,
          state: `uagv/v3/${realtime.robot.manufacturer}/${realtime.robot.serialNumber}/state`,
          visualization: `uagv/v3/${realtime.robot.manufacturer}/${realtime.robot.serialNumber}/visualization`,
          factsheet: `uagv/v3/${realtime.robot.manufacturer}/${realtime.robot.serialNumber}/factsheet`
        }
      : {}
  };

  return (
    <div className="page-stack">
      <div className="page-heading">
        <Space align="start">
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate("/robots/list")}>
            返回
          </Button>
          <div>
            <Typography.Title level={2}>机器人详情</Typography.Title>
            <Typography.Text type="secondary">查看设备身份、接入状态、最近状态摘要和能力摘要。</Typography.Text>
          </div>
        </Space>
      </div>

      <Spin spinning={loading}>
        {realtime?.robot ? (
          <Row gutter={[16, 16]}>
            <Col xs={24} xl={10}>
              <Card
                title="设备身份"
                extra={<Button size="small" icon={<ReloadOutlined />} onClick={loadDetail} />}
              >
                <Descriptions column={1} size="small">
                  <Descriptions.Item label="SN">{realtime.robot.serialNumber}</Descriptions.Item>
                  <Descriptions.Item label="厂商">{realtime.robot.manufacturer ?? "-"}</Descriptions.Item>
                  <Descriptions.Item label="来源 AppID">{realtime.robot.sourceAppId ?? "-"}</Descriptions.Item>
                  <Descriptions.Item label="状态">
                    <Tag color={realtime.robot.status === "active" ? "green" : "default"}>{realtime.robot.status}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="在线状态">
                    <Tag color={realtime.robot.online ? "green" : "default"}>
                      {realtime.robot.online ? "在线" : "离线"}
                    </Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="最近接入">{formatDateTime(realtime.robot.syncedAt)}</Descriptions.Item>
                </Descriptions>
              </Card>

              {currentUser?.debugPermission ? (
                <Card title="调试信息" className="section-card">
                  <Collapse
                    ghost
                    items={[
                      {
                        key: "debug",
                        label: "内部标识与 MQTT Topic",
                        children: <pre className="json-preview">{prettyJson(debugPayload)}</pre>
                      },
                      {
                        key: "state",
                        label: "原始 state",
                        children: <pre className="json-preview">{prettyJson(realtime.state?.rawPayload)}</pre>
                      },
                      {
                        key: "factsheet",
                        label: "原始 factsheet",
                        children: <pre className="json-preview">{prettyJson(factsheet?.rawPayload)}</pre>
                      }
                    ]}
                  />
                </Card>
              ) : null}
            </Col>

            <Col xs={24} xl={14}>
              <Card title="最近状态摘要" extra={<ThunderboltOutlined />}>
                {realtime.state ? (
                  <Descriptions column={2} size="small">
                    <Descriptions.Item label="电量">{realtime.state.batterySoc ?? "-"}%</Descriptions.Item>
                    <Descriptions.Item label="运行模式">{realtime.state.operatingMode ?? "-"}</Descriptions.Item>
                    <Descriptions.Item label="当前任务">{realtime.state.orderId ?? "-"}</Descriptions.Item>
                    <Descriptions.Item label="状态时间">{formatDateTime(realtime.state.time)}</Descriptions.Item>
                    <Descriptions.Item label="地图">{position?.mapId ?? "-"}</Descriptions.Item>
                    <Descriptions.Item label="坐标">
                      X {formatNumber(position?.x)} / Y {formatNumber(position?.y)} / theta {formatNumber(position?.theta)}
                    </Descriptions.Item>
                  </Descriptions>
                ) : (
                  <Empty description="暂无 state 上报" />
                )}
              </Card>

              <Card title="能力摘要" className="section-card" extra={<FileTextOutlined />}>
                {factsheet?.rawPayload ? (
                  <Descriptions column={2} size="small">
                    <Descriptions.Item label="接收时间">{formatDateTime(factsheet.receivedAt)}</Descriptions.Item>
                    <Descriptions.Item label="最大速度">{formatNumber(factsheetView.maximumSpeed)} m/s</Descriptions.Item>
                    <Descriptions.Item label="尺寸">
                      {formatNumber(factsheetView.length)} m x {formatNumber(factsheetView.width)} m
                    </Descriptions.Item>
                    <Descriptions.Item label="支持动作" span={2}>
                      {renderTags(factsheetView.actionTypes, "cyan")}
                    </Descriptions.Item>
                  </Descriptions>
                ) : (
                  <Empty description="暂无能力上报" />
                )}
              </Card>
            </Col>
          </Row>
        ) : (
          <Card>
            <Empty description="暂无机器人详情" />
          </Card>
        )}
      </Spin>
    </div>
  );
}
