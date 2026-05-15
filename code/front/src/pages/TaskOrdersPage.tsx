import { App, Button, Card, Select, Space, Table, Tag, Typography } from "antd";
import { useCallback, useEffect, useState } from "react";
import { cancelOrder, getRobotOrders } from "../api/tasks";
import { getRobots } from "../api/robots";
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

export function TaskOrdersPage() {
  const { message } = App.useApp();
  const [robots, setRobots] = useState<Robot[]>([]);
  const [selectedRobotId, setSelectedRobotId] = useState<string>();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);

  useEffect(() => {
    getRobots({ page: 1, size: 1000 })
      .then((result) => {
        setRobots(result.records);
        setSelectedRobotId(result.records[0]?.id);
      })
      .catch(() => message.error("机器人列表加载失败"));
  }, [message]);

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

  const handleCancel = async (order: Order) => {
    setLoading(true);
    try {
      await cancelOrder(order.robotId, order.id);
      message.success("任务已取消");
      await loadOrders();
    } catch {
      message.error("取消失败，仅 pending/active 任务可取消");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page-stack">
      <div className="page-heading">
        <Typography.Title level={2}>任务列表</Typography.Title>
        <Typography.Text type="secondary">
          通过 WebSocket 订阅任务状态变更，实时更新执行进度。
        </Typography.Text>
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
                  <Button type="link">详情</Button>
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
    </div>
  );
}
