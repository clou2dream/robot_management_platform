import { useEffect, useState } from "react";
import { Spin } from "antd";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuthStore } from "../stores/authStore";
import { refreshAccessToken } from "../api/http";

export function ProtectedRoute() {
  const location = useLocation();
  const accessToken = useAuthStore((state) => state.accessToken);
  const [checking, setChecking] = useState(!accessToken);
  const [refreshFailed, setRefreshFailed] = useState(false);

  useEffect(() => {
    if (accessToken) {
      setChecking(false);
      setRefreshFailed(false);
      return;
    }

    let active = true;
    setChecking(true);

    refreshAccessToken()
      .then(() => {
        if (active) {
          setRefreshFailed(false);
        }
      })
      .catch(() => {
        if (active) {
          setRefreshFailed(true);
        }
      })
      .finally(() => {
        if (active) {
          setChecking(false);
        }
      });

    return () => {
      active = false;
    };
  }, [accessToken]);

  if (checking) {
    return (
      <div className="screen-center">
        <Spin tip="正在恢复登录状态" />
      </div>
    );
  }

  if (!accessToken && refreshFailed) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <Outlet />;
}
