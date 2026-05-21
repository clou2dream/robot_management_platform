import { CheckCircleOutlined, RobotOutlined, SyncOutlined } from "@ant-design/icons";
import { Card, List, Space, Typography } from "antd";

const sourceItems = [
  {
    icon: <CheckCircleOutlined />,
    title: "外部授权平台完成授权",
    description: "机器人首次接入、APP ID、API 凭证和 EMQX 认证由外部授权平台负责。"
  },
  {
    icon: <SyncOutlined />,
    title: "MQTT 接入机器人",
    description: "机器人通过 connection ONLINE 与 state / visualization / factsheet 上报接入，不展示或保存 API Secret。"
  },
  {
    icon: <RobotOutlined />,
    title: "进入机器人列表",
    description: "接入成功后，机器人会出现在列表中，并展示在线状态和最近接收时间。"
  },
  {
    icon: <CheckCircleOutlined />,
    title: "开始巡检与任务",
    description: "运营人员可在本平台查看巡检数据、处理告警并下发 VDA5050 任务。"
  }
];

export function QuickstartPage() {
  return (
    <div className="page-stack">
      <div className="page-heading">
        <Typography.Title level={2}>机器人来源与接入说明</Typography.Title>
        <Typography.Paragraph>
          机器人授权由外部授权平台完成，本平台通过 MQTT 接收已授权机器人用于巡检、任务和告警。
        </Typography.Paragraph>
      </div>

      <Card>
        <List
          dataSource={sourceItems}
          renderItem={(item) => (
            <List.Item>
              <Space align="start" size={12}>
                <span className="source-icon">{item.icon}</span>
                <div>
                  <Typography.Text strong>{item.title}</Typography.Text>
                  <Typography.Paragraph className="source-description">
                    {item.description}
                  </Typography.Paragraph>
                </div>
              </Space>
            </List.Item>
          )}
        />
      </Card>
    </div>
  );
}
