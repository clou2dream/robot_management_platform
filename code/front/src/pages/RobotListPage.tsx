import { App, Button, Card, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getRobots } from "../api/robots";
import type { Robot, RobotSourceStatus } from "../types/robot";

const statusColorMap: Record<RobotSourceStatus, string> = {
  active: "green",
  disabled: "gold",
  revoked: "red"
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

export function RobotListPage() {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [robots, setRobots] = useState<Robot[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);

  const loadRobots = useCallback(async () => {
    setLoading(true);
    try {
      const result = await getRobots({ page, size: pageSize });
      setRobots(result.records);
      setTotal(result.total);
    } catch {
      message.error("机器人列表加载失败，请检查后端服务");
    } finally {
      setLoading(false);
    }
  }, [message, page, pageSize]);

  useEffect(() => {
    void loadRobots();
  }, [loadRobots]);

  const columns: ColumnsType<Robot> = [
    { title: "SN", dataIndex: "serialNumber" },
    {
      title: "厂商",
      dataIndex: "manufacturer",
      render: (manufacturer?: string) => manufacturer || "-"
    },
    {
      title: "状态",
      dataIndex: "status",
      render: (status: RobotSourceStatus) => <Tag color={statusColorMap[status]}>{status}</Tag>
    },
    {
      title: "在线",
      dataIndex: "online",
      render: (online: boolean) => <Tag color={online ? "green" : "default"}>{online ? "在线" : "离线"}</Tag>
    },
    {
      title: "最近接入",
      dataIndex: "syncedAt",
      render: formatDateTime
    },
    {
      title: "操作",
      render: (_, robot) => (
        <Space>
          <Button type="link" onClick={() => navigate(`/robots/${robot.id}`)}>
            详情
          </Button>
          <Button type="link" onClick={() => navigate("/robots/inspection", { state: { robotId: robot.id } })}>
            巡检
          </Button>
          <Button type="link" onClick={() => navigate("/tasks/dispatch", { state: { robotId: robot.id } })}>
            下发任务
          </Button>
        </Space>
      )
    }
  ];

  return (
    <div className="page-stack">
      <div className="page-heading">
        <Typography.Title level={2}>机器人列表</Typography.Title>
        <Typography.Text type="secondary">
          查看机器人基础信息、在线状态和操作入口。
        </Typography.Text>
      </div>
      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Button onClick={loadRobots}>刷新</Button>
        </Space>
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={robots}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true
          }}
          onChange={(pagination) => {
            setPage(pagination.current ?? 1);
            setPageSize(pagination.pageSize ?? 20);
          }}
        />
      </Card>
    </div>
  );
}
