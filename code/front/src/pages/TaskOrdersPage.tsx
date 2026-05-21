import { Client, type IMessage } from "@stomp/stompjs";
import { App, Button, Card, Descriptions, Modal, Select, Space, Table, Tag, Typography } from "antd";
import { AxiosError } from "axios";
import { useCallback, useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import { cancelOrder, getRobotOrder, getRobotOrders } from "../api/tasks";
import { getRobots } from "../api/robots";
import { getAccessToken, useAuthStore } from "../stores/authStore";
import type { Robot } from "../types/robot";
import type { Order, OrderStatus } from "../types/task";

const statusColor: Record<OrderStatus, string> = {
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

const errorMessage = (error: unknown, fallback: string) => {
  if (error instanceof AxiosError) {
    const data = error.response?.data as { message?: string; error?: string } | undefined;
    return data?.message ?? data?.error ?? error.message;
  }
  return fallback;
};

export function TaskOrdersPage() {
  const { message } = App.useApp();
  const location = useLocation();
  const currentUser = useAuthStore((state) => state.currentUser);
  const preferredRobotId = (location.state as { robotId?: string } | null)?.robotId;
  const [robots, setRobots] = useState<Robot[]>([]);
  const [selectedRobotId, setSelectedRobotId] = useState<string>();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [detail, setDetail] = useState<Order>();

  useEffect(() => {
    getRobots({ page: 1, size: 1000 })
      .then((result) => {
        setRobots(result.records);
        setSelectedRobotId(result.records.find((robot) => robot.id === preferredRobotId)?.id ?? result.records[0]?.id);
      })
      .catch(() => message.error("机器人列表加载失败"));
  }, [message, preferredRobotId]);

  const loadOrders = useCallback(async () => {
    if (!selectedRobotId) {
      setOrders([]);
      setTotal(0);
      return;
    }
    setLoading(true);
    try {
      const result = await getRobotOrders(selectedRobotId, { page, size: pageSize });
      setOrders(result.records);
      setTotal(result.total);
    } catch {
      message.error("任务列表加载失败");
    } finally {
      setLoading(false);
    }
  }, [message, page, pageSize, selectedRobotId]);

  useEffect(() => {
    void loadOrders();
  }, [loadOrders]);

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
      onConnect: () => {
        client.subscribe(`/topic/robots/${selectedRobotId}/order-status`, (frame) => {
          const order = parseOrderFrame(frame);
          if (!order) {
            return;
          }
          setOrders((current) => current.map((item) => (item.id === order.id ? order : item)));
        });
      }
    });

    client.activate();
    return () => {
      void client.deactivate();
    };
  }, [selectedRobotId]);

  const handleCancel = async (order: Order) => {
    setLoading(true);
    try {
      await cancelOrder(order.robotId, order.id);
      message.success("任务已取消");
      await loadOrders();
    } catch (error) {
      message.error(errorMessage(error, "任务取消失败"));
    } finally {
      setLoading(false);
    }
  };

  const handleShowDetail = async (order: Order) => {
    try {
      setDetail(await getRobotOrder(order.robotId, order.id));
    } catch {
      message.error("任务详情加载失败");
    }
  };

  return (
    <div className="page-stack">
      <div className="page-heading">
        <Typography.Title level={2}>任务列表</Typography.Title>
      </div>

      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Select
            style={{ width: 280 }}
            placeholder="选择机器人"
            value={selectedRobotId}
            options={robots.map((robot) => ({
              label: `${robot.serialNumber} / ${robot.manufacturer ?? "-"}`,
              value: robot.id
            }))}
            onChange={(value) => {
              setSelectedRobotId(value);
              setPage(1);
            }}
          />
        </Space>
        <Table
          rowKey="id"
          loading={loading}
          dataSource={orders}
          pagination={{ current: page, pageSize, total, showSizeChanger: true }}
          onChange={(pagination) => {
            setPage(pagination.current ?? 1);
            setPageSize(pagination.pageSize ?? 20);
          }}
          columns={[
            { title: "任务 ID", dataIndex: "orderId" },
            { title: "机器人", dataIndex: "robotSn" },
            {
              title: "状态",
              dataIndex: "status",
              render: (status: OrderStatus) => <Tag color={statusColor[status]}>{status}</Tag>
            },
            { title: "创建时间", dataIndex: "createdAt", render: formatDateTime },
            {
              title: "操作",
              render: (_, row) => (
                <Space>
                  <Button type="link" onClick={() => handleShowDetail(row)}>
                    详情
                  </Button>
                  {(row.status === "active" || row.status === "pending") && (
                    <Button type="link" danger onClick={() => handleCancel(row)}>
                      取消
                    </Button>
                  )}
                </Space>
              )
            }
          ]}
        />
      </Card>

      <Modal
        title="任务详情"
        open={Boolean(detail)}
        footer={null}
        onCancel={() => setDetail(undefined)}
        width={820}
      >
        {detail && (
          <Space direction="vertical" size={16} style={{ width: "100%" }}>
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="任务 ID">{detail.orderId}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={statusColor[detail.status]}>{detail.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="机器人">{detail.robotSn ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="记录 ID">{detail.id}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{formatDateTime(detail.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="更新时间">{formatDateTime(detail.updatedAt)}</Descriptions.Item>
            </Descriptions>
            {currentUser?.debugPermission ? (
              <pre className="json-preview">
                {JSON.stringify(detail.payload ?? {}, null, 2)}
              </pre>
            ) : null}
          </Space>
        )}
      </Modal>
    </div>
  );
}

const parseOrderFrame = (frame: IMessage): Order | null => {
  try {
    return JSON.parse(frame.body) as Order;
  } catch {
    return null;
  }
};
