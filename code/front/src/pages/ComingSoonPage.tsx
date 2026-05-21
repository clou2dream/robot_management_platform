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
            该模块归入 <Tag color="blue">{stage}</Tag> 范围。
          </span>
        }
      />
    </Card>
  );
}
