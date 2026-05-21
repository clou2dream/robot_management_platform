import { DeleteOutlined, PlusOutlined } from "@ant-design/icons";
import {
  App,
  Button,
  Card,
  Drawer,
  Form,
  Input,
  Popconfirm,
  Space,
  Table,
  Typography
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useState } from "react";
import {
  createOpenPlatformCredential,
  deleteOpenPlatformCredential,
  getOpenPlatformCredentials
} from "../api/account";
import type {
  OpenPlatformCredential,
  SaveOpenPlatformCredentialRequest
} from "../types/openPlatform";

const CREDENTIAL_CHANGED_EVENT = "open-platform-credentials-changed";

const formatDateTime = (value?: string) => {
  if (!value) {
    return "-";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleString("zh-CN", { hour12: false });
};

export function AccountProfilePage() {
  const { message } = App.useApp();
  const [form] = Form.useForm<SaveOpenPlatformCredentialRequest>();
  const [credentials, setCredentials] = useState<OpenPlatformCredential[]>([]);
  const [loading, setLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const loadCredentials = useCallback(async () => {
    setLoading(true);
    try {
      setCredentials(await getOpenPlatformCredentials());
    } catch {
      message.error("开放平台凭证加载失败");
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void loadCredentials();
  }, [loadCredentials]);

  const emitCredentialChanged = () => {
    window.dispatchEvent(new Event(CREDENTIAL_CHANGED_EVENT));
  };

  const handleCreate = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      await createOpenPlatformCredential(values);
      message.success("开放平台凭证已保存");
      setDrawerOpen(false);
      form.resetFields();
      emitCredentialChanged();
      await loadCredentials();
    } catch {
      message.error("保存失败，请检查 appid 和 apikey 是否重复");
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (credentialId: string) => {
    setLoading(true);
    try {
      await deleteOpenPlatformCredential(credentialId);
      message.success("开放平台凭证已删除");
      emitCredentialChanged();
      await loadCredentials();
    } catch {
      message.error("删除失败");
    } finally {
      setLoading(false);
    }
  };

  const columns: ColumnsType<OpenPlatformCredential> = [
    {
      title: "名称",
      dataIndex: "displayName",
      render: (value?: string) => value || "-"
    },
    { title: "AppID", dataIndex: "appId" },
    { title: "API Key", dataIndex: "apiKeyMasked" },
    {
      title: "更新时间",
      dataIndex: "updatedAt",
      render: formatDateTime
    },
    {
      title: "操作",
      render: (_, credential) => (
        <Popconfirm
          title="删除凭证"
          description="确认删除这组开放平台凭证？"
          onConfirm={() => handleDelete(credential.id)}
        >
          <Button type="link" danger icon={<DeleteOutlined />}>
            删除
          </Button>
        </Popconfirm>
      )
    }
  ];

  return (
    <div className="page-stack">
      <div className="page-heading">
        <Typography.Title level={2}>用户信息</Typography.Title>
        <Typography.Text type="secondary">
          管理当前账号绑定的机器人接入凭证。
        </Typography.Text>
      </div>

      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setDrawerOpen(true)}>
            添加凭证
          </Button>
        </Space>

        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={credentials}
          pagination={false}
        />
      </Card>

      <Drawer
        title="添加开放平台凭证"
        open={drawerOpen}
        width={460}
        onClose={() => setDrawerOpen(false)}
        extra={
          <Button type="primary" loading={submitting} onClick={handleCreate}>
            保存
          </Button>
        }
      >
        <Form<SaveOpenPlatformCredentialRequest> form={form} layout="vertical" requiredMark={false}>
          <Form.Item label="名称" name="displayName">
            <Input placeholder="例如 华东园区开放平台" />
          </Form.Item>
          <Form.Item label="AppID" name="appId" rules={[{ required: true, message: "请输入 AppID" }]}>
            <Input placeholder="开放平台分配的 AppID" />
          </Form.Item>
          <Form.Item label="API Key" name="apiKey" rules={[{ required: true, message: "请输入 API Key" }]}>
            <Input placeholder="开放平台分配的 API Key" />
          </Form.Item>
          <Form.Item label="API Secret" name="apiSecret" rules={[{ required: true, message: "请输入 API Secret" }]}>
            <Input.Password placeholder="开放平台分配的 API Secret" />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  );
}
