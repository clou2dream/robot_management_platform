import {
  AlertOutlined,
  ReloadOutlined,
  RobotOutlined,
  SendOutlined
} from "@ant-design/icons";
import { App, Button, Card, Col, Empty, Row, Space, Spin, Statistic, Table, Tag, Typography } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getAlertSummary, getAlerts } from "../api/alerts";
import { getRobots } from "../api/robots";
import { getRobotOrders } from "../api/tasks";
import type { AlertLevel, AlertRecord } from "../types/alert";
import type { Robot } from "../types/robot";
import type { Order, OrderStatus } from "../types/task";

const alertLevelColor: Record<AlertLevel, string> = {
  WARNING: "gold",
  URGENT: "orange",
  CRITICAL: "volcano",
  FATAL: "red"
};

const orderStatusColor: Record<OrderStatus, string> = {
  pending: "blue",
  active: "green",
  completed: "default",
  failed: "red",
  cancelled: "orange"
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

export function WorkbenchPage() {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [robots, setRobots] = useState<Robot[]>([]);
  const [alerts, setAlerts] = useState<AlertRecord[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [alertSummary, setAlertSummary] = useState({
    openTotal: 0,
    resolvedTotal: 0,
    warning: 0,
    urgent: 0,
    critical: 0,
    fatal: 0
  });
  const [loading, setLoading] = useState(false);

  const loadWorkbench = useCallback(async () => {
    setLoading(true);
    try {
      const [robotResult, alertResult, summaryResult] = await Promise.all([
        getRobots({ page: 1, size: 1000 }),
        getAlerts({ page: 1, size: 5, status: "open" }),
        getAlertSummary()
      ]);

      setRobots(robotResult.records);
      setAlerts(alertResult.records);
      setAlertSummary(summaryResult);

      const orderPages = await Promise.all(
        robotResult.records
          .filter((robot) => robot.status === "active")
          .slice(0, 20)
          .map((robot) => getRobotOrders(robot.id, { page: 1, size: 5 }))
      );
      setOrders(
        orderPages
          .flatMap((page) => page.records)
          .sort((left, right) => {
            const leftTime = left.createdAt ? new Date(left.createdAt).getTime() : 0;
            const rightTime = right.createdAt ? new Date(right.createdAt).getTime() : 0;
            return rightTime - leftTime;
          })
          .slice(0, 5)
      );
    } catch {
      message.error("工作台数据加载失败");
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void loadWorkbench();
  }, [loadWorkbench]);

  const activeOrders = useMemo(
    () => orders.filter((order) => order.status === "pending" || order.status === "active").length,
    [orders]
  );
  const onlineRobots = robots.filter((robot) => robot.online).length;

  return (
    <div className="page-stack">
      <section className="workbench-hero">
        <div>
          <Typography.Title level={2}>工作台</Typography.Title>
          <Typography.Paragraph>
            查看已接入机器人在线态势、未解决告警和近期任务。
          </Typography.Paragraph>
        </div>
        <Space wrap>
          <Button type="primary" icon={<RobotOutlined />} onClick={() => navigate("/robots/inspection")}>
            打开巡检面板
          </Button>
          <Button icon={<SendOutlined />} onClick={() => navigate("/tasks/dispatch")}>
            下发任务
          </Button>
          <Button icon={<ReloadOutlined />} loading={loading} onClick={loadWorkbench}>
            刷新
          </Button>
        </Space>
      </section>

      <Spin spinning={loading}>
        <Row gutter={[16, 16]}>
          <Col xs={24} md={12} xl={8}>
            <Card>
              <Statistic title="在线机器人" value={onlineRobots} suffix={`/ ${robots.length}`} prefix={<RobotOutlined />} />
            </Card>
          </Col>
          <Col xs={24} md={12} xl={8}>
            <Card>
              <Statistic
                title="未解决告警"
                value={alertSummary.openTotal}
                prefix={<AlertOutlined />}
                valueStyle={{ color: alertSummary.openTotal > 0 ? "#e5484d" : undefined }}
              />
            </Card>
          </Col>
          <Col xs={24} md={12} xl={8}>
            <Card>
              <Statistic title="待执行任务" value={activeOrders} prefix={<SendOutlined />} />
            </Card>
          </Col>
        </Row>

        <Row gutter={[16, 16]}>
          <Col xs={24} xl={14}>
            <Card title="实时告警">
              <Table
                size="small"
                pagination={false}
                dataSource={alerts}
                rowKey="id"
                locale={{ emptyText: <Empty description="暂无未处理告警" /> }}
                columns={[
                  {
                    title: "级别",
                    dataIndex: "level",
                    render: (level: AlertLevel) => <Tag color={alertLevelColor[level]}>{level}</Tag>
                  },
                  { title: "机器人", render: (_, record) => record.robotSn || "租户级告警" },
                  { title: "类型", dataIndex: "errorType" },
                  { title: "触发时间", dataIndex: "triggeredAt", render: formatDateTime }
                ]}
              />
            </Card>
          </Col>
          <Col xs={24} xl={10}>
            <Card title="近期任务">
              <Table
                size="small"
                pagination={false}
                dataSource={orders}
                rowKey="id"
                locale={{ emptyText: <Empty description="暂无任务" /> }}
                columns={[
                  { title: "机器人", dataIndex: "robotSn" },
                  {
                    title: "状态",
                    dataIndex: "status",
                    render: (status: OrderStatus) => <Tag color={orderStatusColor[status]}>{status}</Tag>
                  },
                  { title: "创建时间", dataIndex: "createdAt", render: formatDateTime }
                ]}
              />
            </Card>
          </Col>
        </Row>
      </Spin>
    </div>
  );
}
