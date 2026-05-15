import {
  AlertOutlined,
  BookOutlined,
  CheckCircleOutlined,
  RobotOutlined,
  SendOutlined,
  SyncOutlined
} from "@ant-design/icons";
import { Button, Card, Col, Row, Space, Statistic, Table, Tag, Typography } from "antd";
import { useNavigate } from "react-router-dom";

const alertRows = [
  { key: "1", level: "CRITICAL", robot: "SN-001", type: "CONNECTION_BROKEN" },
  { key: "2", level: "URGENT", robot: "SN-006", type: "LOW_BATTERY" }
];

export function WorkbenchPage() {
  const navigate = useNavigate();

  return (
    <div className="page-stack">
      <section className="workbench-hero">
        <div>
          <Typography.Text className="hero-kicker">Console Overview</Typography.Text>
          <Typography.Title level={2}>工作台</Typography.Title>
          <Typography.Paragraph>
            查看已同步机器人在线态势、未解决告警和近期任务。
          </Typography.Paragraph>
        </div>
        <Space wrap>
          <Button type="primary" icon={<RobotOutlined />} onClick={() => navigate("/robots/inspection")}>
            打开巡检面板
          </Button>
          <Button icon={<SendOutlined />} onClick={() => navigate("/tasks/dispatch")}>
            下发任务
          </Button>
          <Button icon={<BookOutlined />} onClick={() => navigate("/docs/quickstart")}>
            来源说明
          </Button>
        </Space>
      </section>

      <Row gutter={[16, 16]}>
        <Col xs={24} md={12} xl={6}>
          <Card>
            <Statistic title="在线机器人" value={32} suffix="/ 58" prefix={<RobotOutlined />} />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card>
            <Statistic title="未解决告警" value={4} prefix={<AlertOutlined />} valueStyle={{ color: "#e5484d" }} />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card>
            <Statistic title="进行中任务" value={7} prefix={<SendOutlined />} />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card>
            <Statistic title="已同步机器人" value={58} prefix={<SyncOutlined />} />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={14}>
          <Card title="实时告警">
            <Table
              size="small"
              pagination={false}
              dataSource={alertRows}
              columns={[
                {
                  title: "级别",
                  dataIndex: "level",
                  render: (level: string) => <Tag color="red">{level}</Tag>
                },
                { title: "机器人", dataIndex: "robot" },
                { title: "类型", dataIndex: "type" }
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} xl={10}>
          <Card title="机器人来源">
            <div className="quick-steps">
              {["外部授权平台完成授权", "同步已授权机器人", "机器人列表可见", "开始巡检/任务"].map((item) => (
                <div className="quick-step" key={item}>
                  <CheckCircleOutlined />
                  <span>{item}</span>
                </div>
              ))}
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
