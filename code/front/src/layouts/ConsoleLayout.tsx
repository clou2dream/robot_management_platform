import {
  AlertOutlined,
  BookOutlined,
  CloudServerOutlined,
  DashboardOutlined,
  DeploymentUnitOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  RobotOutlined,
  SendOutlined
} from "@ant-design/icons";
import {
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
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { logout } from "../api/auth";
import { useAuthStore } from "../stores/authStore";
import { useConsoleStore } from "../stores/consoleStore";

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
  },
  {
    key: "/docs/quickstart",
    icon: <BookOutlined />,
    label: "来源说明"
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

export function ConsoleLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { message } = App.useApp();
  const collapsed = useConsoleStore((state) => state.collapsed);
  const setCollapsed = useConsoleStore((state) => state.setCollapsed);
  const currentUser = useAuthStore((state) => state.currentUser);
  const clearAuth = useAuthStore((state) => state.clearAuth);

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
              VDA5050 / MQTT Console
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
            {!collapsed && (
              <Typography.Text type="secondary">控制台导航</Typography.Text>
            )}
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
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
