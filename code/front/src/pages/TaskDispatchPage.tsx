import { AimOutlined, DeleteOutlined, PlusOutlined, SendOutlined } from "@ant-design/icons";
import { App, Button, Card, Form, Input, InputNumber, Select, Space, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { AxiosError } from "axios";
import type { MouseEvent } from "react";
import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { createOrder } from "../api/tasks";
import { getRobots } from "../api/robots";
import type { Robot } from "../types/robot";
import type { TaskNode } from "../types/task";

const defaultNodes: TaskNode[] = [
  { nodeId: "node-001", x: 12, y: 24, theta: 0.09, mapId: "map-001" },
  { nodeId: "node-002", x: 56, y: 28, theta: 1.57, mapId: "map-001" }
];

const roundCoordinate = (value: number) => Number(value.toFixed(2));
const roundTheta = (value: number) => Number(value.toFixed(3));

const nextNodeId = (nodes: TaskNode[]) => `node-${String(nodes.length + 1).padStart(3, "0")}`;

const angleBetween = (from?: TaskNode, to?: TaskNode) => {
  if (!from || !to) {
    return undefined;
  }
  return roundTheta(Math.atan2(to.y - from.y, to.x - from.x));
};

const sanitizeNode = (node: TaskNode) => ({
  ...node,
  nodeId: node.nodeId.trim(),
  mapId: node.mapId.trim(),
  theta: node.theta
});

const buildEdges = (nodes: TaskNode[], maximumSpeed: number) =>
  nodes.slice(1).map((node, index) => ({
    edgeId: `${nodes[index].nodeId}-${node.nodeId}`,
    startNodeId: nodes[index].nodeId,
    endNodeId: node.nodeId,
    maximumSpeed,
    orientationType: "TANGENTIAL" as const
  }));

const clampPercent = (value: number) => Math.max(4, Math.min(96, value));

const buildMapProjection = (nodes: TaskNode[]) =>
  nodes.map((node) => ({
    node,
    screenX: clampPercent(node.x),
    screenY: 100 - clampPercent(node.y)
  }));

const errorMessage = (error: unknown) => {
  if (error instanceof AxiosError) {
    const data = error.response?.data as { message?: string; error?: string } | undefined;
    return data?.message ?? data?.error ?? error.message;
  }
  return "任务下发失败";
};

export function TaskDispatchPage() {
  const { message } = App.useApp();
  const location = useLocation();
  const navigate = useNavigate();
  const [form] = Form.useForm<{ robotId: string; maximumSpeed: number }>();
  const preferredRobotId = (location.state as { robotId?: string } | null)?.robotId;
  const [robots, setRobots] = useState<Robot[]>([]);
  const [nodes, setNodes] = useState<TaskNode[]>(defaultNodes);
  const [loading, setLoading] = useState(false);

  const projectedNodes = useMemo(() => buildMapProjection(nodes), [nodes]);
  const pathPoints = projectedNodes.map((point) => `${point.screenX},${point.screenY}`).join(" ");

  useEffect(() => {
    getRobots({ page: 1, size: 1000, status: "active" })
      .then((result) => {
        setRobots(result.records);
        const nextRobotId =
          result.records.find((robot) => robot.id === preferredRobotId)?.id ?? result.records[0]?.id;
        if (nextRobotId) {
          form.setFieldValue("robotId", nextRobotId);
        }
      })
      .catch(() => message.error("机器人列表加载失败"));
  }, [form, message, preferredRobotId]);

  const updateNode = <K extends keyof TaskNode>(index: number, key: K, value: TaskNode[K]) => {
    setNodes((current) =>
      current.map((node, nodeIndex) => (nodeIndex === index ? { ...node, [key]: value } : node))
    );
  };

  const handleAddNode = () => {
    setNodes((current) => {
      const previous = current.at(-1);
      const nextIndex = current.length + 1;
      const nextNode: TaskNode = {
        nodeId: nextNodeId(current),
        x: roundCoordinate(previous ? previous.x + 10 : nextIndex * 10),
        y: roundCoordinate(previous ? previous.y : 10 + nextIndex * 5),
        mapId: previous?.mapId ?? "map-001"
      };

      return current.map((node, index) =>
        index === current.length - 1 && previous
          ? { ...node, theta: angleBetween(previous, nextNode) }
          : node
      ).concat(nextNode);
    });
  };

  const handleMapClick = (event: MouseEvent<HTMLDivElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    const x = roundCoordinate(((event.clientX - rect.left) / rect.width) * 100);
    const y = roundCoordinate((1 - (event.clientY - rect.top) / rect.height) * 100);

    setNodes((current) => {
      const previous = current.at(-1);
      const nextNode: TaskNode = {
        nodeId: nextNodeId(current),
        x,
        y,
        mapId: previous?.mapId ?? "map-001"
      };

      return current.map((node, index) =>
        index === current.length - 1 && previous
          ? { ...node, theta: angleBetween(previous, nextNode) }
          : node
      ).concat(nextNode);
    });
  };

  const handleRemoveNode = (index: number) => {
    setNodes((current) => current.filter((_, nodeIndex) => nodeIndex !== index));
  };

  const handleRecalculateTheta = () => {
    setNodes((current) =>
      current.map((node, index) => ({
        ...node,
        theta: angleBetween(node, current[index + 1]) ?? node.theta
      }))
    );
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    const sanitizedNodes = nodes.map(sanitizeNode);
    if (sanitizedNodes.some((node) => !node.nodeId || !node.mapId)) {
      message.error("节点 ID 和地图 ID 不能为空");
      return;
    }

    setLoading(true);
    try {
      const order = await createOrder(values.robotId, {
        maximumSpeed: values.maximumSpeed,
        nodes: sanitizedNodes,
        edges: buildEdges(sanitizedNodes, values.maximumSpeed)
      });
      message.success(`任务已创建：${order.orderId}`);
      navigate("/tasks/orders", { state: { robotId: values.robotId } });
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setLoading(false);
    }
  };

  const columns: ColumnsType<TaskNode> = [
    {
      title: "节点",
      dataIndex: "nodeId",
      width: 130,
      render: (_, node, index) => (
        <Input value={node.nodeId} onChange={(event) => updateNode(index, "nodeId", event.target.value)} />
      )
    },
    {
      title: "X",
      dataIndex: "x",
      width: 110,
      render: (_, node, index) => (
        <InputNumber
          value={node.x}
          precision={2}
          step={0.1}
          style={{ width: "100%" }}
          onChange={(value) => updateNode(index, "x", Number(value ?? 0))}
        />
      )
    },
    {
      title: "Y",
      dataIndex: "y",
      width: 110,
      render: (_, node, index) => (
        <InputNumber
          value={node.y}
          precision={2}
          step={0.1}
          style={{ width: "100%" }}
          onChange={(value) => updateNode(index, "y", Number(value ?? 0))}
        />
      )
    },
    {
      title: "朝向",
      dataIndex: "theta",
      width: 120,
      render: (_, node, index) => (
        <InputNumber
          value={node.theta}
          precision={3}
          step={0.1}
          placeholder="可空"
          style={{ width: "100%" }}
          onChange={(value) => updateNode(index, "theta", value === null ? undefined : Number(value))}
        />
      )
    },
    {
      title: "地图",
      dataIndex: "mapId",
      width: 130,
      render: (_, node, index) => (
        <Input value={node.mapId} onChange={(event) => updateNode(index, "mapId", event.target.value)} />
      )
    },
    {
      title: "操作",
      width: 72,
      render: (_, __, index) => (
        <Button
          danger
          icon={<DeleteOutlined />}
          disabled={nodes.length <= 1}
          onClick={() => handleRemoveNode(index)}
        />
      )
    }
  ];

  return (
    <div className="page-stack">
      <div className="page-heading">
        <Typography.Title level={2}>任务下发</Typography.Title>
        <Typography.Text type="secondary">
          选择在线机器人，编辑任务节点后下发任务。
        </Typography.Text>
      </div>

      <div className="dispatch-grid">
        <Card title="任务配置">
          <Form form={form} layout="vertical" initialValues={{ maximumSpeed: 1.2 }} requiredMark={false}>
            <Form.Item label="机器人" name="robotId" rules={[{ required: true, message: "请选择机器人" }]}>
              <Select
                placeholder="选择已接入机器人"
                options={robots.map((robot) => ({
                  label: `${robot.serialNumber} / ${robot.manufacturer ?? "-"}`,
                  value: robot.id
                }))}
              />
            </Form.Item>
            <Form.Item label="最大速度" name="maximumSpeed" rules={[{ required: true, message: "请输入最大速度" }]}>
              <InputNumber min={0.1} max={3} step={0.1} addonAfter="m/s" style={{ width: "100%" }} />
            </Form.Item>
            <Space wrap>
              <Button icon={<PlusOutlined />} onClick={handleAddNode}>添加节点</Button>
              <Button icon={<AimOutlined />} onClick={handleRecalculateTheta}>重算朝向</Button>
              <Button type="primary" icon={<SendOutlined />} loading={loading} onClick={handleSubmit}>下发任务</Button>
            </Space>
          </Form>

          <Table
            className="section-table"
            size="small"
            pagination={false}
            dataSource={nodes}
            rowKey="nodeId"
            columns={columns}
            scroll={{ x: 680 }}
          />
        </Card>

        <Card title="地图与路径预览">
          <div className="ops-map task-map editable-task-map" onClick={handleMapClick}>
            <svg className="trajectory-layer" viewBox="0 0 100 100" preserveAspectRatio="none">
              {pathPoints ? <polyline className="trajectory-line task-path-line" points={pathPoints} /> : null}
            </svg>
            {projectedNodes.map(({ node, screenX, screenY }, index) => (
              <div
                className="task-node"
                style={{ left: `${screenX}%`, top: `${screenY}%` }}
                key={`${node.nodeId}-${index}`}
              >
                {index + 1}
              </div>
            ))}
          </div>
        </Card>
      </div>
    </div>
  );
}
