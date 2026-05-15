import { CheckCircleOutlined, RobotOutlined, SyncOutlined } from "@ant-design/icons";
import { Card, Steps, Typography } from "antd";

export function QuickstartPage() {
  return (
    <div className="page-stack">
      <div className="page-heading">
        <Typography.Title level={2}>机器人来源与同步说明</Typography.Title>
        <Typography.Text type="secondary">
          机器人授权由外部授权平台完成，本平台只同步已授权机器人用于巡检、任务和告警。
        </Typography.Text>
      </div>

      <Card>
        <Steps
          direction="vertical"
          items={[
            {
              title: "外部授权平台完成授权",
              description: "机器人首次接入、APP ID、API 凭证和 EMQX 认证由外部授权平台负责。",
              icon: <CheckCircleOutlined />
            },
            {
              title: "同步机器人清单",
              description: "本平台通过同步接口获取已授权机器人，不展示或保存 API Secret。",
              icon: <SyncOutlined />
            },
            {
              title: "进入机器人列表",
              description: "同步成功后，机器人会出现在列表中，并展示来源状态、在线状态和最近同步时间。",
              icon: <RobotOutlined />
            },
            {
              title: "开始巡检与任务",
              description: "运营人员可在本平台查看巡检数据、处理告警并下发 VDA5050 任务。",
              icon: <CheckCircleOutlined />
            }
          ]}
        />
      </Card>
    </div>
  );
}
