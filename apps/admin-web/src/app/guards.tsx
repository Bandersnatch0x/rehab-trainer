import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router";
import { useAuth } from "../auth/AuthProvider";
import type { AdminRole } from "../auth/roles";

interface GuardProps {
  children: ReactNode;
}

export function RequireAuth({ children }: GuardProps) {
  const { isAuthenticated } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    const from = `${location.pathname}${location.search}`;
    return <Navigate to="/login" replace state={{ from }} />;
  }
  return <>{children}</>;
}

interface RequireRoleProps extends GuardProps {
  role: AdminRole;
}

/**
 * 服务端仍是权限的最终裁决者（跨机构返回 403）；
 * 这里的守卫只负责不渲染越权入口。
 */
export function RequireRole({ role, children }: RequireRoleProps) {
  const { can } = useAuth();

  if (!can(role)) {
    return <Navigate to="/forbidden" replace />;
  }
  return <>{children}</>;
}
