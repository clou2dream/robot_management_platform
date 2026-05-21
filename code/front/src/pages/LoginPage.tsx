import { LockOutlined, UserOutlined } from "@ant-design/icons";
import { App, Button, Card, Form, Input, Space, Typography } from "antd";
import { useLocation, useNavigate } from "react-router-dom";
import { login } from "../api/auth";
import { useAuthStore } from "../stores/authStore";
import type { LoginRequest } from "../types/auth";

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { message } = App.useApp();
  const setSession = useAuthStore((state) => state.setSession);
  const from = (location.state as { from?: Location } | null)?.from?.pathname;

  const handleSubmit = async (values: LoginRequest) => {
    try {
      const session = await login(values);
      setSession(session.accessToken, session.currentUser);
      message.success("登录成功");
      navigate(from ?? "/workbench", { replace: true });
    } catch {
      message.error("账号或密码错误，请重新输入");
    }
  };

  return (
    <main className="login-page">
      <section className="login-hero">
        <Typography.Title level={1}>机器人运营管理平台</Typography.Title>
        <Typography.Paragraph>
          查看机器人状态、下发任务并处理告警。
        </Typography.Paragraph>
        <div className="hero-tags">
          <span>机器人接入</span>
          <span>实时巡检</span>
          <span>告警处理</span>
          <span>任务可追溯</span>
        </div>
      </section>

      <Card className="login-card">
        <Space direction="vertical" size={6} className="login-heading">
          <Typography.Title level={3}>登录控制台</Typography.Title>
        </Space>

        <Form<LoginRequest>
          layout="vertical"
          size="large"
          requiredMark={false}
          onFinish={handleSubmit}
        >
          <Form.Item
            label="用户名"
            name="username"
            rules={[{ required: true, message: "请输入用户名" }]}
          >
            <Input prefix={<UserOutlined />} placeholder="请输入用户名" />
          </Form.Item>
          <Form.Item
            label="密码"
            name="password"
            rules={[{ required: true, message: "请输入密码" }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="请输入密码" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>
            登录
          </Button>
        </Form>
      </Card>
    </main>
  );
}
