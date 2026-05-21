import {
  AlertOutlined,
  CloudServerOutlined,
  DashboardOutlined,
  DeploymentUnitOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  RobotOutlined,
  SendOutlined,
  SettingOutlined,
  UserOutlined
} from "@ant-design/icons";
import {
  Alert,
  App,
  Avatar,
  Badge,
  Button,
  Dropdown,
  Layout,
  Menu,
  Space,
  Typography,
  type MenuProps
} from "antd";
import { useCallback, useEffect, useState } from "react";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { getOpenPlatformCredentialStatus } from "../api/account";
import { logout } from "../api/auth";
import { useAuthStore } from "../stores/authStore";
import { useConsoleStore } from "../stores/consoleStore";
import type { OpenPlatformCredentialStatus } from "../types/openPlatform";

const { Header, Sider, Content } = Layout;

const menuItems: MenuProps["items"] = [
  {
    key: "/workbench",
    icon: <DashboardOutlined />,
    label: "工作台"
  },
  {
    key: "robots",
    icon: <RobotOutlined />,
    label: "机器人服务",
    children: [
      { key: "/robots/inspection", label: "实时巡检" },
      { key: "/robots/list", label: "机器人列表" }
    ]
  },
  {
    key: "tasks",
    icon: <SendOutlined />,
    label: "任务编排",
    children: [
      { key: "/tasks/dispatch", label: "任务下发" },
      { key: "/tasks/orders", label: "任务列表" }
    ]
  },
  {
    key: "/alerts",
    icon: <AlertOutlined />,
    label: "告警运维"
  },
  {
    key: "/operators",
    icon: <DeploymentUnitOutlined />,
    label: "权限管理"
  }
];

const getSelectedKey = (pathname: string) => {
  if (pathname === "/") {
    return "/workbench";
  }

  return pathname;
};

const getOpenKeys = (pathname: string) => {
  if (pathname.startsWith("/robots")) {
    return ["robots"];
  }
  if (pathname.startsWith("/tasks")) {
    return ["tasks"];
  }
  return [];
};

const CREDENTIAL_CHANGED_EVENT = "open-platform-credentials-changed";

export function ConsoleLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { message } = App.useApp();
  const collapsed = useConsoleStore((state) => state.collapsed);
  const setCollapsed = useConsoleStore((state) => state.setCollapsed);
  const currentUser = useAuthStore((state) => state.currentUser);
  const clearAuth = useAuthStore((state) => state.clearAuth);
  const [credentialStatus, setCredentialStatus] = useState<OpenPlatformCredentialStatus | null>(null);

  const loadCredentialStatus = useCallback(async () => {
    if (!currentUser) {
      setCredentialStatus(null);
      return;
    }

    try {
      setCredentialStatus(await getOpenPlatformCredentialStatus());
    } catch {
      setCredentialStatus(null);
    }
  }, [currentUser]);

  useEffect(() => {
    void loadCredentialStatus();

    window.addEventListener(CREDENTIAL_CHANGED_EVENT, loadCredentialStatus);
    return () => {
      window.removeEventListener(CREDENTIAL_CHANGED_EVENT, loadCredentialStatus);
    };
  }, [loadCredentialStatus]);

  const handleLogout = async () => {
    try {
      await logout();
      message.success("已退出登录");
    } finally {
      clearAuth();
      navigate("/login", { replace: true });
    }
  };

  return (
    <Layout className="console-shell">
      <Header className="console-header">
        <div className="brand">
          <div className="brand-mark">
            <CloudServerOutlined />
          </div>
          <div>
            <Typography.Text className="brand-title">
              机器人运营管理平台
            </Typography.Text>
            <Typography.Text className="brand-subtitle">
              运营控制台
            </Typography.Text>
          </div>
        </div>

        <Space size={20}>
          <Badge dot offset={[-2, 4]}>
            <Button type="text" icon={<AlertOutlined />} onClick={() => navigate("/alerts")}>
              告警
            </Button>
          </Badge>
          <Typography.Text type="secondary">
            {currentUser?.tenantName ?? "默认租户"}
          </Typography.Text>
          <Dropdown
            menu={{
              items: [
                {
                  key: "profile",
                  icon: <UserOutlined />,
                  label: "用户信息",
                  onClick: () => navigate("/account/profile")
                },
                {
                  key: "logout",
                  icon: <LogoutOutlined />,
                  label: "退出登录",
                  onClick: handleLogout
                }
              ]
            }}
          >
            <Space className="user-entry">
              <Avatar size="small">
                {(currentUser?.username ?? "U").slice(0, 1).toUpperCase()}
              </Avatar>
              <span>{currentUser?.username ?? "用户"}</span>
            </Space>
          </Dropdown>
        </Space>
      </Header>

      <Layout>
        <Sider
          width={232}
          collapsedWidth={72}
          collapsible
          collapsed={collapsed}
          trigger={null}
          className="console-sider"
        >
          <div className="sider-toolbar">
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed(!collapsed)}
            />
          </div>
          <Menu
            mode="inline"
            selectedKeys={[getSelectedKey(location.pathname)]}
            defaultOpenKeys={getOpenKeys(location.pathname)}
            items={menuItems}
            onClick={({ key }) => {
              if (String(key).startsWith("/")) {
                navigate(String(key));
              }
            }}
          />
        </Sider>

        <Content className="console-content">
          {credentialStatus && !credentialStatus.bound && (
            <Alert
              className="credential-banner"
              type="warning"
              showIcon
              message="当前账号未绑定开放平台凭证"
              action={
                <Button
                  size="small"
                  type="primary"
                  icon={<SettingOutlined />}
                  onClick={() => navigate("/account/profile")}
                >
                  去设置
                </Button>
              }
            />
          )}
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
