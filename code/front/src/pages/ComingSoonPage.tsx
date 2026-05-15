import { Card, Result, Tag } from "antd";

interface ComingSoonPageProps {
  title: string;
  stage: "v1.1" | "后续迭代";
}

export function ComingSoonPage({ title, stage }: ComingSoonPageProps) {
  return (
    <Card>
      <Result
        status="info"
        title={title}
        subTitle={
          <span>
            当前页面属于 <Tag color="blue">{stage}</Tag> 范围，MVP 阶段先保留导航入口。
          </span>
        }
      />
    </Card>
  );
}
