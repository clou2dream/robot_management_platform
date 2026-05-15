import { PlusOutlined, RobotOutlined } from "@ant-design/icons";
import {
  App,
  Button,
  Card,
  Drawer,
  Form,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useState } from "react";
import {
  createOperator,
  deleteOperator,
  getOperatorRobots,
  getOperators,
  grantRobotAccess,
  revokeRobotAccess,
  updateOperatorRole
} from "../api/operators";
import { getRobots } from "../api/robots";
import type { UserRole } from "../types/auth";
import type { CreateOperatorRequest, Operator } from "../types/operator";
import type { Robot } from "../types/robot";

const roleOptions: Array<{ label: string; value: UserRole }> = [
  { label: "管理员", value: "admin" },
  { label: "运营员", value: "operator" },
  { label: "只读", value: "viewer" }
];

const roleColorMap: Record<UserRole, string> = {
  admin: "blue",
  operator: "green",
  viewer: "default"
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

export function OperatorsPage() {
  const { message } = App.useApp();
  const [form] = Form.useForm<CreateOperatorRequest>();
  const [operators, setOperators] = useState<Operator[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);

  const [accessOpen, setAccessOpen] = useState(false);
  const [accessOperator, setAccessOperator] = useState<Operator | null>(null);
  const [accessRobots, setAccessRobots] = useState<Robot[]>([]);
  const [allRobots, setAllRobots] = useState<Robot[]>([]);
  const [selectedRobotIds, setSelectedRobotIds] = useState<string[]>([]);
  const [accessLoading, setAccessLoading] = useState(false);

  const loadOperators = useCallback(async () => {
    setLoading(true);
    try {
      const result = await getOperators({ page, size: pageSize });
      setOperators(result.records);
      setTotal(result.total);
    } catch {
      message.error("人员列表加载失败，请确认当前账号是否为 admin");
    } finally {
      setLoading(false);
    }
  }, [message, page, pageSize]);

  useEffect(() => {
    void loadOperators();
  }, [loadOperators]);

  const handleCreate = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      await createOperator(values);
      message.success("运营人员已创建");
      setCreateOpen(false);
      form.resetFields();
      await loadOperators();
    } catch {
      message.error("创建失败，请检查用户名是否已存在");
    } finally {
      setSubmitting(false);
    }
  };

  const handleRoleChange = async (operator: Operator, role: UserRole) => {
    try {
      await updateOperatorRole(operator.id, role);
      message.success("角色已更新");
      await loadOperators();
    } catch {
      message.error("角色更新失败");
    }
  };

  const handleDelete = async (operator: Operator) => {
    try {
      await deleteOperator(operator.id);
      message.success("账号已删除");
      await loadOperators();
    } catch {
      message.error("删除失败，不能删除当前账号或无权限");
    }
  };

  const openAccessDrawer = async (operator: Operator) => {
    setAccessOperator(operator);
    setAccessOpen(true);
    setSelectedRobotIds([]);
    setAccessLoading(true);
    try {
      const [accessList, robotPage] = await Promise.all([
        getOperatorRobots(operator.id),
        getRobots({ page: 1, size: 1000 })
      ]);
      setAccessRobots(accessList);
      setAllRobots(robotPage.records);
    } catch {
      message.error("机器人访问范围加载失败");
    } finally {
      setAccessLoading(false);
    }
  };

  const refreshAccessRobots = async () => {
    if (!accessOperator) {
      return;
    }
    const accessList = await getOperatorRobots(accessOperator.id);
    setAccessRobots(accessList);
  };

  const handleGrantAccess = async () => {
    if (!accessOperator || selectedRobotIds.length === 0) {
      return;
    }
    setAccessLoading(true);
    try {
      await grantRobotAccess(accessOperator.id, selectedRobotIds);
      message.success("机器人访问权限已更新");
      setSelectedRobotIds([]);
      await refreshAccessRobots();
      await loadOperators();
    } catch {
      message.error("授权失败，admin 账号无需单独授权");
    } finally {
      setAccessLoading(false);
    }
  };

  const handleRevokeAccess = async (robotId: string) => {
    if (!accessOperator) {
      return;
    }
    setAccessLoading(true);
    try {
      await revokeRobotAccess(accessOperator.id, robotId);
      message.success("机器人访问权限已撤销");
      await refreshAccessRobots();
      await loadOperators();
    } catch {
      message.error("撤销失败");
    } finally {
      setAccessLoading(false);
    }
  };

  const columns: ColumnsType<Operator> = [
    { title: "用户名", dataIndex: "username" },
    {
      title: "角色",
      dataIndex: "role",
      render: (role: UserRole, operator) => (
        <Select
          size="small"
          value={role}
          style={{ width: 108 }}
          options={roleOptions}
          onChange={(value) => handleRoleChange(operator, value)}
        />
      )
    },
    {
      title: "机器人访问数",
      dataIndex: "robotAccessCount",
      render: (count: number, operator) =>
        operator.role === "admin" ? <Tag color="blue">全部</Tag> : <Tag color="green">{count}</Tag>
    },
    {
      title: "创建时间",
      dataIndex: "createdAt",
      render: formatDateTime
    },
    {
      title: "操作",
      render: (_, operator) => (
        <Space>
          <Button
            type="link"
            icon={<RobotOutlined />}
            disabled={operator.role === "admin"}
            onClick={() => openAccessDrawer(operator)}
          >
            管理授权
          </Button>
          <Popconfirm
            title="删除账号"
            description={`确认删除 ${operator.username}？`}
            onConfirm={() => handleDelete(operator)}
          >
            <Button type="link" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ];

  return (
    <div className="page-stack">
      <div className="page-heading">
        <Typography.Title level={2}>人员管理</Typography.Title>
        <Typography.Text type="secondary">
          管理运营人员账号、角色，以及 operator/viewer 可访问的机器人范围。
        </Typography.Text>
      </div>

      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            新建人员
          </Button>
        </Space>
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={operators}
          pagination={{ current: page, pageSize, total, showSizeChanger: true }}
          onChange={(pagination) => {
            setPage(pagination.current ?? 1);
            setPageSize(pagination.pageSize ?? 20);
          }}
        />
      </Card>

      <Drawer
        title="新建运营人员"
        open={createOpen}
        width={420}
        onClose={() => setCreateOpen(false)}
        extra={
          <Button type="primary" loading={submitting} onClick={handleCreate}>
            创建
          </Button>
        }
      >
        <Form<CreateOperatorRequest>
          form={form}
          layout="vertical"
          initialValues={{ role: "operator" }}
          requiredMark={false}
        >
          <Form.Item label="用户名" name="username" rules={[{ required: true, message: "请输入用户名" }]}>
            <Input placeholder="例如 operator01" />
          </Form.Item>
          <Form.Item label="初始密码" name="password" rules={[{ required: true, message: "请输入初始密码" }]}>
            <Input.Password placeholder="至少 6 位" />
          </Form.Item>
          <Form.Item label="角色" name="role" rules={[{ required: true, message: "请选择角色" }]}>
            <Select options={roleOptions} />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title={`机器人访问授权${accessOperator ? ` - ${accessOperator.username}` : ""}`}
        open={accessOpen}
        width={560}
        onClose={() => setAccessOpen(false)}
      >
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
          {accessOperator && (
            <Tag color={roleColorMap[accessOperator.role]}>
              {accessOperator.role}
            </Tag>
          )}
          <Space.Compact style={{ width: "100%" }}>
            <Select
              mode="multiple"
              placeholder="选择要授权的机器人"
              value={selectedRobotIds}
              onChange={setSelectedRobotIds}
              options={allRobots.map((robot) => ({
                label: `${robot.serialNumber} / ${robot.manufacturer ?? "-"}`,
                value: robot.id
              }))}
              style={{ width: "100%" }}
            />
            <Button type="primary" loading={accessLoading} onClick={handleGrantAccess}>
              授权
            </Button>
          </Space.Compact>

          <Table
            rowKey="id"
            size="small"
            loading={accessLoading}
            pagination={false}
            dataSource={accessRobots}
            columns={[
              { title: "SN", dataIndex: "serialNumber" },
              { title: "厂商", dataIndex: "manufacturer" },
              {
                title: "操作",
                render: (_, robot) => (
                  <Button type="link" danger onClick={() => handleRevokeAccess(robot.id)}>
                    撤销
                  </Button>
                )
              }
            ]}
          />
        </Space>
      </Drawer>
    </div>
  );
}
