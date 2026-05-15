import { Button, Result } from "antd";
import { useNavigate } from "react-router-dom";

export function NotFoundPage() {
  const navigate = useNavigate();

  return (
    <div className="screen-center">
      <Result
        status="404"
        title="页面不存在"
        subTitle="请检查访问路径，或返回工作台继续操作。"
        extra={
          <Button type="primary" onClick={() => navigate("/workbench")}>
            返回工作台
          </Button>
        }
      />
    </div>
  );
}
