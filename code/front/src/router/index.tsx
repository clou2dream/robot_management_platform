import { createBrowserRouter, Navigate } from "react-router-dom";
import { ProtectedRoute } from "./ProtectedRoute";
import { ConsoleLayout } from "../layouts/ConsoleLayout";
import { LoginPage } from "../pages/LoginPage";
import { WorkbenchPage } from "../pages/WorkbenchPage";
import { InspectionPage } from "../pages/InspectionPage";
import { RobotDetailPage } from "../pages/RobotDetailPage";
import { RobotListPage } from "../pages/RobotListPage";
import { TaskDispatchPage } from "../pages/TaskDispatchPage";
import { TaskOrdersPage } from "../pages/TaskOrdersPage";
import { QuickstartPage } from "../pages/QuickstartPage";
import { OperatorsPage } from "../pages/OperatorsPage";
import { AlertsPage } from "../pages/AlertsPage";
import { AccountProfilePage } from "../pages/AccountProfilePage";
import { NotFoundPage } from "../pages/NotFoundPage";

export const router = createBrowserRouter([
  {
    path: "/login",
    element: <LoginPage />
  },
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <ConsoleLayout />,
        children: [
          { index: true, element: <Navigate to="/workbench" replace /> },
          { path: "/workbench", element: <WorkbenchPage /> },
          { path: "/robots/inspection", element: <InspectionPage /> },
          { path: "/robots/:robotId", element: <RobotDetailPage /> },
          { path: "/robots/list", element: <RobotListPage /> },
          { path: "/tasks/dispatch", element: <TaskDispatchPage /> },
          { path: "/tasks/orders", element: <TaskOrdersPage /> },
          { path: "/docs/quickstart", element: <QuickstartPage /> },
          { path: "/account/profile", element: <AccountProfilePage /> },
          { path: "/alerts", element: <AlertsPage /> },
          {
            path: "/operators",
            element: <OperatorsPage />
          }
        ]
      }
    ]
  },
  { path: "*", element: <NotFoundPage /> }
]);
