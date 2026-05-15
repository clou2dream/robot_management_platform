import React from "react";
import ReactDOM from "react-dom/client";
import { ConfigProvider, App as AntApp, theme } from "antd";
import zhCN from "antd/locale/zh_CN";
import App from "./App";
import "./styles/global.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: theme.defaultAlgorithm,
        token: {
          colorPrimary: "#1769ff",
          borderRadius: 6,
          colorBgLayout: "#f4f7fc",
          colorText: "#334155",
          colorTextHeading: "#0b1f3a",
          fontFamily:
            "-apple-system, BlinkMacSystemFont, \"Segoe UI\", \"PingFang SC\", \"Microsoft YaHei\", sans-serif"
        },
        components: {
          Layout: {
            headerBg: "#ffffff",
            siderBg: "#ffffff"
          },
          Table: {
            headerBg: "#f7faff"
          }
        }
      }}
    >
      <AntApp>
        <App />
      </AntApp>
    </ConfigProvider>
  </React.StrictMode>
);
