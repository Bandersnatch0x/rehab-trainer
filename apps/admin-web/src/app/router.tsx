import { createBrowserRouter, Navigate, Outlet } from "react-router";
import { AuthProvider } from "../auth/AuthProvider";
import AlertsPage from "../pages/AlertsPage";
import AuditLogPage from "../pages/AuditLogPage";
import FollowUpsPage from "../pages/FollowUpsPage";
import ForbiddenPage from "../pages/ForbiddenPage";
import LoginPage from "../pages/LoginPage";
import MediaAssetsPage from "../pages/MediaAssetsPage";
import NotFoundPage from "../pages/NotFoundPage";
import PatientDetailPage from "../pages/PatientDetailPage";
import PatientsPage from "../pages/PatientsPage";
import PermissionsPage from "../pages/PermissionsPage";
import PrescriptionEditPage from "../pages/PrescriptionEditPage";
import ResearchPage from "../pages/ResearchPage";
import StageConfirmationPage from "../pages/StageConfirmationPage";
import { AppLayout } from "./AppLayout";
import { RequireAuth, RequireRole } from "./guards";

/** AuthProvider 需要在 Router 上下文内，才能使用 useNavigate 处理 401 跳转。 */
function RootProviders() {
  return (
    <AuthProvider>
      <Outlet />
    </AuthProvider>
  );
}

export const router = createBrowserRouter([
  {
    element: <RootProviders />,
    children: [
      { path: "/login", element: <LoginPage /> },
      // 403 页不要求登录：账号有效但无管理端角色时正是要展示它。
      { path: "/forbidden", element: <ForbiddenPage /> },
      {
        element: (
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        ),
        children: [
          { index: true, element: <Navigate to="/patients" replace /> },
          { path: "patients", element: <PatientsPage /> },
          { path: "patients/:patientId", element: <PatientDetailPage /> },
          {
            path: "patients/:patientId/stage-confirmation",
            element: (
              <RequireRole roles={["rehab_therapist"]}>
                <StageConfirmationPage />
              </RequireRole>
            ),
          },
          {
            path: "patients/:patientId/prescription",
            element: (
              <RequireRole roles={["rehab_therapist"]}>
                <PrescriptionEditPage />
              </RequireRole>
            ),
          },
          { path: "alerts", element: <AlertsPage /> },
          { path: "follow-ups", element: <FollowUpsPage /> },
          { path: "audit-log", element: <AuditLogPage /> },
          { path: "research", element: <ResearchPage /> },
          { path: "media-assets", element: <MediaAssetsPage /> },
          {
            path: "admin/permissions",
            element: (
              <RequireRole roles={["care_admin", "rehab_therapist"]}>
                <PermissionsPage />
              </RequireRole>
            ),
          },
        ],
      },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
]);
