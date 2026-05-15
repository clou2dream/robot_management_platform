import { BookOutlined, SyncOutlined } from "@ant-design/icons";
import { App, Button, Card, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getRobots, syncRobots } from "../api/robots";
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
  const [syncing, setSyncing] = useState(false);
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

  const handleSync = async () => {
    setSyncing(true);
    try {
      const result = await syncRobots();
      message.success(`同步完成：新增 ${result.created}，更新 ${result.updated}，跳过 ${result.skipped}`);
      if (page !== 1) {
        setPage(1);
      } else {
        await loadRobots();
      }
    } catch {
      message.error("同步失败，请检查后端服务或外部授权平台配置");
    } finally {
      setSyncing(false);
    }
  };

  const columns: ColumnsType<Robot> = [
    { title: "SN", dataIndex: "serialNumber" },
    { title: "外部机器人 ID", dataIndex: "externalRobotId" },
    {
      title: "厂商",
      dataIndex: "manufacturer",
      render: (manufacturer?: string) => manufacturer || "-"
    },
    {
      title: "来源状态",
      dataIndex: "status",
      render: (status: RobotSourceStatus) => <Tag color={statusColorMap[status]}>{status}</Tag>
    },
    {
      title: "在线",
      dataIndex: "online",
      render: (online: boolean) => <Tag color={online ? "green" : "default"}>{online ? "在线" : "离线"}</Tag>
    },
    {
      title: "最近同步",
      dataIndex: "syncedAt",
      render: formatDateTime
    },
    {
      title: "操作",
      render: (_, robot) => (
        <Space>
          <Button type="link" disabled>
            详情
          </Button>
          <Button type="link" onClick={() => navigate("/robots/inspection")}>
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
          展示从外部授权平台同步而来的机器人、来源状态、在线状态和基础信息。
        </Typography.Text>
      </div>
      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Button type="primary" icon={<SyncOutlined />} loading={syncing} onClick={handleSync}>
            同步机器人
          </Button>
          <Button icon={<BookOutlined />} onClick={() => navigate("/docs/quickstart")}>
            来源说明
          </Button>
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
