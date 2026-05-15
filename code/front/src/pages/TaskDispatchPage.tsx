import { PlusOutlined, SendOutlined } from "@ant-design/icons";
import { App, Button, Card, Form, InputNumber, Select, Space, Steps, Table, Typography } from "antd";
import { useEffect, useState } from "react";
import { createOrder } from "../api/tasks";
import { getRobots } from "../api/robots";
import type { Robot } from "../types/robot";
import type { TaskNode } from "../types/task";

const defaultNodes: TaskNode[] = [
  { nodeId: "node-001", x: 1.2, y: 2.4, theta: 0 },
  { nodeId: "node-002", x: 5.6, y: 2.8, theta: 1.57 }
];

export function TaskDispatchPage() {
  const { message } = App.useApp();
  const [form] = Form.useForm<{ robotId: string; maxSpeed: number }>();
  const [robots, setRobots] = useState<Robot[]>([]);
  const [nodes, setNodes] = useState<TaskNode[]>(defaultNodes);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    getRobots({ page: 1, size: 1000, status: "active" })
      .then((result) => setRobots(result.records))
      .catch(() => message.error("机器人列表加载失败"));
  }, [message]);

  const handleAddNode = () => {
    const nextIndex = nodes.length + 1;
    setNodes([
      ...nodes,
      {
        nodeId: `node-${String(nextIndex).padStart(3, "0")}`,
        x: Number((nextIndex * 2.1).toFixed(2)),
        y: Number((2 + nextIndex * 0.4).toFixed(2)),
        theta: 0
      }
    ]);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    setLoading(true);
    try {
      const order = await createOrder(values.robotId, {
        maxSpeed: values.maxSpeed,
        nodes
      });
      message.success(`任务已创建：${order.orderId}`);
    } catch {
      message.error("任务下发失败，请确认机器人状态和访问权限");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page-stack">
      <div className="page-heading">
        <Typography.Title level={2}>任务下发</Typography.Title>
        <Typography.Text type="secondary">
          MVP 阶段提供任务配置框架和报文预览区，后续接入 Konva 地图选点。
        </Typography.Text>
      </div>

      <Card>
        <Steps
          current={1}
          items={[
            { title: "选择机器人" },
            { title: "地图选点" },
            { title: "配置动作" },
            { title: "预览报文" },
            { title: "下发任务" }
          ]}
        />
      </Card>

      <div className="dispatch-grid">
        <Card title="任务配置">
          <Form form={form} layout="vertical" initialValues={{ maxSpeed: 1.2 }} requiredMark={false}>
            <Form.Item label="机器人" name="robotId" rules={[{ required: true, message: "请选择机器人" }]}>
              <Select
                placeholder="选择已同步机器人"
                options={robots.map((robot) => ({
                  label: `${robot.serialNumber} / ${robot.manufacturer ?? "-"}`,
                  value: robot.id
                }))}
              />
            </Form.Item>
            <Form.Item label="最大速度" name="maxSpeed" rules={[{ required: true, message: "请输入最大速度" }]}>
              <InputNumber min={0.1} max={3} step={0.1} addonAfter="m/s" style={{ width: "100%" }} />
            </Form.Item>
            <Space>
              <Button icon={<PlusOutlined />} onClick={handleAddNode}>添加节点</Button>
              <Button type="primary" icon={<SendOutlined />} loading={loading} onClick={handleSubmit}>下发任务</Button>
            </Space>
          </Form>

          <Table
            className="section-table"
            size="small"
            pagination={false}
            dataSource={nodes}
            rowKey="nodeId"
            columns={[
              { title: "节点", dataIndex: "nodeId" },
              { title: "X", dataIndex: "x" },
              { title: "Y", dataIndex: "y" },
              { title: "朝向", dataIndex: "theta" }
            ]}
          />
        </Card>

        <Card title="地图与路径预览">
          <div className="mock-map task-map">
            <div className="task-node node-a">1</div>
            <div className="task-node node-b">2</div>
            <div className="path-line" />
          </div>
        </Card>
      </div>
    </div>
  );
}
