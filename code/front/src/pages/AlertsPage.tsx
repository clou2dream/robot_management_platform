import {
  AlertOutlined,
  CheckCircleOutlined,
  ReloadOutlined,
  ThunderboltOutlined
} from "@ant-design/icons";
import { Client, type IMessage } from "@stomp/stompjs";
import {
  App,
  Button,
  Card,
  Col,
  Descriptions,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useState } from "react";
import {
  getAlert,
  getAlerts,
  getAlertSummary,
  resolveAlert
} from "../api/alerts";
import { getRobots } from "../api/robots";
import { getAccessToken } from "../stores/authStore";
import type { AlertLevel, AlertRecord, AlertStatus, AlertSummary } from "../types/alert";
import type { Robot } from "../types/robot";

const levelColor: Record<AlertLevel, string> = {
  WARNING: "gold",
  URGENT: "orange",
  CRITICAL: "volcano",
  FATAL: "red"
};

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

export function AlertsPage() {
  const { message } = App.useApp();
  const [alerts, setAlerts] = useState<AlertRecord[]>([]);
  const [summary, setSummary] = useState<AlertSummary>({
    openTotal: 0,
    resolvedTotal: 0,
    warning: 0,
    urgent: 0,
    critical: 0,
    fatal: 0
  });
  const [robots, setRobots] = useState<Robot[]>([]);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [detail, setDetail] = useState<AlertRecord>();
  const [status, setStatus] = useState<AlertStatus | undefined>("open");
  const [level, setLevel] = useState<AlertLevel>();
  const [robotId, setRobotId] = useState<string>();
  const [keyword, setKeyword] = useState("");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);

  const loadAlerts = useCallback(async () => {
    setLoading(true);
    try {
      const [alertResult, summaryResult] = await Promise.all([
        getAlerts({
          page,
          size: pageSize,
          status,
          level,
          robotId,
          keyword: keyword || undefined
        }),
        getAlertSummary()
      ]);
      setAlerts(alertResult.records);
      setTotal(alertResult.total);
      setSummary(summaryResult);
    } catch {
      message.error("告警数据加载失败，请检查后端服务");
    } finally {
      setLoading(false);
    }
  }, [keyword, level, message, page, pageSize, robotId, status]);

  useEffect(() => {
    getRobots({ page: 1, size: 1000 })
      .then((result) => setRobots(result.records))
      .catch(() => message.error("机器人列表加载失败"));
  }, [message]);

  useEffect(() => {
    void loadAlerts();
  }, [loadAlerts]);

  useEffect(() => {
    const token = getAccessToken();
    if (!token) {
      return;
    }

    const wsProtocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const client = new Client({
      brokerURL: `${wsProtocol}//${window.location.host}/ws`,
      connectHeaders: {
        Authorization: `Bearer ${token}`
      },
      reconnectDelay: 3000,
      onConnect: () => {
        client.subscribe("/topic/alerts", (frame) => {
          const alert = parseAlertFrame(frame);
          if (!alert) {
            return;
          }
          setAlerts((current) => {
            const rest = current.filter((item) => item.id !== alert.id);
            if (status === "open" && alert.status !== "open") {
              return rest;
            }
            if (level && alert.level !== level) {
              return rest;
            }
            if (robotId && alert.robotId !== robotId) {
              return rest;
            }
            return [alert, ...rest].slice(0, pageSize);
          });
          void getAlertSummary().then(setSummary).catch(() => undefined);
        });
      }
    });

    client.activate();
    return () => {
      void client.deactivate();
    };
  }, [level, pageSize, robotId, status]);

  const handleResolve = async (record: AlertRecord) => {
    setActionLoading(true);
    try {
      await resolveAlert(record.id);
      message.success("告警已处理");
      await loadAlerts();
    } catch {
      message.error("告警处理失败，viewer 账号没有处理权限");
    } finally {
      setActionLoading(false);
    }
  };

  const handleShowDetail = async (record: AlertRecord) => {
    try {
      const result = await getAlert(record.id);
      setDetail(result);
    } catch {
      message.error("告警详情加载失败");
    }
  };

  const columns: ColumnsType<AlertRecord> = [
    {
      title: "等级",
      dataIndex: "level",
      width: 110,
      render: (value: AlertLevel) => <Tag color={levelColor[value]}>{value}</Tag>
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 100,
      render: (value: AlertStatus) => (
        <Tag color={value === "open" ? "red" : "green"}>
          {value === "open" ? "未处理" : "已处理"}
        </Tag>
      )
    },
    {
      title: "机器人",
      render: (_, record) => record.robotSn || record.manufacturer || "租户级告警"
    },
    { title: "错误类型", dataIndex: "errorType" },
    {
      title: "描述",
      dataIndex: "description",
      ellipsis: true,
      render: (value?: string) => value || "-"
    },
    {
      title: "触发时间",
      dataIndex: "triggeredAt",
      width: 180,
      render: formatDateTime
    },
    {
      title: "操作",
      width: 160,
      render: (_, record) => (
        <Space>
          <Button type="link" onClick={() => handleShowDetail(record)}>
            详情
          </Button>
          {record.status === "open" && (
            <Button type="link" loading={actionLoading} onClick={() => handleResolve(record)}>
              处理
            </Button>
          )}
        </Space>
      )
    }
  ];

  return (
    <div className="page-stack">
      <div className="page-heading">
        <Typography.Title level={2}>告警中心</Typography.Title>
        <Typography.Text type="secondary">
          汇总机器人运行告警，按等级、状态和机器人定位异常并完成处理。
        </Typography.Text>
      </div>

      <Row gutter={16}>
        <Col span={6}>
          <Card>
            <Statistic title="未处理告警" value={summary.openTotal} prefix={<AlertOutlined />} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="严重 / 致命" value={summary.critical + summary.fatal} prefix={<ThunderboltOutlined />} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="预警 / 紧急" value={summary.warning + summary.urgent} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="已处理" value={summary.resolvedTotal} prefix={<CheckCircleOutlined />} />
          </Card>
        </Col>
      </Row>

      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <Select
            allowClear
            style={{ width: 140 }}
            placeholder="处理状态"
            value={status}
            options={[
              { label: "未处理", value: "open" },
              { label: "已处理", value: "resolved" }
            ]}
            onChange={(value) => {
              setStatus(value);
              setPage(1);
            }}
          />
          <Select
            allowClear
            style={{ width: 150 }}
            placeholder="告警等级"
            value={level}
            options={[
              { label: "WARNING", value: "WARNING" },
              { label: "URGENT", value: "URGENT" },
              { label: "CRITICAL", value: "CRITICAL" },
              { label: "FATAL", value: "FATAL" }
            ]}
            onChange={(value) => {
              setLevel(value);
              setPage(1);
            }}
          />
          <Select
            allowClear
            showSearch
            style={{ width: 260 }}
            placeholder="机器人"
            value={robotId}
            optionFilterProp="label"
            options={robots.map((robot) => ({
              label: `${robot.serialNumber} / ${robot.manufacturer ?? "-"}`,
              value: robot.id
            }))}
            onChange={(value) => {
              setRobotId(value);
              setPage(1);
            }}
          />
          <Input.Search
            allowClear
            style={{ width: 280 }}
            placeholder="搜索错误类型、描述、建议"
            onSearch={(value) => {
              setKeyword(value.trim());
              setPage(1);
            }}
          />
          <Button icon={<ReloadOutlined />} onClick={loadAlerts}>
            刷新
          </Button>
        </Space>

        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={alerts}
          pagination={{ current: page, pageSize, total, showSizeChanger: true }}
          onChange={(pagination) => {
            setPage(pagination.current ?? 1);
            setPageSize(pagination.pageSize ?? 20);
          }}
        />
      </Card>

      <Modal
        title="告警详情"
        open={Boolean(detail)}
        footer={null}
        onCancel={() => setDetail(undefined)}
        width={720}
      >
        {detail && (
          <Space direction="vertical" size={16} style={{ width: "100%" }}>
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="等级">
                <Tag color={levelColor[detail.level]}>{detail.level}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                {detail.status === "open" ? "未处理" : "已处理"}
              </Descriptions.Item>
              <Descriptions.Item label="机器人">
                {detail.robotSn || "租户级告警"}
              </Descriptions.Item>
              <Descriptions.Item label="错误类型">
                {detail.errorType}
              </Descriptions.Item>
              <Descriptions.Item label="触发时间">
                {formatDateTime(detail.triggeredAt)}
              </Descriptions.Item>
              <Descriptions.Item label="处理时间">
                {formatDateTime(detail.resolvedAt)}
              </Descriptions.Item>
              <Descriptions.Item label="描述" span={2}>
                {detail.description || "-"}
              </Descriptions.Item>
              <Descriptions.Item label="建议" span={2}>
                {detail.hint || "-"}
              </Descriptions.Item>
            </Descriptions>
          </Space>
        )}
      </Modal>
    </div>
  );
}

const parseAlertFrame = (frame: IMessage): AlertRecord | null => {
  try {
    return JSON.parse(frame.body) as AlertRecord;
  } catch {
    return null;
  }
};
